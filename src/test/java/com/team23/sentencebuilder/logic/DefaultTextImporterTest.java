package com.team23.sentencebuilder.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultTextImporterTest {
    private static final double EPSILON = 0.000001;

    private final List<Double> reported = new ArrayList<>(); // spy

    private void assertProgressContract() {
        assertFalse(reported.isEmpty());

        double previous = 0.0;
        for (double value : reported) {
            assertTrue(value >= 0.0 && value <= 1.0);
            assertTrue(value >= previous);
            previous = value;
        }
        assertEquals(TextImporter.PROGRESS_COMPLETE, reported.get(reported.size() - 1));
    }

    private static Tokenizer stubWithSentences(int count) {
        return new Tokenizer() {
            @Override
            public List<List<String>> tokenize(String text) {
                List<List<String>> sentences = new ArrayList<>();

                for (int ixSentence = 0; ixSentence < count; ixSentence++) {
                    sentences.add(List.of("a", "b", "c"));
                }

                return sentences;
            }

            @Override
            public String normalize(String word) {
                return word;
            }
        };
    }

    private static void assertEmptyCounts(CorpusCounts counts) {
        assertEquals(0, counts.getWordCount());
        assertTrue(counts.getTotal().isEmpty());
        assertTrue(counts.getStarts().isEmpty());
        assertTrue(counts.getEnds().isEmpty());
        assertTrue(counts.getFollows().isEmpty());
    }

    @Test
    public void smallTextHasExpectedWordStartAndEndCounts() {
        Tokenizer tokenizer = new RegexTokenizer();
        DefaultTextImporter importer = new DefaultTextImporter(tokenizer);

        CorpusCounts counts = importer.importText("The cat sat. The dog sat.", reported::add);

        assertEquals(6, counts.getWordCount());
        assertEquals(2, counts.getStarts().get(tokenizer.normalize("The")));
        assertEquals(2, counts.getEnds().get(tokenizer.normalize("sat")));
        assertProgressContract();
    }

    @Test
    public void manySentencesReportMonotonicProgressEndingAtOne() {
        int sentenceCount = 1_200;
        DefaultTextImporter importer = new DefaultTextImporter(stubWithSentences(sentenceCount));

        importer.importText("ignored", reported::add);

        assertProgressContract();
        assertEquals(sentenceCount / DefaultTextImporter.PROGRESS_INTERVAL + 1, reported.size());
        assertEquals((double) DefaultTextImporter.PROGRESS_INTERVAL / sentenceCount,  reported.getFirst(), EPSILON);
    }

    @Test
    public void exactMultipleOfIntervalReportsOneOnlyOnce() {
        int sentenceCount = DefaultTextImporter.PROGRESS_INTERVAL * 2;
        DefaultTextImporter importer = new DefaultTextImporter(stubWithSentences(sentenceCount));

        importer.importText("ignored", reported::add);

        assertProgressContract();
        assertEquals(1, reported.stream().filter(value -> value == 1.0).count());
    }

    @Test
    public void fewSentencesReportOnlyTheFinalOne() {
        DefaultTextImporter importer = new DefaultTextImporter(stubWithSentences(3));

        importer.importText("ignored", reported::add);

        assertEquals(List.of(1.0), reported);
    }

    @Test
    public void emptyTextReturnsEmptyCountsAndReportsOne() {
        DefaultTextImporter importer = new DefaultTextImporter(new RegexTokenizer());

        CorpusCounts counts = importer.importText("", reported::add);

        assertEmptyCounts(counts);
        assertEquals(List.of(1.0), reported);
    }

    @Test
    public void whitespaceOnlyTextBehavesLikeEmptyText() {
        DefaultTextImporter importer = new DefaultTextImporter(new RegexTokenizer());

        CorpusCounts counts = importer.importText("   \n\t ", reported::add);

        assertEmptyCounts(counts);
        assertEquals(List.of(1.0), reported);
    }

    @Test
    public void punctuationOnlyTextBehavesLikeEmptyText() {
        DefaultTextImporter importer = new DefaultTextImporter(new RegexTokenizer());

        CorpusCounts counts = importer.importText("... !!! ???", reported::add);

        assertEmptyCounts(counts);
        assertEquals(List.of(1.0), reported);
    }

    @Test
    public void overloadWithoutCallbackStillWorks() {
        DefaultTextImporter importer = new DefaultTextImporter(new RegexTokenizer());

        CorpusCounts counts = importer.importText("The cat sat. The dog sat.");

        assertEquals(6, counts.getWordCount());
    }

    @Test
    public void nullTokenizerIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new DefaultTextImporter(null));

        assertEquals("tokenizer must not be null", ex.getMessage());
    }

    @Test
    public void nullTextIsRejected() {
        DefaultTextImporter importer = new DefaultTextImporter(stubWithSentences(1));

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> importer.importText(null, reported::add));

        assertEquals("text must not be null", ex.getMessage());
    }

    @Test
    public void nullProgressIsRejected() {
        DefaultTextImporter importer = new DefaultTextImporter(stubWithSentences(1));

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> importer.importText("x", null));

        assertEquals("progress must not be null", ex.getMessage());
    }
}
