package com.heartbridge.fs.exception;

/**
 * the application runtime exception, used to wrap the compile exception in application
 * @author GavinCook
 * @since  1.0.0
 */
public class ApplicationRunTimeException extends RuntimeException{

    public ApplicationRunTimeException() {
        super();
    }

    public ApplicationRunTimeException(String message) {
        super(message);
    }

    public ApplicationRunTimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApplicationRunTimeException(Throwable cause) {
        super(cause);
    }
}
