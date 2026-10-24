package my.worth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class History {
    private final List<HistoryEntry> history;

    public History() {
        history = new ArrayList<>();
        history.add(new HistoryEntry(LocalDateTime.now(), Status.TODO));
    }

    // 恢复已保存的历史；复制列表，避免外部修改传入列表影响当前对象。
    History(List<HistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("History must contain an initial entry");
        }
        history = new ArrayList<>(List.copyOf(entries));
    }

    public Status getStatus() {
        return history.get(history.size() - 1).status();
    }

    // 这里只修改内存对象；持久化由 CardRepository.updateStatus 在事务中完成。
    public void updateStatus(Status state) {
        if (!getStatus().canMoveTo(state)) {
            throw new IllegalArgumentException("Invalid status transition: " + getStatus() + " -> " + state);
        }
        history.add(new HistoryEntry(LocalDateTime.now(), state));
    }

    // 返回不可修改的副本，防止调用方绕过状态检查直接添加或删除历史。
    public List<HistoryEntry> getHistory() { return List.copyOf(history); }
}
