package com.team23.sentencebuilder.ui;

/**
 * One row of the word table on the Import tab: a word plus everything the
 * session knows about it so far.
 *
 * @param word - the normalized (lowercased) word
 * @param count - how many times the word occurred in the session
 * @param starts - how many sentences began with the word
 * @param ends - how many sentences ended with the word
 *
 * @author Ronak Chavva
 */
public record WordRow(String word, int count, int starts, int ends) {
}
