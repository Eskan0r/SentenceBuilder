package edu.utdallas.sentencebuilder.ui;

import edu.utdallas.sentencebuilder.AppContext;
import edu.utdallas.sentencebuilder.model.Follower;
import edu.utdallas.sentencebuilder.model.WordRow;
import edu.utdallas.sentencebuilder.repo.WordRepository;
import edu.utdallas.sentencebuilder.text.Tokenizer;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;

import java.util.List;

/**
 * The auto-complete screen.  Nothing is predicted while a word is being
 * typed; suggestions are refreshed only when a delimiter (space, comma,
 * period, …) completes a word, exactly as the assignment asks.
 */
public final class AutoCompleteController implements Refreshable {

    private static final int SUGGESTION_LIMIT = 25;

    @FXML private TextArea editor;
    @FXML private ListView<Follower> suggestions;
    @FXML private Label prevLabel;
    @FXML private Label status;
    @FXML private Label sessionLabel;

    /** Number of words in the editor that have already been written to the database. */
    private int committedWords;
    /** The last committed word, or null when the next word starts a new sentence. */
    private String prevWord;
    private int suggestionGeneration;

    private int sessionWords;
    private int sessionSentences;
    private int sessionNewWords;

    @FXML
    private void initialize() {
        editor.textProperty().addListener((obs, oldText, newText) -> onTextChanged(oldText, newText));
        suggestions.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Follower f, boolean empty) {
                super.updateItem(f, empty);
                setText(empty || f == null ? null
                        : f.word() + "    ×" + f.count() + (f.chosenCount() > 0 ? "  (chosen " + f.chosenCount() + ")" : ""));
            }
        });
        suggestions.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                onInsert();
            }
        });
        suggestions.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                onInsert();
                e.consume();
            }
        });
        suggestions.setPlaceholder(new Label("No suggestions: nothing has followed that word yet."));
        updateSessionLabel();
    }

    @Override
    public void refresh() {
        loadSuggestions(prevWord);
    }

    // ---- typing -----------------------------------------------------------------------------

    private void onTextChanged(String oldText, String newText) {
        if (newText.length() < oldText.length()) {
            recomputeStateAfterDeletion(newText);
            return;
        }
        if (newText.isEmpty() || !isDelimiter(newText.charAt(newText.length() - 1))) {
            return;                                     // still inside a word: do nothing
        }
        List<String> words = Tokenizer.words(newText);
        for (int i = committedWords; i < words.size(); i++) {
            commitWord(words.get(i));
        }
        committedWords = words.size();
        char last = newText.charAt(newText.length() - 1);
        if (isSentenceEnd(last) && prevWord != null) {
            endSentence();
        }
    }

    private void commitWord(String word) {
        String prev = prevWord;
        Async.serial(() -> AppContext.get().autoComplete.recordWord(prev, word), isNew -> {
            sessionWords++;
            if (isNew) {
                sessionNewWords++;
                status.setText("\"" + word + "\" was new and has been added to the database.");
            } else if (prev != null) {
                status.setText("Recorded \"" + prev + "\" → \"" + word + "\".");
            } else {
                status.setText("Recorded \"" + word + "\" as a sentence start.");
            }
            updateSessionLabel();
        }, Dialogs::error);
        prevWord = word;
        loadSuggestions(word);
    }

    private void endSentence() {
        String last = prevWord;
        Async.serial(() -> {
            AppContext.get().autoComplete.recordSentenceEnd(last);
            return null;
        }, v -> {
            sessionSentences++;
            updateSessionLabel();
        }, Dialogs::error);
        prevWord = null;
        loadSuggestions(null);
    }

    /** The user deleted text: work out which words are still "committed" from what is left. */
    private void recomputeStateAfterDeletion(String text) {
        List<String> words = Tokenizer.words(text);
        boolean endsClean = text.isEmpty() || isDelimiter(text.charAt(text.length() - 1));
        int complete = endsClean ? words.size() : Math.max(0, words.size() - 1);
        committedWords = Math.min(committedWords, complete);
        String trimmed = text.stripTrailing();
        boolean sentenceOver = trimmed.isEmpty() || isSentenceEnd(trimmed.charAt(trimmed.length() - 1));
        String newPrev = committedWords == 0 || sentenceOver ? null : words.get(committedWords - 1);
        if (newPrev == null ? prevWord != null : !newPrev.equals(prevWord)) {
            prevWord = newPrev;
            loadSuggestions(prevWord);
        }
    }

    private static boolean isDelimiter(char c) {
        return Character.isWhitespace(c) || c == ',' || c == ';' || c == ':' || isSentenceEnd(c);
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?';
    }

    // ---- suggestions ----------------------------------------------------------------------------

    private void loadSuggestions(String prev) {
        int gen = ++suggestionGeneration;
        prevLabel.setText(prev == null ? "Words that start a sentence" : "Words that follow \"" + prev + "\"");
        Async.run(() -> {
            if (prev == null) {
                List<WordRow> starters = AppContext.get().words.search("", 1,
                        WordRepository.Sort.SENTENCE_STARTERS, SUGGESTION_LIMIT);
                return starters.stream().filter(w -> w.start() > 0)
                        .map(w -> new Follower(w.text(), w.start(), 0)).toList();
            }
            return AppContext.get().autoComplete.suggestions(prev, SUGGESTION_LIMIT);
        }, list -> {
            if (gen == suggestionGeneration) {
                suggestions.getItems().setAll(list);
            }
        }, Dialogs::error);
    }

    @FXML
    private void onInsert() {
        Follower f = suggestions.getSelectionModel().getSelectedItem();
        if (f == null) {
            return;
        }
        String text = editor.getText();
        if (!text.isEmpty() && !isDelimiter(text.charAt(text.length() - 1))) {
            text += " ";
        }
        editor.setText(text + f.word() + " ");   // the trailing space commits the word
        editor.positionCaret(editor.getText().length());
        editor.requestFocus();
    }

    @FXML
    private void onNewText() {
        editor.clear();
        committedWords = 0;
        prevWord = null;
        loadSuggestions(null);
    }

    @FXML
    private void onSaveSession() {
        if (sessionWords == 0) {
            status.setText("Nothing typed yet.");
            return;
        }
        int w = sessionWords, s = sessionSentences, n = sessionNewWords;
        Async.run(() -> {
            AppContext.get().autoComplete.recordSession(w, s, n);
            return null;
        }, v -> {
            sessionWords = sessionSentences = sessionNewWords = 0;
            updateSessionLabel();
            status.setText("Session saved to the import history.");
        }, Dialogs::error);
    }

    private void updateSessionLabel() {
        sessionLabel.setText("This session: " + sessionWords + " words, " + sessionSentences + " sentences, "
                + sessionNewWords + " new words");
    }
}
