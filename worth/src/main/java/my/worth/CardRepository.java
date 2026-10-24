package my.worth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Repository
public class CardRepository {
    // 本类不判断当前登录用户；调用它的 Service 需要先检查访问权限。
    private final JdbcTemplate jdbcTemplate;

    public CardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 新建卡片：卡片和初始历史必须一起保存，失败时一起回滚。
    @Transactional
    public Card save(long ownerId, Card card) {
        if (card == null) {
            throw new IllegalArgumentException("Card cannot be null");
        }
        if (card.getId() != null) {
            throw new IllegalArgumentException("Card is already saved");
        }
        List<HistoryEntry> history = card.getHistory();
        if (history.size() != 1 || history.get(0).status() != Status.TODO) {
            throw new IllegalArgumentException("Save the new TODO card before moving it");
        }
        validateDetails(card.getCardName(), card.getDescription());

        String sql = "INSERT INTO cards (owner_id, name, description, status) VALUES (?, ?, ?, ?)";
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statementCreator = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
                statement.setLong(1, ownerId);
                statement.setString(2, card.getCardName());
                statement.setString(3, card.getDescription());
                statement.setString(4, Status.TODO.name());
                return statement;
            }
        };
        jdbcTemplate.update(statementCreator, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Database did not return a card ID");
        }
        long cardId = generatedId.longValue();
        insertHistory(cardId, history.get(0));
        return findById(cardId).orElseThrow();
    }

    // 卡片与历史分两次查询，保持同一次读取中的数据一致。
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Card> findById(long cardId) {
        String sql = "SELECT * FROM cards WHERE id = ?";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, cardId);
        if (rows.next()) {
            Card card = readCard(rows);
            return Optional.of(card);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Card> findByOwnerId(long ownerId) {
        String sql = "SELECT * FROM cards WHERE owner_id = ? ORDER BY id";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, ownerId);
        List<Card> cards = new ArrayList<>();
        while (rows.next()) {
            Card card = readCard(rows);
            cards.add(card);
        }
        return cards;
    }

    public void updateDetails(long cardId, String name, String description) {
        validateDetails(name, description);
        String sql = "UPDATE cards SET name = ?, description = ? WHERE id = ?";
        int changedRows = jdbcTemplate.update(sql, name, description, cardId);
        checkCardExists(changedRows, cardId);
    }

    public void updateDescription(long cardId, String description) {
        validateDescription(description);
        String sql = "UPDATE cards SET description = ? WHERE id = ?";
        int changedRows = jdbcTemplate.update(sql, description, cardId);
        checkCardExists(changedRows, cardId);
    }

    @Transactional
    public Card updateStatus(long cardId, Status next) {
        Card card = lockCard(cardId);
        card.updateStatus(next);
        String sql = "UPDATE cards SET status = ? WHERE id = ?";
        jdbcTemplate.update(sql, next.name(), cardId);
        List<HistoryEntry> history = card.getHistory();
        HistoryEntry newEntry = history.get(history.size() - 1);
        insertHistory(cardId, newEntry);
        return findById(cardId).orElseThrow();
    }

    @Transactional
    public void deleteById(long cardId) {
        lockCard(cardId);
        // 历史引用卡片，所以必须先删除历史，再删除卡片。
        jdbcTemplate.update("DELETE FROM card_history WHERE card_id = ?", cardId);
        jdbcTemplate.update("DELETE FROM cards WHERE id = ?", cardId);
    }

    private Card lockCard(long cardId) {
        // 锁住这一行直到事务结束，避免两次状态更新同时修改同一张卡。
        String sql = "SELECT * FROM cards WHERE id = ? FOR UPDATE";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, cardId);
        if (!rows.next()) {
            throw new NoSuchElementException("Card not found: " + cardId);
        }
        return readCard(rows);
    }

    private Card readCard(SqlRowSet row) {
        long cardId = row.getLong("id");
        long ownerId = row.getLong("owner_id");
        String name = row.getString("name");
        String description = row.getString("description");
        Status status = Status.valueOf(row.getString("status"));

        List<HistoryEntry> history = findHistory(cardId);
        if (history.isEmpty()) {
            throw new DataIntegrityViolationException("Card has no history: " + cardId);
        }
        HistoryEntry lastEntry = history.get(history.size() - 1);
        if (lastEntry.status() != status) {
            throw new DataIntegrityViolationException("Card status and history disagree: " + cardId);
        }
        return Card.restore(cardId, ownerId, name, description, history);
    }

    private List<HistoryEntry> findHistory(long cardId) {
        String sql = "SELECT occurred_at, status FROM card_history WHERE card_id = ? ORDER BY id";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, cardId);
        List<HistoryEntry> history = new ArrayList<>();
        while (rows.next()) {
            Timestamp time = rows.getTimestamp("occurred_at");
            String statusName = rows.getString("status");
            Status status = Status.valueOf(statusName);
            HistoryEntry entry = new HistoryEntry(time.toLocalDateTime(), status);
            history.add(entry);
        }
        return history;
    }

    private void insertHistory(long cardId, HistoryEntry entry) {
        String sql = "INSERT INTO card_history (card_id, occurred_at, status) VALUES (?, ?, ?)";
        Timestamp time = Timestamp.valueOf(entry.occurredAt());
        jdbcTemplate.update(sql, cardId, time, entry.status().name());
    }

    private void validateDetails(String name, String description) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("Card name must contain 1 to 200 characters");
        }
        validateDescription(description);
    }

    private void validateDescription(String description) {
        if (description == null || description.length() > 10000) {
            throw new IllegalArgumentException("Description must be non-null and at most 10000 characters");
        }
    }

    private void checkCardExists(int count, long cardId) {
        if (count == 0) {
            throw new NoSuchElementException("Card not found: " + cardId);
        }
    }
}


