package my.worth;

import java.time.LocalDateTime;

public record HistoryEntry(LocalDateTime occurredAt, Status status) {}
