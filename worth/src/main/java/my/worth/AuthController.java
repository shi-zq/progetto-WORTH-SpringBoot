package my.worth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

// Spring 将返回对象序列化为 JSON；类上的路径与方法上的路径拼成完整 URL。
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    // 构造方法注入：Spring 提供 AuthService，Controller 不直接操作数据库。
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 接收 JSON；不要记录此对象的日志，因为它含有原始密码。
    // record 自动提供构造方法和 username() / password() 等访问方法。
    public record AuthRequest(String username, String password) {}

    // 只返回公开字段，不把含密码哈希的 User 直接返回给客户端。
    public record UserResponse(long id, String username) {}

    // POST 提交注册数据、创建用户；@RequestBody 将 JSON 转换成 AuthRequest。
    // void 仅表示没有响应正文，成功时仍有 HTTP 201 响应；注册不会自动登录。
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody AuthRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        try {
            authService.register(request.username(), request.password());
        } catch (IllegalArgumentException e) {
            // 在服务器端将业务异常转换为 HTTP 异常，再由 Spring 生成响应。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    // 登录会创建会话，所以用 POST；查询当前用户才适合 GET。
    // 没有指定成功状态码时，正常返回对象得到 HTTP 200 和 JSON 正文。
    @PostMapping("/login")
    public UserResponse login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        User user;
        try {
            user = authService.authenticate(request.username(), request.password());
        } catch (IllegalArgumentException e) {
            // 在服务器端将业务异常转换为 HTTP 异常，再由 Spring 生成响应。
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password", e);
        }

        // 先验证密码，再替换旧会话，避免复用登录前的 Session ID。
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        // true：没有会话时创建一个；容器通常用 JSESSIONID Cookie 关联后续请求。
        HttpSession session = httpRequest.getSession(true);
        // userId 保存在服务器的 Session 中，客户端不应自行指定当前登录身份。
        session.setAttribute("userId", user.getId());
        session.setMaxInactiveInterval(1800); // 连续 30 分钟未访问后过期。
        return new UserResponse(user.getId(), user.getUsername());
    }

    // 此路径按操作命名，用 POST 执行退出；204 明确表示成功且没有响应正文。
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        // false 表示没有会话就返回 null；退出时不创建新会话。
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    // 读取当前用户，是 GET 操作，没有请求正文，也不接收客户端提供的 userId。
    @GetMapping("/me")
    public UserResponse me(HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        if (current == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        Object value = current.getAttribute("userId");

        // 同时排除属性不存在（null）和类型错误的情况。
        if (!(value instanceof Long)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        // 登录时保存的是 Long；检查类型后才能安全转换。
        long userId = (Long) value;

        try {
            // Service 已经拆开 Optional，这里直接接收 User。
            User user = authService.getCurrentUser(userId);
            return new UserResponse(user.getId(), user.getUsername());
        } catch (NoSuchElementException e) {
            // 会话还在，但对应的用户已经不存在。
            current.invalidate();
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not logged in", e);
        }
    }

    // 后续卡片 API 必须检查 Session 中的 userId，并确认它等于卡片的 ownerId。
    // 浏览器客户端接入前还需配置 CSRF 防护；当前尚未启用完整 Spring Security。
}
