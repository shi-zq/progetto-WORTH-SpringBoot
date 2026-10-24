package my.worth;

import java.util.List;

public class Card {
    private Long id;
    private Long ownerId;
    private String cardName;
    private String description;
    private History history;

    public Card(String cardName, String description) {
        this.cardName = cardName;
        this.description = description;
        this.history = new History();
    }

    static Card restore(long id, long ownerId, String name, String description,
                        List<HistoryEntry> entries) {
        return new Card(id, ownerId, name, description, new History(entries));
    }

    private Card(long id, long ownerId, String name, String description, History history) {
        this.id = id;
        this.ownerId = ownerId;
        this.cardName = name;
        this.description = description;
        this.history = history;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public Status getStatus() { return history.getStatus(); }
    public void updateStatus(Status state) { history.updateStatus(state); }
    public String getCardName() { return cardName; }
    public String getDescription() { return description; }
    public List<HistoryEntry> getHistory() { return history.getHistory(); }
}
