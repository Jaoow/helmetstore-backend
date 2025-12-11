package com.jaoow.helmetstore.exception;

/**
 * @deprecated This exception is deprecated as of version 2.0.3 and will be removed in future versions.
 * Reinvestment feature has been removed from the system.
 */
@Deprecated(since = "2.0.3", forRemoval = true)
public class InvalidReinvestmentException extends RuntimeException {

    public InvalidReinvestmentException(String message) {
        super(message);
    }

    public InvalidReinvestmentException(String message, Throwable cause) {
        super(message, cause);
    }
}

