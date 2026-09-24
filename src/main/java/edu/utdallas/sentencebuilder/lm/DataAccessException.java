package edu.utdallas.sentencebuilder.lm;

/** Unchecked wrapper so SQLExceptions do not leak into the generator interfaces. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
