package com.team23.sentencebuilder.io;

import com.team23.sentencebuilder.logic.ImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Utf8TextFileReaderTest {
    private final Utf8TextFileReader reader = new Utf8TextFileReader();

    @TempDir
    Path dir;

    @Test
    void readsNormalUtf8File() throws Exception {
        Path file = dir.resolve("normal.txt");
        Files.writeString(file, "Caf\u00e9 au lait.\nSecond line.", StandardCharsets.UTF_8);

        assertEquals("Caf\u00e9 au lait.\nSecond line.", reader.read(file));
    }

    @Test
    void stripsLeadingBom() throws Exception {
        Path file = dir.resolve("bom.txt");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "Hello.".getBytes(StandardCharsets.UTF_8);
        Files.write(file, concat(bom, body));

        String text = reader.read(file);

        assertEquals("Hello.", text);
        assertFalse(text.startsWith("\uFEFF"));
    }

    @Test
    void leavesLineEndingsAlone() throws Exception {
        Path file = dir.resolve("crlf.txt");
        Files.writeString(file, "a\r\nb\rc\n", StandardCharsets.UTF_8);

        assertEquals("a\r\nb\rc\n", reader.read(file));
    }

    @Test
    void emptyFileGivesEmptyString() throws Exception {
        Path file = dir.resolve("empty.txt");
        Files.write(file, new byte[0]);

        assertEquals("", reader.read(file));
    }

    @Test
    void missingPathThrowsReadableException() {
        Path missing = dir.resolve("nope.txt");

        ImportException e = assertThrows(ImportException.class, () -> reader.read(missing));

        assertTrue(e.getMessage().contains("file not found"));
    }

    @Test
    void directoryThrowsReadableException() {
        ImportException e = assertThrows(ImportException.class, () -> reader.read(dir));

        assertTrue(e.getMessage().contains("directory"));
    }

    @Test
    void eachFailureHasADistinctMessage() throws Exception {
        Path missing = dir.resolve("nope.txt");
        Path bad = dir.resolve("bad.txt");
        Files.write(bad, new byte[]{'h', (byte) 0x81});

        Set<String> messages = new HashSet<>();
        messages.add(assertThrows(ImportException.class, () -> reader.read(missing)).getMessage());
        messages.add(assertThrows(ImportException.class, () -> reader.read(dir)).getMessage());
        messages.add(assertThrows(ImportException.class, () -> reader.read(bad)).getMessage());

        assertEquals(3, messages.size());
    }

    @Test
    void leavesNoOpenHandleBehind() throws Exception {
        Path file = dir.resolve("handle.txt");
        Files.writeString(file, "text");

        reader.read(file);

        assertDoesNotThrow(() -> Files.delete(file));
        assertFalse(Files.exists(file));
    }

    @Test
    void legacyWindowsFileIsConverted() throws Exception {
        Path file = dir.resolve("latin1.txt");
        Files.write(file, new byte[]{'h', 'i', (byte) 0xE9, ' ', (byte) 0x93, 'x', (byte) 0x94, '\n'});

        assertEquals("hi\u00e9 \u201cx\u201d\n", reader.read(file)); // accented characters and curly quotes
    }

    @Test
    void undecodableFileThrowsAndAsksForUtf8() throws Exception {
        Path file = dir.resolve("bad.txt");
        Files.write(file, new byte[]{'h', 'i', (byte) 0x81}); // invalid UTF-8

        ImportException e = assertThrows(ImportException.class, () -> reader.read(file));

        assertTrue(e.getMessage().contains("UTF-8"));
        assertNotNull(e.getCause());
    }

    @Test
    void utf16FileIsRejectedNotGarbled() throws Exception {
        Path file = dir.resolve("utf16.txt");
        Files.write(file, "Hello.".getBytes(StandardCharsets.UTF_16));

        assertThrows(ImportException.class, () -> reader.read(file));
    }

    @Test
    void nullPathIsRejected() {
        assertThrows(NullPointerException.class, () -> reader.read(null));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }
}