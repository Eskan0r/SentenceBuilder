package edu.utdallas.sentencebuilder.ui;

import edu.utdallas.sentencebuilder.AppContext;
import edu.utdallas.sentencebuilder.generate.GenerationOptions;
import edu.utdallas.sentencebuilder.generate.SentenceGenerator;
import edu.utdallas.sentencebuilder.model.GeneratedSentence;
import edu.utdallas.sentencebuilder.model.ImportedFile;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import java.util.ArrayList;
import java.util.List;

/**
 * Generate N sentences with the chosen algorithm, then optionally feed them
 * back into the corpus (requirement 2 of the generative part).
 */
public final class GenerateController implements Refreshable {

    @FXML private TextField startWord;
    @FXML private CheckBox randomStart;
    @FXML private ComboBox<SentenceGenerator> generator;
    @FXML private Spinner<Integer> count;
    @FXML private Spinner<Integer> minWords;
    @FXML private Spinner<Integer> maxWords;
    @FXML private Button generateButton;
    @FXML private Button feedBackButton;
    @FXML private Label algorithmHint;
    @FXML private ListView<GeneratedSentence> results;
    @FXML private Label status;

    @FXML
    private void initialize() {
        generator.getItems().setAll(AppContext.get().generators);
        generator.getSelectionModel().selectFirst();
        generator.valueProperty().addListener((o, a, g) -> algorithmHint.setText(g == null ? "" : g.description()));
        algorithmHint.setText(generator.getValue().description());

        Spinners.integer(count, 1, 200, 10);
        Spinners.integer(minWords, 1, 20, 3);
        Spinners.integer(maxWords, 2, 80, 25);
        startWord.disableProperty().bind(randomStart.selectedProperty());

        results.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GeneratedSentence s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText((getIndex() + 1) + ".  " + s.text());
                    setTooltip(new Tooltip(s.generatorName() + " · start \"" + s.startWord() + "\" · "
                            + s.words().size() + " words"));
                }
            }
        });
        results.setPlaceholder(new Label("Generated sentences appear here."));
    }

    @Override
    public void refresh() {
        // Nothing to reload; generators read the database live.
    }

    @FXML
    private void onGenerate() {
        SentenceGenerator gen = generator.getValue();
        boolean random = randomStart.isSelected();
        String start = startWord.getText().trim();
        if (!random && start.isEmpty()) {
            status.setText("Type a start word or tick \"Random start word\".");
            return;
        }
        int n = Spinners.value(count);
        int min = Spinners.value(minWords);
        int max = Math.max(min, Spinners.value(maxWords));
        GenerationOptions opts = new GenerationOptions(min, max);

        generateButton.setDisable(true);
        status.setText("Generating…");
        Async.run(() -> {
            List<GeneratedSentence> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                GeneratedSentence s = random ? gen.generateFromRandomStart(opts) : gen.generate(start, opts, false);
                out.add(AppContext.get().sentences.save(s));
            }
            return out;
        }, sentences -> {
            results.getItems().addAll(sentences);
            feedBackButton.setDisable(results.getItems().isEmpty());
            generateButton.setDisable(false);
            status.setText(sentences.size() + " sentence(s) generated with \"" + gen.name() + "\" and saved to history.");
        }, err -> {
            generateButton.setDisable(false);
            status.setText(err.getMessage() != null ? err.getMessage() : "Generation failed.");
            if (!(err instanceof IllegalArgumentException) && !(err instanceof IllegalStateException)) {
                Dialogs.error(err);
            }
        });
    }

    @FXML
    private void onFeedBack() {
        List<GeneratedSentence> batch = new ArrayList<>(results.getItems());
        if (batch.isEmpty()) {
            return;
        }
        feedBackButton.setDisable(true);
        status.setText("Adding " + batch.size() + " sentences to the corpus…");
        Async.run(() -> {
            ImportedFile f = AppContext.get().importService.importGenerated(batch, (frac, msg) -> { });
            AppContext.get().sentences.markFedBack(batch.stream().map(GeneratedSentence::id).toList(), f.id());
            return f;
        }, f -> {
            results.getItems().clear();
            status.setText(String.format("Added %,d words from %d sentences (%d new words). See the Import tab.",
                    f.wordCount(), f.sentenceCount(), f.newWords()));
        }, err -> {
            feedBackButton.setDisable(false);
            status.setText("Could not add sentences.");
            Dialogs.error(err);
        });
    }

    @FXML
    private void onClear() {
        results.getItems().clear();
        feedBackButton.setDisable(true);
        status.setText("");
    }
}
