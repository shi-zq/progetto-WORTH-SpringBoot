package my.worth;

public enum Status {
    TODO, INPROGRESS, TOBEREVISED, DONE;

    // this 是当前状态，next 是目标状态；重复状态、null 和从 DONE 出发均不允许。
    public boolean canMoveTo(Status next) {
        if (next == null) return false;
        return switch (this) {
            case TODO -> next == INPROGRESS;
            case INPROGRESS -> next == TOBEREVISED || next == DONE;
            case TOBEREVISED -> next == INPROGRESS || next == DONE;
            case DONE -> false;
        };
    }
}
