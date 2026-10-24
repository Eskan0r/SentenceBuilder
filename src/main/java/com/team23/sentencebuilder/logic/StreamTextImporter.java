package com.team23.sentencebuilder.logic;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;

/**
 * Helps turn a stream of raw text into {@link CorpusCounts} without having to hold whole
 * text in memory.
 * <p>
 * This is another flavor to {@link TextImporter} for input that reads incrementally, which
 * say we have a large text file that arrives through an IPC (inter-process communication)
 * channel rather than all one contiguous block. As always, {@link DoubleConsumer} is there
 * so that UI is able to render progress over time for streaming implementation.
 * <p>
 * Likewise, progress is ALWAYS monotonically increasing, between 0.0 to 1.0, and last value
 * is {@link TextImporter#PROGRESS_COMPLETE}.
 * <p>
 * Because the total length of a stream is often unknown or only approximate, intermediate
 * progress is an estimate. If the length is {@link #UNKNOWN_LENGTH}, only the final 1.0 is
 * reported. An empty stream is valid, yielding empty counts and reporting 1.0.
 *
 * @author Alen Jo
 */
public interface StreamTextImporter {
    long UNKNOWN_LENGTH = -1;

    /**
     * Reads the stream to its end, counts its sentences, and reports progress as it goes.
     * <p>
     *
     * @param reader - source of raw text, read to EOF and not closed by this method
     * @param expectedChars - rough total length in characters used to estimate progress, or {@link #UNKNOWN_LENGTH}
     * @param progress - progress values as import advances
     *
     * @return {@link CorpusCounts} object containing all counts of text read from the stream
     *
     * @throws IOException if reading from the reader fails; no partial counts are returned
     * @throws NullPointerException if reader or progress is null
     */
    CorpusCounts importStream(Reader reader, long expectedChars, DoubleConsumer progress) throws IOException;

    /**
     * Imports a stream of unknown length without reporting progress.
     *
     * @param reader - source of raw text, read to EOF and not closed by this method
     *
     * @return {@link CorpusCounts} object containing all counts of text read from the stream
     *
     * @throws IOException if reading from the reader fails
     * @throws NullPointerException if reader is null
     */
    default CorpusCounts importStream(Reader reader) throws IOException {
        return importStream(reader, UNKNOWN_LENGTH, v -> {});
    }

    /**
     * Opens the file as UTF-8, imports it, and closes it again, using the file's size in bytes
     * as the progress hint.
     * <p>
     * Since bytes are not characters (multi-byte characters, and {@code \r\n} line endings
     * being read as one character), progress may run slightly low before jumping to 1.0 at
     * the end. Unlike {@link #importStream(Reader, long, DoubleConsumer)}, this method opens
     * the reader itself and therefore closes it automatically.
     *
     * @param path - file to import
     * @param progress - progress values as import advances
     *
     * @return {@link CorpusCounts} object containing all counts of text in the file
     *
     * @throws IOException if the file cannot be opened or read
     * @throws NullPointerException if path or progress is null
     */
    default CorpusCounts importFile(Path path, DoubleConsumer progress) throws IOException
    {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return importStream(reader, Files.size(path), progress);
        }
    }
}
