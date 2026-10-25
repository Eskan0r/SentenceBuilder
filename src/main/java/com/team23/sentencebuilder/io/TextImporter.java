package com.team23.sentencebuilder.io;

import com.team23.sentencebuilder.logic.CorpusCounts;
import com.team23.sentencebuilder.logic.RegexTokenizer;
import com.team23.sentencebuilder.logic.Tokenizer;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Reads a plain text file, turns it into sentences with a {@link Tokenizer},
 * and feeds those sentences into a {@link CorpusCounts} so the counts of the
 * whole session grow by whatever the file contained.
 * <p>
 * The file is streamed line by line rather than slurped whole, so a large
 * Gutenberg novel keeps a small memory footprint while still being able to
 * report progress and honor a cancellation in the middle of the read. Blank
 * lines (paragraph breaks) flush the paragraph that was accumulating, which
 * matches how the tokenizer already treats paragraphs.
 * <p>
 * Bytes are decoded as UTF-8 with replacement characters for anything
 * malformed, so odd or old encodings degrade instead of aborting the import.
 *
 * @author Ronak Chavva
 */
public class TextImporter {
    private static final double PROGRESS_STEP = 0.01;
    private static final char PARAGRAPH_SEPARATOR = '\n';

    private final Tokenizer tokenizer;

    /**
     * Creates an importer that tokenizes with the default tokenizer.
     */
    public TextImporter() {
        this(new RegexTokenizer());
    }

    /**
     * Creates an importer that tokenizes with a caller supplied tokenizer.
     *
     * @param tokenizer - splits raw text into sentences of normalized words
     */
    public TextImporter(Tokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
    }

    /**
     * Imports a text file into the given counts. Progress fractions are based
     * on bytes read compared to the size of the file on disk.
     *
     * @param file - the text file to read, which must exist and be readable
     * @param target - the session counts that receive every sentence of the file
     * @param progress - where to report how far along the import is; may be null
     * @param cancelled - polled often; when it returns true the import stops; may be null
     * @return - what the file contributed, so the caller can show it or store it
     * @throws IOException - if the file cannot be read
     * @throws CancellationException - if the cancelled check returned true partway through
     */
    public ImportResult importFile(Path file, CorpusCounts target,
                                   ProgressListener progress, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(file, "File cannot be null");
        Objects.requireNonNull(target, "Counts cannot be null");

        ProgressListener listener = progress == null ? (fraction, message) -> {} : progress;
        BooleanSupplier cancelCheck = cancelled == null ? () -> false : cancelled;

        long fileSize = Files.size(file);
        String fileName = file.getFileName().toString();
        int wordsBefore = target.getWordCount();
        int uniqueWordsBefore = target.getTotal().size();
        int sentenceCount = 0;
        double nextReport = 0.0;

        StringBuilder paragraph = new StringBuilder();

        try (InputStream raw = Files.newInputStream(file);
             CountingInputStream counted = new CountingInputStream(raw);
             Reader decoded = new InputStreamReader(counted, decoder());
             BufferedReader reader = new BufferedReader(decoded)) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (cancelCheck.getAsBoolean()) {
                    throw new CancellationException("Import of " + fileName + " was cancelled");
                }

                if (line.isBlank()) {
                    sentenceCount += countParagraph(paragraph.toString(), target);
                    paragraph.setLength(0);
                } else {
                    if (!paragraph.isEmpty()) {
                        paragraph.append(PARAGRAPH_SEPARATOR);
                    }
                    paragraph.append(line);
                }

                double fraction = fileSize == 0 ? 1.0 : Math.min(1.0, (double) counted.count() / fileSize);

                if (fraction >= nextReport) {
                    listener.onProgress(fraction,
                            String.format("Analyzing %s - %,d sentences so far", fileName, sentenceCount));
                    nextReport = fraction + PROGRESS_STEP;
                }
            }

            if (!paragraph.isEmpty()) {
                sentenceCount += countParagraph(paragraph.toString(), target);
            }
        }

        listener.onProgress(1.0,
                String.format("Analyzing %s - %,d sentences so far", fileName, sentenceCount));

        return new ImportResult(
                fileName,
                file.toAbsolutePath().toString(),
                target.getWordCount() - wordsBefore,
                sentenceCount,
                target.getTotal().size() - uniqueWordsBefore,
                Instant.now());
    }

    /**
     * Tokenizes one paragraph and counts each of its sentences. Blank
     * paragraphs count as nothing.
     *
     * @param paragraph - raw paragraph text containing no blank lines
     * @param target - the session counts that receive the sentences
     * @return - how many sentences were found in the paragraph
     */
    private int countParagraph(String paragraph, CorpusCounts target) {
        if (paragraph.isBlank()) {
            return 0;
        }

        int found = 0;

        for (List<String> sentence : tokenizer.tokenize(paragraph)) {
            target.addSentence(sentence);
            found++;
        }

        return found;
    }

    /**
     * Builds the UTF-8 decoder that swaps bad bytes for replacement
     * characters instead of failing the whole import.
     *
     * @return - a decoder that tolerates malformed input
     */
    private static CharsetDecoder decoder() {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    /**
     * Counts the bytes that actually left the file, which is what progress
     * reporting is measured against.
     */
    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int next = in.read();

            if (next != -1) {
                count++;
            }

            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = in.read(buffer, offset, length);

            if (read > 0) {
                count += read;
            }

            return read;
        }

        private long count() {
            return count;
        }
    }
}
