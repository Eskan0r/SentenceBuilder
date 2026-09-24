package edu.utdallas.sentencebuilder.ui;

import edu.utdallas.sentencebuilder.AppContext;
import edu.utdallas.sentencebuilder.model.CorpusSummary;
import edu.utdallas.sentencebuilder.model.GeneratedSentence;
import edu.utdallas.sentencebuilder.model.WordRow;
import edu.utdallas.sentencebuilder.repo.WordRepository;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;

/** Summary numbers, generated-sentence history with duplicate detection, and starter/ender reports. */
public final class ReportsController implements Refreshable {

    private static final int TOP = 100;

    @FXML private Label sDistinct;
    @FXML private Label sTokens;
    @FXML private Label sSentences;
    @FXML private Label sBigrams;
    @FXML private Label sFiles;
    @FXML private Label sGenerated;
    @FXML private Label sDistinctGenerated;

    @FXML private TextField sentenceFilter;
    @FXML private CheckBox duplicatesOnly;
    @FXML private Label sentenceCount;
    @FXML private TableView<GeneratedSentence> sentences;
    @FXML private TableColumn<GeneratedSentence, String> colSentence;
    @FXML private TableColumn<GeneratedSentence, String> colGenerator;
    @FXML private TableColumn<GeneratedSentence, String> colStartWord;
    @FXML private TableColumn<GeneratedSentence, String> colRandom;
    @FXML private TableColumn<GeneratedSentence, Integer> colTimes;
    @FXML private TableColumn<GeneratedSentence, String> colFedBack;
    @FXML private TableColumn<GeneratedSentence, String> colGeneratedAt;

    @FXML private TableView<WordRow> starters;
    @FXML private TableColumn<WordRow, String> colStarterWord;
    @FXML private TableColumn<WordRow, Integer> colStarterCount;
    @FXML private TableColumn<WordRow, String> colStarterPct;
    @FXML private TableView<WordRow> enders;
    @FXML private TableColumn<WordRow, String> colEnderWord;
    @FXML private TableColumn<WordRow, Integer> colEnderCount;
    @FXML private TableColumn<WordRow, String> colEnderPct;
    @FXML private TableView<WordRow> neverStart;
    @FXML private TableColumn<WordRow, String> colNeverWord;
    @FXML private TableColumn<WordRow, Integer> colNeverCount;

    @FXML
    private void initialize() {
        colSentence.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().text()));
        colGenerator.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().generatorName()));
        colStartWord.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().startWord()));
        colRandom.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().randomStart() ? "yes" : ""));
        colTimes.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().duplicates()));
        colFedBack.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().fedBack() ? "yes" : ""));
        colGeneratedAt.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().generatedAt() == null ? "" : ImportController.WHEN.format(cd.getValue().generatedAt())));
        sentences.setPlaceholder(new Label("No generated sentences yet."));

        colStarterWord.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().text()));
        colStarterCount.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().start()));
        colStarterPct.setCellValueFactory(cd -> new ReadOnlyStringWrapper(pct(cd.getValue().start(), cd.getValue().total())));
        colEnderWord.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().text()));
        colEnderCount.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().end()));
        colEnderPct.setCellValueFactory(cd -> new ReadOnlyStringWrapper(pct(cd.getValue().end(), cd.getValue().total())));
        colNeverWord.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().text()));
        colNeverCount.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().total()));
    }

    @FXML
    @Override
    public void refresh() {
        Async.run(() -> AppContext.get().words.summary(), this::showSummary);
        loadSentences();
        Async.run(() -> AppContext.get().words.search("", 1, WordRepository.Sort.SENTENCE_STARTERS, TOP),
                rows -> starters.getItems().setAll(rows.stream().filter(w -> w.start() > 0).toList()));
        Async.run(() -> AppContext.get().words.search("", 1, WordRepository.Sort.SENTENCE_ENDERS, TOP),
                rows -> enders.getItems().setAll(rows.stream().filter(w -> w.end() > 0).toList()));
        Async.run(() -> {
            // "Never starts a sentence": frequent words with start_count == 0.
            List<WordRow> frequent = AppContext.get().words.search("", 1, WordRepository.Sort.MOST_FREQUENT, TOP * 20);
            return frequent.stream().filter(w -> w.start() == 0).limit(TOP).toList();
        }, neverStart.getItems()::setAll);
    }

    private void showSummary(CorpusSummary s) {
        sDistinct.setText(String.format("%,d", s.distinctWords()));
        sTokens.setText(String.format("%,d", s.totalTokens()));
        sSentences.setText(String.format("%,d", s.sentenceStarts()));
        sBigrams.setText(String.format("%,d", s.bigrams()));
        sFiles.setText(String.format("%,d", s.filesImported()));
        sGenerated.setText(String.format("%,d", s.sentencesGenerated()));
        sDistinctGenerated.setText(String.format("%,d", s.distinctSentencesGenerated()));
    }

    @FXML
    private void loadSentences() {
        boolean dups = duplicatesOnly.isSelected();
        String f = sentenceFilter.getText();
        Async.run(() -> AppContext.get().sentences.history(dups, f, 2_000), rows -> {
            sentences.getItems().setAll(rows);
            sentenceCount.setText(rows.size() + " sentence(s)" + (rows.size() >= 2_000 ? " (limit reached)" : ""));
        });
    }

    private static String pct(int part, int total) {
        return total == 0 ? "" : String.format("%.0f%%", 100.0 * part / total);
    }
}
