package com.team23.sentencebuilder.logic;

import java.util.List;

/**
 * One observed step in the chain: after the words in context, the word next appeared.
 *
 * @param context - the preceding words, oldest first (may include Boundary.START)
 * @param next - the word that followed, or Boundary.END
 */
public record Transition(List<String> context, String next) {
}
