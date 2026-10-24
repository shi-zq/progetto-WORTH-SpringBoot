package my.worth;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

// Service 检查卡片归属，并调用 Repository。
// ownerId 由 Controller 从已验证的 Session 获取，不能来自请求正文。
@Service
public class CardService {
    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public Card getCard(long ownerId, long cardId) {
        Optional<Card> result = cardRepository.findById(cardId);
        if (result.isEmpty()) {
            throw new NoSuchElementException("Card not found");
        }
        else {
            if (result.get().getOwnerId().equals(ownerId)) {
                return result.get();
            }
            else {
                throw new NoSuchElementException("Card not found");
            }
        }
    }

    public Card createCard(long ownerId, String name, String description) {
        Card tmp = new Card(name, description);
        return cardRepository.save(ownerId, tmp);
    }

    public List<Card> listCards(long ownerId) {
        return cardRepository.findByOwnerId(ownerId);
    }

    public Card updateDetails(long ownerId, long cardId, String name, String description) {
        Card tmp = getCard(ownerId, cardId);

        // PATCH 中 null 表示保留原值。
        if (name == null && description == null) {
            throw new IllegalArgumentException("Provide a name or description to update");
        }

        if (name == null) {
            name = tmp.getCardName();
        }
        if (description == null) {
            description = tmp.getDescription();
        }

        cardRepository.updateDetails(cardId, name, description);
        return getCard(ownerId, cardId);
    }

    public Card updateStatus(long ownerId, long cardId, Status status) {
        getCard(ownerId, cardId);
        return cardRepository.updateStatus(cardId, status);
    }
}
