package com.team23.sentencebuilder.logic;

import java.util.List;

public interface Tokenizer {
    List<List<String>> tokenize(String text);
    String normalize(String text);
}
