package com.team23.sentencebuilder.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;

/**
 * Sequential {@link StreamTextImporter}: reads the stream line by line on the calling thread,
 * gathers lines into blocks, and tokenizes and counts one block at a time into a single
 * {@link CorpusCounts}.
 * <p>
 * Blocks are cut at the first blank line after at least {@link #BLOCK_CHARS} characters have
 * accumulated, so sentences are tokenized independently per block. A sentence that spans a
 * blank line is therefore split in two, which can make the counts differ slightly from
 * {@link DefaultTextImporter} on the same text. For ordinary prose where paragraph breaks end
 * sentences, the results are equal. Line terminators are normalized to {@code \n}.
 * <p>
 * As always, progress advances once per block and is capped below 1.0 until the stream is fully
 * consumed. As the first block is tokenized before the first progress report, the progress
 * bar sits at 0 during that stage.
 *
 * @author Alen Jo
 */
public class StreamingTextImporter implements StreamTextImporter {
    static final int BLOCK_CHARS = 64 * 1024; // min chars accumulated before block cut at next blank line
    private final Tokenizer tokenizer;

    public StreamingTextImporter(Tokenizer tokenizer)
    {
        this.tokenizer = Objects.requireNonNull(tokenizer);
    }

    /**
     * Reads the stream block by block, counting each block's sentences as it goes.
     * <p>
     * Blocks the calling thread until the stream is exhausted. Progress values are reported
     * on the calling thread, are monotonically increasing, never exceed 0.99 before the
     * stream ends, and always end with exactly {@link TextImporter#PROGRESS_COMPLETE}. A
     * trailing partial block (shorter than {@link #BLOCK_CHARS}) is counted after the last
     * line is read. If reading fails, no partial counts are returned and completion is not
     * reported.
     *
     * @param reader - source of raw text, read to EOF and not closed by this method
     * @param expectedChars - rough total length in characters used to estimate progress, or {@link #UNKNOWN_LENGTH}
     * @param progress - progress values as import advances
     *
     * @return {@link CorpusCounts} object containing all counts of text read from the stream
     *
     * @throws IOException if reading from the reader fails
     * @throws NullPointerException if reader or progress is null
     */
    @Override
    public CorpusCounts importStream(Reader reader, long expectedChars, DoubleConsumer progress) throws IOException {
        Objects.requireNonNull(reader);
        Objects.requireNonNull(progress);

        CorpusCounts counts = new CorpusCounts();
        BufferedReader inStream = reader instanceof BufferedReader br ? br : new BufferedReader(reader);
        StringBuilder block = new StringBuilder();

        long charsRead = 0;
        double lastReported = 0.0;
        String line;

        while ((line = inStream.readLine()) != null)
        {
            block.append(line).append("\n");

            if (block.length() >= BLOCK_CHARS && line.isBlank())
            {
                charsRead += countBlock(block, counts);
                lastReported += report(progress, charsRead, expectedChars, lastReported);
            }
        }

        if (!block.isEmpty()) {
            countBlock(block, counts);
        }

        progress.accept(TextImporter.PROGRESS_COMPLETE);
        return counts;
    }

    /**
     * Tokenizes the block, adds every sentence to the counts, and empties the block.
     *
     * @param block - accumulated text to count; cleared by this method
     * @param counts - counts that receive the block's sentences
     *
     * @return number of characters in the block before it was cleared
     */
    private int countBlock(StringBuilder block, CorpusCounts counts) {
        int length = block.length();
        for (List<String> sentence : tokenizer.tokenize(block.toString())) {
            counts.addSentence(sentence);
        }
        block.setLength(0);
        return length;
    }

    /**
     * Reports an estimated progress value if it is an increase over the last one reported.
     *
     * @param progress - receiver of the progress value
     * @param read - characters consumed so far
     * @param expected - expected total characters, or non-positive if unknown
     * @param last - last value reported
     *
     * @return the last value reported after this call
     */
    private static double report(DoubleConsumer progress, long read, long expected, double last) {
        if (expected <= 0) return last;
        double value = Math.min((double) read / expected, 0.99);
        if (value > last) {
            progress.accept(value);
            return value;
        }
        return last;
    }
}