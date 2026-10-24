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

    // Spring 将配置好数据源的 JdbcTemplate 传进来，不需要自己创建数据库连接。
    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // passwordHash 是 Service 算好的哈希；id 由数据库自动生成。
    public void save(String username, String passwordHash) {
        String sql =
                "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        // 参数按顺序填入两个 ?，不要把用户输入拼接进 SQL。
        // update 用于 INSERT / UPDATE / DELETE；数据库唯一约束会拒绝重复用户名。
        jdbcTemplate.update(sql, username, passwordHash);
    }

    // Optional 表示可能查到一个用户，也可能没有；调用方必须处理不存在的情况。
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, username);
        // 游标开始时在第一行之前；next() 移到下一行，存在该行才返回 true。
        if (rows.next()) {
            User user = readUser(rows);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    // 按 ID 查询用户，复用 readUser 将数据库行转换成对象。
    public Optional<User> findById(long userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        SqlRowSet rows = jdbcTemplate.queryForRowSet(sql, userId);
        // 游标开始时在第一行之前；next() 移到下一行，存在该行才返回 true。
        if (rows.next()) {
            User user = readUser(rows);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    // 把当前数据库行转换成 Java 对象；列名对应 schema.sql 中的字段。
    private User readUser(SqlRowSet row) {
        long userId = row.getLong("id");
        String username = row.getString("username");
        String passwordHash = row.getString("password_hash");
        return new User(userId, username, passwordHash);
    }
}
