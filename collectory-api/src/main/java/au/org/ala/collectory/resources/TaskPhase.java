package au.org.ala.collectory.resources;

public enum TaskPhase {
    NEW(false),
    IGNORED(true),
    QUEUED(false),
    METADATA(false),
    GENERATING(false),
    DOWNLOADING(false),
    PROCESSING(false),
    CONNECITNG(false),
    EMPTY(true),
    COMPLETED(true),
    ERROR(true),
    CANCELLED(true);

    private final boolean terminal;

    TaskPhase(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
