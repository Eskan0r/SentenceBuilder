package com.team23.sentencebuilder.io;

import com.team23.sentencebuilder.logic.ImportException;
import com.team23.sentencebuilder.logic.TextFileReader;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Text file reader that reads in UTF-8 formatting (and attempts to read old latin formats
 * to the best of their ability if possible)
 *
 * @author Alen Jo
 */
public class Utf8TextFileReader implements TextFileReader {
    private static final char BOM = '\uFEFF';
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /**
     * Does the following procedure:
     * 1. Checks if the file path supplied is ready to read
     * 2. Deserializes into string from file with BOM included
     * 3. Strips out the BOM for final string version
     *
     * @param path - file location
     * @return - file contents as a string
     *
     * @throws ImportException - if any part of the above steps fail
     */
    @Override
    public String read(Path path) throws ImportException {
        Objects.requireNonNull(path, "file path is null!");

        verifyFile(path);
        String text = readText(path);

        return stripBom(text);
    }

    /**
     * Checks if the file is ready to be read
     *
     * @param path - the file's location
     * @throws ImportException - client friendly message letting user know that file failed some precondition
     * checks
     */
    private static void verifyFile(Path path) throws ImportException {
        if (Files.notExists(path)) {
            throw new ImportException("file not found: " + path);
        }

        if (Files.isDirectory(path)) {
            throw new ImportException("file is a directory: " + path);
        }

        if (!Files.isReadable(path)) {
            throw new ImportException("file is not readable: " + path);
        }
    }

    /**
     * Reads in the contents of a file as UTF-8
     *
     * @param path - file's location
     * @return - file contents as a string
     * @throws ImportException - client friendly message that lets users know file could not be read at this time
     */
    private static String readText(Path path) throws ImportException {
        String text;

        try {
            text = readUtf8OrLegacy(path); // try UTF-8 or the older Windows 1252 format if at all possible
        } catch (CharacterCodingException cce) {
            throw new ImportException(path.getFileName() + " could not be decoded as UTF-8 or "  + "Windows-1252. Please re-save the file as UTF-8 and try again.", cce);
        } catch (IOException ie) {
            throw new ImportException("Could not read " + path.getFileName() + ": " + ie.getMessage(), ie); // potential race conditions?
        }

        return text;
    }

    /**
     * Reads out the UTF-8 or legacy file encodings from a supplied file and normalizes it all
     * out into UTF-8 format. It can't catch everything but this should catch majority of it.
     *
     * @param path - file location
     * @return - text that is in UTF-8 format
     * @throws IOException - could not convert to UTF-8 safely perhaps due to a unique file format
     */
    private static String readUtf8OrLegacy(Path path) throws IOException {
        String text;

        try {
            text = Files.readString(path); // read as UTF-8 if possible
        } catch (CharacterCodingException notUtf8) {
            text = Files.readString(path, WINDOWS_1252); // try Windows_1252 format for older files
            if (text.indexOf('\u0000') >= 0) {
                throw notUtf8; // too many NULLs meaning this file garbled in deserialize
            }
        }

        return text;
    }

    /**
     * String without the byte order marking which can cause failure in downstream if failed
     *
     * @param text - file contents decoded from bytes with byte order mark
     * @return - file contents without the byte order mark
     */
    private static String stripBom(String text) {
        String removedBom = text;

        if (!text.isEmpty() && text.charAt(0) == BOM) {
            removedBom = text.substring(1);
        }

        return removedBom;
    }
}
