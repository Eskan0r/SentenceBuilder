package com.team23.sentencebuilder.logic;

import java.util.function.DoubleConsumer;

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
