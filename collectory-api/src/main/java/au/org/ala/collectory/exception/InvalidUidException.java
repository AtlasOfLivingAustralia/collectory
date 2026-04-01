package au.org.ala.collectory.exception;

public class InvalidUidException extends RuntimeException {

    public InvalidUidException(String uid) {
        super("Invalid UID: " + uid);
    }
}
