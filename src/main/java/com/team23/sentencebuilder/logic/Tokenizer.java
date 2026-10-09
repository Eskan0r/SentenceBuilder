package com.team23.sentencebuilder.logic;

import java.util.List;

/**
 * A component that helps break down raw text into smaller components for a
 * system to assign values and decorate properties to signify what the purpose
 * of a particular word is in existence and context to the raw text it was in.
 *
 * @author Alen Jo
 */
public interface Tokenizer {

    /** worker that helps take text and transforms it into a tokenized representation */
    List<List<String>> tokenize(String text);

    /** helps words conform to some uniform spec*/
    String normalize(String text);
}
