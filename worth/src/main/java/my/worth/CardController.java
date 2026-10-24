package my.worth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.List;

@RestController
@RequestMapping("/api/cards")
public class CardController {
    private final CardService cardService;
    private final AuthService authService;

    public CardController(CardService cardService, AuthService authService) {
        this.cardService = cardService;
        this.authService = authService;
    }

    // 请求没有 ownerId。创建时 name、description 必填（description 可以是空字符串）。
    public record CreateCardRequest(String name, String description) {}
    // PATCH：未提供或为 null 的字段保留原值；不允许两个字段都不提供。
    public record UpdateCardRequest(String name, String description) {}
    // JSON 示例：{"status":"INPROGRESS"}；未知枚举字符串由 Spring 返回 400。
    public record UpdateStatusRequest(Status status) {}
    public record CardResponse(long id, String name, String description, Status status) {}
    public record ErrorResponse(String message) {}

    private long requireUserId(HttpServletRequest request) {
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
            authService.getCurrentUser(userId);
            return userId;
        } catch (NoSuchElementException e) {
            current.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
    }

    private CardResponse toResponse(Card card) {
        return new CardResponse(card.getId(), card.getCardName(), card.getDescription(), card.getStatus());
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@RequestBody CreateCardRequest body, HttpServletRequest request) {
        long userId = requireUserId(request);
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return toResponse(cardService.createCard(userId, body.name(), body.description()));
    }
    @GetMapping("/get")
    public List<CardResponse> list(HttpServletRequest request) {
        long userId = requireUserId(request);
        List<CardResponse> result = new ArrayList<>();
        List<Card> tmp = cardService.listCards(userId);
        for (Card card : tmp) {
            result.add(toResponse(card));
        }
        return result;
    }
    @GetMapping("/{id}")
    public CardResponse detail(@PathVariable("id") long id, HttpServletRequest request) {
        long userId = requireUserId(request);
        Card tmp = cardService.getCard(userId, id);
        return toResponse(tmp);
    }
    @PatchMapping("/{id}")
    public CardResponse updateDetails(@PathVariable("id") long id, @RequestBody UpdateCardRequest body, HttpServletRequest request) {
        long userId = requireUserId(request);
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        Card tmp = cardService.updateDetails(userId, id, body.name(), body.description());
        return toResponse(tmp);

    }
    @PatchMapping("/{id}/status")
    public CardResponse updateStatus(@PathVariable("id") long id, @RequestBody UpdateStatusRequest body, HttpServletRequest request) {
        long userId = requireUserId(request);
        if (body == null || body.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        Card tmp = cardService.updateStatus(userId, id, body.status());
        return toResponse(tmp);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidArgument(IllegalArgumentException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NoSuchElementException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateName(DuplicateKeyException e) {
        return new ErrorResponse("Card name already exists");
    }
}
