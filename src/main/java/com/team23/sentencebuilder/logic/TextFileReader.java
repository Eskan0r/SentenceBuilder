package com.team23.sentencebuilder.logic;

import java.nio.file.Path;

/**
 * Reads a text file into a string
 *
 * @author Alen Jo
 */
public interface TextFileReader {
    /**
     * Takes in a file and turns that into a string blob
     *
     * @param path - file location
     * @return - file's text
     * @throws ImportException - if file missing, directory unreachable, or
     * file is in an invalid encoding (such as it not being in UTF-8)
     */
    String read(Path path) throws ImportException;
}
