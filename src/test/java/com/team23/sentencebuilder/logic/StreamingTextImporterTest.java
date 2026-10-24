package com.team23.sentencebuilder.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

import static org.junit.jupiter.api.Assertions.*;

class StreamingTextImporterTest {
    private static final int BLOCK = StreamingTextImporter.BLOCK_CHARS;

    private static class SplittingTokenizer implements Tokenizer {
        @Override
        public List<List<String>> tokenize(String text) {
            List<List<String>> sentences = new ArrayList<>();
            for (String chunk : text.split("\\.")) {
                List<String> words = tokenizeSentence(chunk);
                if (!words.isEmpty()) {
                    sentences.add(words);
                }
            }
            return sentences;
        }

        @Override
        public String normalize(String text) {
            return "";
        }

        public List<String> tokenizeSentence(String sentence) {
            List<String> words = new ArrayList<>();
            for (String word : sentence.trim().split("\\s+")) {
                if (!word.isEmpty()) {
                    words.add(word.toLowerCase(Locale.ROOT));
                }
            }
            return words;
        }
    }

    private static class CountingTokenizer extends SplittingTokenizer {
        final List<Integer> blockLengths = Collections.synchronizedList(new ArrayList<>());
        final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());

        @Override
        public List<List<String>> tokenize(String text) {
            blockLengths.add(text.length());
            threads.add(Thread.currentThread());
            return super.tokenize(text);
        }
    }

    private static class ProgressRecorder implements DoubleConsumer {
        final List<Double> values = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();

        @Override
        public void accept(double value) {
            values.add(value);
            threads.add(Thread.currentThread());
        }
    }

    private static class TrackingReader extends StringReader {
        boolean closed;

        TrackingReader(String text) {
            super(text);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }

    private static Reader failingAfter() {
        return new Reader() {
            private final StringReader delegate = new StringReader("one two. three.\n");

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                int read = delegate.read(buffer, offset, length);
                if (read == -1) {
                    throw new IOException("boom");
                }
                return read;
            }

            @Override
            public void close() {}
        };
    }

    /**
     * Paragraphs of 5 sentences, each paragraph on one line ending in ". " and followed by a
     * blank line. Every sentence ends in a period, so cutting at blank lines never splits one.
     * Roughly 115 chars per paragraph.
     */
    private static String makeCorpus(int paragraphs, long seed) {
        Random random = new Random(seed);
        StringBuilder text = new StringBuilder();
        for (int p = 0; p < paragraphs; p++) {
            for (int s = 0; s < 5; s++) {
                int words = 1 + random.nextInt(10);
                for (int w = 0; w < words; w++) {
                    if (w > 0) {
                        text.append(' ');
                    }
                    text.append('w').append(random.nextInt(40));
                }
                text.append(". ");
            }
            text.append("\n\n");
        }
        return text.toString();
    }

    private static String bigLine() {
        StringBuilder line = new StringBuilder();
        while (line.length() < BLOCK) {
            line.append("word. ");
        }
        return line.toString();
    }

    private static CorpusCounts sequential(String text) {
        return new DefaultTextImporter(new SplittingTokenizer()).importText(text);
    }

    private static CorpusCounts streamed(String text) throws IOException {
        return new StreamingTextImporter(new SplittingTokenizer()).importStream(new StringReader(text));
    }

    private static void assertSameCounts(CorpusCounts expected, CorpusCounts actual) {
        assertEquals(expected.getWordCount(), actual.getWordCount(), "wordCount");
        assertEquals(expected.getTotal(), actual.getTotal(), "totals");
        assertEquals(expected.getStarts(), actual.getStarts(), "starts");
        assertEquals(expected.getEnds(), actual.getEnds(), "ends");
        assertEquals(expected.getFollows(), actual.getFollows(), "follows");
    }

    private static void assertValidProgress(List<Double> values) {
        assertFalse(values.isEmpty(), "progress must report at least the final 1.0");
        assertEquals(TextImporter.PROGRESS_COMPLETE, values.getLast());

        double previous = 0.0;
        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            assertTrue(value > 0.0 && value <= 1.0, "out of range: " + value);
            assertTrue(value >= previous, "progress went backwards: " + previous + " -> " + value);
            if (i < values.size() - 1) {
                assertTrue(value < TextImporter.PROGRESS_COMPLETE, "1.0 must only be reported last");
            }
            previous = value;
        }
    }

    // argument checks

    @Test
    void constructorRejectsNullTokenizer() {
        assertThrows(NullPointerException.class, () -> new StreamingTextImporter(null));
    }

    @Test
    void importStreamRejectsNullArguments() {
        StreamingTextImporter importer = new StreamingTextImporter(new SplittingTokenizer());
        assertThrows(NullPointerException.class,
                () -> importer.importStream(null, StreamTextImporter.UNKNOWN_LENGTH, v -> {}));
        assertThrows(NullPointerException.class,
                () -> importer.importStream(new StringReader("a."), StreamTextImporter.UNKNOWN_LENGTH, null));
        assertThrows(NullPointerException.class, () -> importer.importStream(null));
    }

    // empty and tiny input

    @Test
    void emptyStreamYieldsEmptyCountsAndReportsOnlyComplete() throws IOException {
        ProgressRecorder progress = new ProgressRecorder();

        CorpusCounts counts = new StreamingTextImporter(new SplittingTokenizer())
                .importStream(new StringReader(""), 0, progress);

        assertEquals(0, counts.getWordCount());
        assertTrue(counts.getTotal().isEmpty());
        assertEquals(List.of(TextImporter.PROGRESS_COMPLETE), progress.values);
    }

    @Test
    void whitespaceAndPunctuationOnlyYieldEmptyCounts() throws IOException {
        ProgressRecorder progress = new ProgressRecorder();

        CorpusCounts counts = new StreamingTextImporter(new SplittingTokenizer())
                .importStream(new StringReader("\n\n  \n...\n\n"), 11, progress);

        assertEquals(0, counts.getWordCount());
        assertEquals(List.of(TextImporter.PROGRESS_COMPLETE), progress.values);
    }

    @Test
    void smallTextProducesExpectedCounts() throws IOException {
        CorpusCounts counts = streamed("The cat sat. The dog sat.");

        assertEquals(6, counts.getWordCount());
        assertEquals(Map.of("the", 2, "cat", 1, "dog", 1, "sat", 2), counts.getTotal());
        assertEquals(Map.of("the", 2), counts.getStarts());
        assertEquals(Map.of("sat", 2), counts.getEnds());
        assertEquals(Map.of(
                "the", Map.of("cat", 1, "dog", 1),
                "cat", Map.of("sat", 1),
                "dog", Map.of("sat", 1)), counts.getFollows());
    }

    @Test
    void textWithoutTrailingNewlineIsStillCounted() throws IOException {
        assertSameCounts(sequential("one two three."), streamed("one two three."));
        assertEquals(3, streamed("one two three.").getWordCount());
    }

    // comparison of streaming approach vs. default text importing

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 600, 1_000, 5_000})
    void matchesDefaultImporterAcrossCorpusSizes(int paragraphs) throws IOException {
        String text = makeCorpus(paragraphs, 42L);

        assertSameCounts(sequential(text), streamed(text));
    }

    @Test
    void acceptsAnAlreadyBufferedReader() throws IOException {
        String text = makeCorpus(2_000, 8L);
        CorpusCounts actual = new StreamingTextImporter(new SplittingTokenizer())
                .importStream(new BufferedReader(new StringReader(text)));

        assertSameCounts(sequential(text), actual);
    }

    @Test
    void windowsLineEndingsGiveSameCountsAsUnixLineEndings() throws IOException {
        String unix = makeCorpus(2_000, 21L);
        String windows = unix.replace("\n", "\r\n");

        assertSameCounts(streamed(unix), streamed(windows));
    }

    @Test
    void oneArgOverloadMatchesFullOverload() throws IOException {
        String text = makeCorpus(1_000, 2L);
        StreamingTextImporter importer = new StreamingTextImporter(new SplittingTokenizer());

        CorpusCounts full = importer.importStream(new StringReader(text), text.length(), v -> {});
        CorpusCounts shortcut = importer.importStream(new StringReader(text));

        assertSameCounts(full, shortcut);
    }

    @Test
    void importerIsStatelessAcrossCalls() throws IOException {
        String text = makeCorpus(1_000, 5L);
        StreamingTextImporter importer = new StreamingTextImporter(new SplittingTokenizer());

        CorpusCounts first = importer.importStream(new StringReader(text));
        CorpusCounts second = importer.importStream(new StringReader(text));

        assertNotSame(first, second);
        assertSameCounts(first, second);
    }

    // block cutting and memory bound tightness checks

    @Test
    void cutsAtBlankLineOnceBlockIsFull() throws IOException {
        CountingTokenizer tokenizer = new CountingTokenizer();
        String text = bigLine() + "\n\n" + bigLine() + "\n\n" + "tail words.";

        new StreamingTextImporter(tokenizer).importStream(new StringReader(text));

        assertEquals(3, tokenizer.blockLengths.size(), "two full blocks plus the trailing partial block");
    }

    @Test
    void doesNotCutBelowBlockSizeEvenWithBlankLines() throws IOException {
        CountingTokenizer tokenizer = new CountingTokenizer();
        String text = "a b c.\n\n".repeat(50); // far below BLOCK_CHARS

        new StreamingTextImporter(tokenizer).importStream(new StringReader(text));

        assertEquals(1, tokenizer.blockLengths.size());
    }

    @Test
    void doesNotCutWithoutBlankLinesNoMatterHowLong() throws IOException {
        CountingTokenizer tokenizer = new CountingTokenizer();
        String text = "w1 w2 w3 w4.\n".repeat(3 * BLOCK / 13 + 1); // > 3 blocks, no blank line

        CorpusCounts counts = new StreamingTextImporter(tokenizer).importStream(new StringReader(text));

        assertEquals(1, tokenizer.blockLengths.size());
        assertSameCounts(sequential(text), counts);
    }

    @Test
    void blockSizesAreBoundedForParagraphedText() throws IOException {
        CountingTokenizer tokenizer = new CountingTokenizer();
        String text = makeCorpus(5_000, 9L);

        new StreamingTextImporter(tokenizer).importStream(new StringReader(text));

        List<Integer> lengths = tokenizer.blockLengths;
        assertTrue(lengths.size() > 3, "corpus should span several blocks");
        for (int i = 0; i < lengths.size(); i++) {
            assertTrue(lengths.get(i) < BLOCK + 300, "block too large: " + lengths.get(i));
            if (i < lengths.size() - 1) {
                assertTrue(lengths.get(i) >= BLOCK, "non-final block cut too early: " + lengths.get(i));
            }
        }
    }

    @Test
    void sentenceSpanningBlankLineIsSplitUnlikeDefaultImporter() throws IOException {
        String padding = "pad ".repeat(BLOCK / 4 + 1); // one line, longer than BLOCK_CHARS
        String text = padding + "\n\nend of sentence.";

        assertEquals(Map.of("pad", 1), sequential(text).getStarts());
        assertEquals(Map.of("pad", 1, "end", 1), streamed(text).getStarts());
    }

    // threading-ness

    @Test
    void tokenizerAndProgressRunOnCallingThreadOnly() throws IOException {
        CountingTokenizer tokenizer = new CountingTokenizer();
        ProgressRecorder progress = new ProgressRecorder();
        String text = makeCorpus(5_000, 3L);

        new StreamingTextImporter(tokenizer).importStream(new StringReader(text), text.length(), progress);

        assertFalse(tokenizer.threads.isEmpty());
        tokenizer.threads.forEach(thread -> assertSame(Thread.currentThread(), thread));
        progress.threads.forEach(thread -> assertSame(Thread.currentThread(), thread));
    }

    // progress bar

    @Test
    void progressWithAccurateHintIsMonotonicAndEndsAtComplete() throws IOException {
        String text = makeCorpus(5_000, 3L);
        ProgressRecorder progress = new ProgressRecorder();

        new StreamingTextImporter(new SplittingTokenizer())
                .importStream(new StringReader(text), text.length(), progress);

        assertValidProgress(progress.values);
        assertTrue(progress.values.size() > 2, "multi-block input should report intermediate progress");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 0.5, 1.0, 1.5, 100.0})
    void progressStaysValidForUnderAndOverestimatedHints(double factor) throws IOException {
        String text = makeCorpus(5_000, 4L);
        long hint = Math.max(1, (long) (text.length() * factor));
        ProgressRecorder progress = new ProgressRecorder();

        new StreamingTextImporter(new SplittingTokenizer())
                .importStream(new StringReader(text), hint, progress);

        assertValidProgress(progress.values);
        for (int i = 0; i < progress.values.size() - 1; i++) {
            assertTrue(progress.values.get(i) <= 0.99, "intermediate progress must be capped at 0.99");
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { StreamTextImporter.UNKNOWN_LENGTH, 0 })
    void unknownOrZeroLengthReportsOnlyComplete(long expectedChars) throws IOException {
        ProgressRecorder progress = new ProgressRecorder();

        new StreamingTextImporter(new SplittingTokenizer())
                .importStream(new StringReader(makeCorpus(5_000, 6L)), expectedChars, progress);

        assertEquals(List.of(TextImporter.PROGRESS_COMPLETE), progress.values);
    }

    // failure and reader ownership checks
    @Test
    void doesNotCloseTheReader() throws IOException {
        TrackingReader reader = new TrackingReader(makeCorpus(100, 1L));

        new StreamingTextImporter(new SplittingTokenizer()).importStream(reader);

        assertFalse(reader.closed);
    }

    @Test
    void ioExceptionFromReaderPropagatesWithoutCompletion() {
        ProgressRecorder progress = new ProgressRecorder();
        StreamingTextImporter importer = new StreamingTextImporter(new SplittingTokenizer());

        IOException thrown = assertThrows(IOException.class, () ->
                importer.importStream(failingAfter(), 100, progress));

        assertEquals("boom", thrown.getMessage());
        assertFalse(progress.values.contains(TextImporter.PROGRESS_COMPLETE));
    }

    @Test
    void tokenizerFailurePropagatesWithoutCompletion() {
        AtomicInteger calls = new AtomicInteger();
        Tokenizer failing = new SplittingTokenizer() {
            @Override
            public List<List<String>> tokenize(String text) {
                if (calls.incrementAndGet() == 2) {
                    throw new IllegalStateException("bad block");
                }
                return super.tokenize(text);
            }
        };
        String text = makeCorpus(5_000, 12L);
        ProgressRecorder progress = new ProgressRecorder();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                new StreamingTextImporter(failing)
                        .importStream(new StringReader(text), text.length(), progress));

        assertEquals("bad block", thrown.getMessage());
        assertFalse(progress.values.contains(TextImporter.PROGRESS_COMPLETE));
    }

    // importing
    @Test
    void importFileMatchesImportStreamAndEndsAtComplete(@TempDir Path dir) throws IOException {
        String text = makeCorpus(2_000, 14L);
        Path file = dir.resolve("book.txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        ProgressRecorder progress = new ProgressRecorder();

        CorpusCounts actual = new StreamingTextImporter(new SplittingTokenizer()).importFile(file, progress);

        assertSameCounts(sequential(text), actual);
        assertValidProgress(progress.values);
    }

    @Test
    void importFileDecodesUtf8(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("accents.txt");
        Files.writeString(file, "caf\u00e9 na\u00efve. caf\u00e9.", StandardCharsets.UTF_8);

        CorpusCounts counts = new StreamingTextImporter(new SplittingTokenizer())
                .importFile(file, v -> {});

        assertEquals(Map.of("caf\u00e9", 2, "na\u00efve", 1), counts.getTotal());
    }

    @Test
    void importFileMissingFileThrowsIOException(@TempDir Path dir) {
        StreamingTextImporter importer = new StreamingTextImporter(new SplittingTokenizer());

        assertThrows(IOException.class, () -> importer.importFile(dir.resolve("nope.txt"), v -> {}));
    }

    @Test
    void importFileRejectsNullArguments(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a.txt");
        Files.writeString(file, "a b c.", StandardCharsets.UTF_8);
        StreamingTextImporter importer = new StreamingTextImporter(new SplittingTokenizer());

        assertThrows(NullPointerException.class, () -> importer.importFile(null, v -> {}));
        assertThrows(NullPointerException.class, () -> importer.importFile(file, null));
    }
}