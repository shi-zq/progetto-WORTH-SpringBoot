package my.worth;

import java.util.List;

public class Card {
    // Long 允许 null：新建且尚未保存的卡片还没有数据库 ID。
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

    // 从数据库恢复时沿用历史，不能再生成一条初始 TODO（这里是业务状态名）。
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
