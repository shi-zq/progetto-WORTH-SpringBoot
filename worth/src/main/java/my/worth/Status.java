package my.worth;

public enum Status {
    TODO, INPROGRESS, TOBEREVISED, DONE;

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
