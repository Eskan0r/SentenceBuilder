package com.team23.sentencebuilder.logic;

/**
 * Throws for when a file cannot be imported or satisfies such import. Message
 * is written for end user and can display in UI as such.
 *
 * @author Alen Jo
 */
public class ImportException extends Exception {
    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
