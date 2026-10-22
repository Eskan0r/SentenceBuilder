package com.team23.sentencebuilder.io;

import com.team23.sentencebuilder.logic.CorpusCounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

public class TextImporterTest {
    @TempDir
    Path tempDir;

    @Test
    public void importsFileAndCountsItsWords() throws IOException {
        Path file = writeSample("sample.txt", "The cat sat.\n\nThe dog ran!\n");
        CorpusCounts counts = new CorpusCounts();

        ImportResult result = new TextImporter().importFile(file, counts, (fraction, message) -> {}, () -> false);

        assertEquals("sample.txt", result.fileName());
        assertEquals(6, result.wordCount());
        assertEquals(2, result.sentenceCount());
        assertEquals(5, result.newWords());
        assertEquals(2, counts.getStarts().get("the"));
        assertEquals(1, counts.getEnds().get("sat"));
        assertEquals(1, counts.getEnds().get("ran"));
        assertEquals(1, counts.getFollows().get("the").get("cat"));
        assertEquals(1, counts.getFollows().get("the").get("dog"));
    }

    @Test
    public void secondImportOnlyReportsWordsThatAreGenuinelyNew() throws IOException {
        Path first = writeSample("first.txt", "The cat sat.\n");
        Path second = writeSample("second.txt", "The cat ran.\n");
        CorpusCounts counts = new CorpusCounts();
        TextImporter importer = new TextImporter();

        importer.importFile(first, counts, null, null);
        ImportResult result = importer.importFile(second, counts, null, null);

        assertEquals(1, result.newWords());
        assertEquals(4, counts.getTotal().size());
    }

    @Test
    public void reportsProgressThatReachesTheEnd() throws IOException {
        Path file = writeSample("progress.txt", "One two three.\n\nFour five six.\n");
        double[] lastFraction = {0.0};

        new TextImporter().importFile(file, new CorpusCounts(),
                (fraction, message) -> lastFraction[0] = fraction, () -> false);

        assertEquals(1.0, lastFraction[0]);
    }

    @Test
    public void cancellationStopsTheImport() throws IOException {
        Path file = writeSample("cancelled.txt", "One two three.\n\nFour five six.\n");

        assertThrows(CancellationException.class, () ->
                new TextImporter().importFile(file, new CorpusCounts(), null, () -> true));
    }

    @Test
    public void malformedBytesAreReplacedInsteadOfFailing() throws IOException {
        Path file = tempDir.resolve("latin.txt");
        Files.write(file, new byte[]{'h', 'i', (byte) 0xE9, '\n'});
        CorpusCounts counts = new CorpusCounts();

        ImportResult result = new TextImporter().importFile(file, counts, null, null);

        assertEquals(1, result.sentenceCount());
        assertEquals(1, result.wordCount());
        assertTrue(counts.getTotal().containsKey("hi"));
    }

    /**
     * Writes a small text file into the temporary directory.
     *
     * @param name - file name within the temporary directory
     * @param content - raw text of the file
     * @return - the path of the written file
     */
    private Path writeSample(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
