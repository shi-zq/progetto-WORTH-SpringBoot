package my.worth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository 只负责 users 表的读写。
 * AuthService 校验输入和密码，AuthController 处理 HTTP 和 Session。
 */
@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String username, String passwordHash) {
        String sql =
                "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        jdbcTemplate.update(sql, username, passwordHash);
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, username);
        if (rows.next()) {
            User user = readUser(rows);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public Optional<User> findById(long userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, userId);
        if (rows.next()) {
            User user = readUser(rows);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    private User readUser(SqlRowSet row) {
        long userId = row.getLong("id");
        String username = row.getString("username");
        String passwordHash = row.getString("password_hash");
        return new User(userId, username, passwordHash);
    }
}
