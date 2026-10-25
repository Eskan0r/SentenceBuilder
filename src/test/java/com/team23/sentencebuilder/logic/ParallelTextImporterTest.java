package com.team23.sentencebuilder.logic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ParallelTextImporterTest {
    private static final int POOL_SIZE = 4;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(POOL_SIZE);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static Tokenizer fixed(List<List<String>> sentences) {
        return new Tokenizer() {
            @Override
            public List<List<String>> tokenize(String text) {
                return sentences;
            }

            @Override
            public String normalize(String text) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static List<List<String>> makeSentences(int count, long seed) {
        Random random = new Random(seed);
        List<List<String>> sentences = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int length = random.nextInt(13);
            List<String> sentence = new ArrayList<>(length);
            for (int j = 0; j < length; j++) {
                sentence.add("w" + random.nextInt(40));
            }
            sentences.add(sentence);
        }
        return sentences;
    }

    private static void assertSameCounts(CorpusCounts expected, CorpusCounts actual) {
        assertEquals(expected.getWordCount(), actual.getWordCount(), "wordCount");
        assertEquals(expected.getTotal(), actual.getTotal(), "totals");
        assertEquals(expected.getStarts(), actual.getStarts(), "starts");
        assertEquals(expected.getEnds(), actual.getEnds(), "ends");
        assertEquals(expected.getFollows(), actual.getFollows(), "follows");
    }

    /** Records every progress value and the thread it was reported on. */
    private static class ProgressRecorder implements java.util.function.DoubleConsumer {
        final List<Double> values = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();

        @Override
        public void accept(double value) {
            values.add(value);
            threads.add(Thread.currentThread());
        }
    }

    // construction and argument checks
    @Test
    void constructorRejectsNullTokenizer() {
        assertThrows(NullPointerException.class,
                () -> new ParallelTextImporter(null, executor, POOL_SIZE));
    }

    @Test
    void constructorRejectsNullExecutor() {
        assertThrows(NullPointerException.class,
                () -> new ParallelTextImporter(fixed(List.of()), null, POOL_SIZE));
    }

    @Test
    void constructorRejectsParallelismBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelTextImporter(fixed(List.of()), executor, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelTextImporter(fixed(List.of()), executor, -3));
    }

    @Test
    void importTextRejectsNullArguments() {
        TextImporter importer = new ParallelTextImporter(fixed(List.of()), executor, POOL_SIZE);
        assertThrows(NullPointerException.class, () -> importer.importText(null, v -> {}));
        assertThrows(NullPointerException.class, () -> importer.importText("text", null));
        assertThrows(NullPointerException.class, () -> importer.importText(null));
    }

    // empty and tiny input

    @Test
    void emptyTextYieldsEmptyCountsAndReportsOnlyComplete() {
        TextImporter importer = new ParallelTextImporter(fixed(List.of()), executor, POOL_SIZE);
        ProgressRecorder progress = new ProgressRecorder();

        CorpusCounts counts = importer.importText("", progress);

        assertEquals(0, counts.getWordCount());
        assertTrue(counts.getTotal().isEmpty());
        assertEquals(List.of(TextImporter.PROGRESS_COMPLETE), progress.values);
    }

    @Test
    void onlyEmptySentencesYieldsEmptyCountsAndEndsAtComplete() {
        List<List<String>> sentences = List.of(List.of(), List.of(), List.of());
        TextImporter importer = new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE);
        ProgressRecorder progress = new ProgressRecorder();

        CorpusCounts counts = importer.importText("...", progress);

        assertEquals(0, counts.getWordCount());
        assertEquals(TextImporter.PROGRESS_COMPLETE, progress.values.getLast());
    }

    @Test
    void singleChunkInputMatchesSequential() {
        List<List<String>> sentences = makeSentences(50, 1L); // below MIN_CHUNK_SIZE
        CorpusCounts expected = new DefaultTextImporter(fixed(sentences)).importText("x");
        CorpusCounts actual = new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE).importText("x");

        assertSameCounts(expected, actual);
    }

    // comparison of parallel vs. sequential (aka default)

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    void matchesSequentialImporterAcrossParallelism(int parallelism) {
        List<List<String>> sentences = makeSentences(10_000, 42L); // multiple chunks
        CorpusCounts expected = new DefaultTextImporter(fixed(sentences)).importText("x");

        CorpusCounts actual = new ParallelTextImporter(fixed(sentences), executor, parallelism)
                .importText("x");

        assertSameCounts(expected, actual);
    }

    @Test
    void repeatedRunsAreDeterministic() {
        List<List<String>> sentences = makeSentences(10_000, 7L);
        ParallelTextImporter importer = new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE);
        CorpusCounts first = importer.importText("x");

        for (int run = 0; run < 20; run++) {
            assertSameCounts(first, importer.importText("x"));
        }
    }

    @Test
    void wordCountEqualsSumOfTotals() {
        List<List<String>> sentences = makeSentences(10_000, 99L);
        CorpusCounts counts = new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE).importText("x");

        int sum = counts.getTotal().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(sum, counts.getWordCount());
    }

    // progress bar

    @Test
    void progressIsMonotonicBoundedAndEndsExactlyAtComplete() {
        List<List<String>> sentences = makeSentences(10_000, 3L);
        ProgressRecorder progress = new ProgressRecorder();

        new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE).importText("x", progress);

        assertTrue(progress.values.size() > 1, "large input should report intermediate progress");
        assertEquals(TextImporter.PROGRESS_COMPLETE, progress.values.getLast());

        double previous = 0.0;
        for (int i = 0; i < progress.values.size(); i++) {
            double value = progress.values.get(i);
            assertTrue(value > 0.0 && value <= 1.0, "out of range: " + value);
            assertTrue(value >= previous, "progress went backwards: " + previous + " -> " + value);
            if (i < progress.values.size() - 1) {
                assertTrue(value < TextImporter.PROGRESS_COMPLETE,
                        "1.0 must only be reported last");
            }
            previous = value;
        }
    }

    @Test
    void progressIsReportedOnlyOnCallingThread() {
        List<List<String>> sentences = makeSentences(10_000, 5L);
        ProgressRecorder progress = new ProgressRecorder();

        new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE).importText("x", progress);

        assertFalse(progress.threads.isEmpty());
        progress.threads.forEach(thread -> assertSame(Thread.currentThread(), thread));
    }

    // failure, interruption, executor ownership (concurrency control)

    @Test
    void workerFailureSurfacesAsIllegalStateExceptionWithCause() {
        List<List<String>> sentences = makeSentences(2_000, 11L);
        sentences.set(1_500, null); // addSentence(null) throws NPE inside a worker
        ProgressRecorder progress = new ProgressRecorder();
        ParallelTextImporter importer = new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> importer.importText("x", progress));

        assertInstanceOf(NullPointerException.class, thrown.getCause());
        assertFalse(progress.values.contains(TextImporter.PROGRESS_COMPLETE),
                "must not report completion when the import failed");
    }

    @Test
    void interruptedCallerGetsCancellationAndInterruptStatusIsRestored() {
        List<List<String>> sentences = makeSentences(5_000, 13L);
        ParallelTextImporter importer = new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE);

        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> importer.importText("x"));
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt status must be restored");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void doesNotShutDownInjectedExecutor() {
        List<List<String>> sentences = makeSentences(2_000, 17L);
        new ParallelTextImporter(fixed(sentences), executor, POOL_SIZE).importText("x");

        assertFalse(executor.isShutdown());
    }

    @Test
    void importerIsReusableAfterAFailure() {
        List<List<String>> bad = makeSentences(2_000, 19L);
        bad.set(1_500, null);
        assertThrows(IllegalStateException.class,
                () -> new ParallelTextImporter(fixed(bad), executor, POOL_SIZE).importText("x"));

        List<List<String>> good = makeSentences(2_000, 19L);
        CorpusCounts expected = new DefaultTextImporter(fixed(good)).importText("x");
        CorpusCounts actual = new ParallelTextImporter(fixed(good), executor, POOL_SIZE).importText("x");
        assertSameCounts(expected, actual);
    }
}