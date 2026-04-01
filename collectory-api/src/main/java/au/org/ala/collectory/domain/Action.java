package au.org.ala.collectory.domain;

public enum Action {
    LOGIN("logged in"),
    LOGOUT("logged out"),
    VIEW("viewed"),
    EDIT_CANCEL("edited but cancelled"),
    EDIT_SAVE("edited and saved"),
    PREVIEW("previewed"),
    DELETE("deleted"),
    SEARCH("searched for "),
    LIST("listed collections"),
    MYLIST("listed own collections"),
    DATA_LOAD("loaded data"),
    CREATE("created a collection"),
    CREATE_CANCEL("cancelled creation of collection"),
    CREATE_INSTITUTION("created an institution"),
    CREATE_CONTACT("created a contact"),
    UPLOAD_IMAGE("uploaded file"),
    REPORT("viewed reports"),
    NOTIFY("notifiable event"),
    SCAN("scanned for updates");

    private final String display;

    Action(String display) {
        this.display = display;
    }

    @Override
    public String toString() {
        return display;
    }
}
