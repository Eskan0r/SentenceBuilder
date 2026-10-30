package com.team23.sentencebuilder.io;

/**
 * Helps strip out unnecessary text identifiers to feed into downstream
 * accessors that need consistent text across the board
 *
 * @author Alen Jo
 */
public interface BoilerplateStripper {
    String strip(String rawText);
}