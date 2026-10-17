package com.team23.sentencebuilder.logic;

import java.util.function.DoubleConsumer;

/**
 * Turns a block of raw text into {@link CorpusCounts}.
 * <p>
 * Implementations report progress through a plain {@link DoubleConsumer}, so
 * they have no knowledge of any UI toolkit, and they do not persist anything.
 * <p>
 *
 * Progress represents values between 0.0 and 1.0 and is in monotonically increasing fashion.
 * Last value represents {@link #PROGRESS_COMPLETE}. Empty or punctuation-only
 * text is valid to which it yields empty counts and reports 1.0.
 *
 * @author Alen Jo
 */
public interface TextImporter {
    double PROGRESS_COMPLETE = 1.0;

    /**
     * Imports text and reports progress on operation
     *
     * @param text - raw text to import
     * @param progress - progress values as import advances
     *
     * @return - counts built from text
     * @throws NullPointerException if text or progress is null
     */
    CorpusCounts importText(String text, DoubleConsumer progress);

    /**
     * Imports text without reporting progress
     *
     * @param text - raw text to import
     *
     * @return - counts built from text
     * @throws NullPointerException if text or progress is null
     */
    default CorpusCounts importText(String text)
    {
        return importText(text, value -> {});
    }
}
