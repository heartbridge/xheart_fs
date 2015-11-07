package com.github.heartbridge.fs.exception;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class NoMatchedMethodFoundException extends RuntimeException {
    public NoMatchedMethodFoundException() {
        super();
    }

    public NoMatchedMethodFoundException(String message) {
        super(message);
    }

    public NoMatchedMethodFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoMatchedMethodFoundException(Throwable cause) {
        super(cause);
    }
}
