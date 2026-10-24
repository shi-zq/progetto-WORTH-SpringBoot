package my.worth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void register(String username, String password) {
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
        String passwordHash = passwordEncoder.encode(password);
        try {
            userRepository.save(username, passwordHash);
        } catch (DuplicateKeyException e) {
            // 并发注册可能同时通过前面的检查，数据库唯一约束是最后保障。
            throw new IllegalStateException("Username already exists", e);
        }
    }

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
        User user = result.get();
        // matches 用原始密码验证已存哈希；用户不存在和密码错误使用相同提示。
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return user;
    }

    public User getCurrentUser(long userId) {
        Optional<User> result = userRepository.findById(userId);
        if (result.isEmpty()) {
            throw new NoSuchElementException("User not found");
        }
        return result.get();
    }
}
