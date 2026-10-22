package com.team23.sentencebuilder.ui;

import com.team23.sentencebuilder.io.ImportResult;
import com.team23.sentencebuilder.io.TextImporter;
import com.team23.sentencebuilder.logic.CorpusCounts;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller of the Import tab, which is the only tab that does anything so
 * far. It lets the user pick a text file, imports it on a background thread
 * with a progress bar, and then shows what the session now holds: the files
 * that were imported and every word with its counts.
 * <p>
 * Nothing here is written to a database. The counts live in a
 * {@link CorpusCounts} for as long as the window is open, so this tab can be
 * wired up to persistence later without changing the UI.
 *
 * @author Ronak Chavva
 */
public class ImportController {
    private static final DateTimeFormatter IMPORTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML
    private Button importButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button clearButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label statusLabel;

    @FXML
    private Label wordsLabel;

    @FXML
    private TableView<ImportResult> filesTable;

    @FXML
    private TableColumn<ImportResult, String> fileNameColumn;

    @FXML
    private TableColumn<ImportResult, Integer> fileWordsColumn;

    @FXML
    private TableColumn<ImportResult, Integer> fileSentencesColumn;

    @FXML
    private TableColumn<ImportResult, Integer> fileNewWordsColumn;

    @FXML
    private TableColumn<ImportResult, String> importedAtColumn;

    @FXML
    private TableView<WordRow> wordsTable;

    @FXML
    private TableColumn<WordRow, String> wordColumn;

    @FXML
    private TableColumn<WordRow, Integer> countColumn;

    @FXML
    private TableColumn<WordRow, Integer> startsColumn;

    @FXML
    private TableColumn<WordRow, Integer> endsColumn;

    @FXML
    private TextField filterField;

    private final TextImporter importer = new TextImporter();
    private final ObservableList<ImportResult> importedFiles = FXCollections.observableArrayList();
    private final ObservableList<WordRow> allWords = FXCollections.observableArrayList();
    private final FilteredList<WordRow> filteredWords = new FilteredList<>(allWords);
    private final SortedList<WordRow> sortedWords = new SortedList<>(filteredWords);
    private final BooleanProperty importing = new SimpleBooleanProperty(false);

    private CorpusCounts counts = new CorpusCounts();
    private Task<ImportResult> importTask;

    @FXML
    private void initialize() {
        importButton.disableProperty().bind(importing);
        cancelButton.disableProperty().bind(importing.not());
        clearButton.disableProperty().bind(Bindings.isEmpty(importedFiles));

        wireFilesTable();
        wireWordsTable();
    }

    /**
     * Shows a file chooser and imports whatever text file the user picks.
     */
    @FXML
    private void onImportClicked() {
        if (importing.get()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Text File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text files (*.txt, *.text)", "*.txt", "*.text"),
                new FileChooser.ExtensionFilter("All files (*.*)", "*.*"));

        File chosen = chooser.showOpenDialog(window());

        if (chosen != null) {
            startImport(chosen.toPath());
        }
    }

    /**
     * Stops the import that is running, if there is one. Words already
     * counted before the cancel are thrown away with the rest of the file.
     */
    @FXML
    private void onCancelClicked() {
        if (importTask != null) {
            importTask.cancel(true);
        }
    }

    /**
     * Forgets everything this session imported, after confirming with the user.
     */
    @FXML
    private void onClearClicked() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(window());
        confirmation.setTitle("Clear Session");
        confirmation.setHeaderText("Clear all imported text?");
        confirmation.setContentText("Every imported file, word count, and transition held in memory "
                + "will be discarded. Nothing is saved anywhere, so this cannot be undone.");

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        counts = new CorpusCounts();
        importedFiles.clear();
        allWords.clear();
        wordsLabel.setText("No words yet");
        progressBar.setProgress(0.0);
        statusLabel.setText("Session cleared. Choose a text file to begin.");
    }

    /**
     * Runs the import off the JavaFX thread so the window stays responsive
     * while a large novel is being read.
     *
     * @param path - file to import
     */
    private void startImport(Path path) {
        Task<ImportResult> task = new Task<>() {
            @Override
            protected ImportResult call() throws Exception {
                return importer.importFile(path, counts,
                        (fraction, message) -> {
                            updateProgress(fraction, 1.0);
                            updateMessage(message);
                        },
                        this::isCancelled);
            }
        };

        task.setOnSucceeded(event -> {
            finishImportDisplay();
            ImportResult result = task.getValue();
            importedFiles.add(0, result);
            refreshWordTable();
            progressBar.setProgress(1.0);
            statusLabel.setText(String.format("Imported %s - %,d words in %,d sentences, %,d of them new to this session.",
                    result.fileName(), result.wordCount(), result.sentenceCount(), result.newWords()));
        });

        task.setOnCancelled(event -> {
            finishImportDisplay();
            progressBar.setProgress(0.0);
            statusLabel.setText("Import cancelled. Nothing from that file was kept.");
        });

        task.setOnFailed(event -> {
            finishImportDisplay();
            progressBar.setProgress(0.0);
            Throwable cause = task.getException();
            statusLabel.setText("Import failed: " + (cause == null ? "unknown error" : cause.getMessage()));
        });

        importTask = task;
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().unbind();
        statusLabel.textProperty().bind(task.messageProperty());
        importing.set(true);

        Thread worker = new Thread(task, "text-import");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Puts the status label and the progress bar back under the control of
     * this controller once a task is no longer running.
     */
    private void finishImportDisplay() {
        statusLabel.textProperty().unbind();
        importing.set(false);
        importTask = null;
    }

    /**
     * Rebuilds the word table from the session counts and refreshes the
     * summary label. Must run on the JavaFX thread after an import finished.
     */
    private void refreshWordTable() {
        Map<String, Integer> totals = counts.getTotal();
        Map<String, Integer> starts = counts.getStarts();
        Map<String, Integer> ends = counts.getEnds();

        List<WordRow> rows = new ArrayList<>(totals.size());

        for (Map.Entry<String, Integer> wordEntry : totals.entrySet()) {
            String word = wordEntry.getKey();
            rows.add(new WordRow(word, wordEntry.getValue(),
                    starts.getOrDefault(word, 0), ends.getOrDefault(word, 0)));
        }

        allWords.setAll(rows);
        wordsLabel.setText(String.format("%,d unique words in this session", rows.size()));
    }

    /**
     * Sets up the imported files table: newest import on top, five columns,
     * and a hint when there is nothing to show.
     */
    private void wireFilesTable() {
        filesTable.setItems(importedFiles);
        filesTable.setPlaceholder(new Label("No files imported yet."));

        fileNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().fileName()));
        fileWordsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().wordCount()));
        fileSentencesColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().sentenceCount()));
        fileNewWordsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().newWords()));
        importedAtColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(IMPORTED_AT_FORMAT.format(cell.getValue().importedAt())));
    }

    /**
     * Sets up the word table: sorted alphabetically unless the user clicks
     * another column, filterable through the filter field, and with a hint
     * when no words have been imported yet.
     */
    private void wireWordsTable() {
        wordsTable.setItems(sortedWords);
        wordsTable.setPlaceholder(new Label("No words yet. Import a text file to fill this table."));

        wordColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().word()));
        countColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().count()));
        startsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().starts()));
        endsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().ends()));

        sortedWords.comparatorProperty().bind(wordsTable.comparatorProperty());
        wordColumn.setSortType(TableColumn.SortType.ASCENDING);
        wordsTable.getSortOrder().add(wordColumn);

        filterField.textProperty().addListener((observable, oldText, newText) -> {
            String query = newText == null ? "" : newText.trim().toLowerCase(Locale.ROOT);
            filteredWords.setPredicate(query.isEmpty()
                    ? null
                    : row -> row.word().toLowerCase(Locale.ROOT).contains(query));
        });
    }

    /**
     * @return - the window that dialogs should hang off of
     */
    private Window window() {
        return statusLabel.getScene() == null ? null : statusLabel.getScene().getWindow();
    }
}
