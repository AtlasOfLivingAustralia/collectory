package au.org.ala.collectory.exception;

import lombok.Getter;

@Getter
public class ExternalResourceException extends RuntimeException {
    private final String code;
    private final Object[] args;

    public ExternalResourceException(String message) {
        super(message);
        this.code = null;
        this.args = null;
    }

    public ExternalResourceException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
        this.args = null;
    }

    public ExternalResourceException(String message, String code, Object... args) {
        super(message);
        this.code = code;
        this.args = args;
    }

    public ExternalResourceException(String message, Throwable cause, String code, Object... args) {
        super(message, cause);
        this.code = code;
        this.args = args;
    }
}
