package my.worth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 接收 JSON；不要记录此对象的日志，因为它含有原始密码。
    public record AuthRequest(String username, String password) {}

    // 只返回公开字段，不把含密码哈希的 User 直接返回给客户端。
    public record UserResponse(long id, String username) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody AuthRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        try {
            authService.register(request.username(), request.password());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        User user;
        try {
            user = authService.authenticate(request.username(), request.password());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password", e);
        }

        // 先验证密码，再替换旧会话，避免复用登录前的 Session ID。
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setMaxInactiveInterval(1800); // 30-minute idle timeout.
        return new UserResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/me")
    public UserResponse me(HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        if (current == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        Object value = current.getAttribute("userId");

        if (!(value instanceof Long)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        long userId = (Long) value;

        try {
            User user = authService.getCurrentUser(userId);
            return new UserResponse(user.getId(), user.getUsername());
        } catch (NoSuchElementException e) {
            current.invalidate();
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not logged in", e);
        }
    }

    // 浏览器客户端接入前还需配置 CSRF 防护；当前尚未启用完整 Spring Security。
}
