package com.team23.sentencebuilder.logic;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;

/**
 * Sentence-based {@link TextImporter}: tokenizes the whole text up front with
 * injected {@link Tokenizer}. Then, it adds each sentence to a new {@link CorpusCounts}
 * one-by-one on calling thread
 * <p>
 * As tokenization happens before first progress report, progress bar sits at 0 during this
 * stage.
 *
 * @author Alen Jo
 */
public class DefaultTextImporter implements TextImporter {
    static final int PROGRESS_INTERVAL = 500; // sentences processed between intervals
    private final Tokenizer tokenizer;

    /**
     * Creates an importer that uses the given tokenizer
     *
     * @param tokenizer - tokenizer used to split text into sentences
     * @throws NullPointerException - if the tokenizer is null
     */
    public DefaultTextImporter(Tokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
    }

    /**
     * Goes through all the sentences and creates corpus counts for each of the words within
     * the block of text
     *
     * @param text - raw text to import
     * @param progress - progress values as import advances
     *
     * @return {@link CorpusCounts} object containing all counts of text from raw text
     */
    @Override
    public CorpusCounts importText(String text, DoubleConsumer progress) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(progress, "progress must not be null");

        List<List<String>> sentences = tokenizer.tokenize(text);
        int totalSentences = sentences.size();
        CorpusCounts corpusCounts = new CorpusCounts();

        for (int ixSentence = 0; ixSentence < totalSentences; ixSentence++) {
            corpusCounts.addSentence(sentences.get(ixSentence));

            int processed = ixSentence + 1;
            if (processed % PROGRESS_INTERVAL == 0 && processed < totalSentences) {
                progress.accept((double) processed / totalSentences);
            }
        }
        
        progress.accept(PROGRESS_COMPLETE);
        return corpusCounts;
    }
}
