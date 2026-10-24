package my.worth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;

// Service 负责业务规则，只抛 Java 异常；HTTP 状态码由 Controller 决定。
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 注册顺序：校验输入 → 查询重名 → 哈希密码 → 保存。
    public void register(String username, String password) {
        // || 从左到右短路判断，null 时不会再调用字符串方法；isBlank 包含纯空格。
        if (username == null || username.isBlank() || username.length() > 100) {
            throw new IllegalArgumentException("Invalid username");
        }
        // BCrypt 上限为 72 个 UTF-8 字节；不要 trim 密码，空格也是密码的一部分。
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Invalid password");
        }
        Optional<User> result = userRepository.findByUsername(username);
        if (result.isPresent()) {
            throw new IllegalStateException("Username already exists");
        }
        // BCrypt 自动加盐；同一密码的哈希可不同，因此登录不能重新 encode 后比较字符串。
        String passwordHash = passwordEncoder.encode(password);
        try {
            userRepository.save(username, passwordHash);
        } catch (DuplicateKeyException e) {
            // 并发注册可能同时通过前面的检查，数据库唯一约束是最后保障。
            throw new IllegalStateException("Username already exists", e);
        }
    }

    // 这里只验证凭据；Controller 负责创建 Session，且不得直接返回含密码哈希的 User。
    public User authenticate(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 100) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        if (password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        Optional<User> result = userRepository.findByUsername(username);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        // 前面已排除 Optional.empty()，现在才能安全取出 User。
        User user = result.get();
        // matches 用原始密码验证已存哈希；用户不存在和密码错误使用相同提示。
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return user;
    }

    // 确认 Session 对应的用户仍存在，不重新验证密码。
    // Service 接收 ID，不接收 HttpSession；HTTP 和会话处理留在 Controller。
    public User getCurrentUser(long userId) {
        Optional<User> result = userRepository.findById(userId);
        if (result.isEmpty()) {
            throw new NoSuchElementException("User not found");
        }
        return result.get();
    }
}
