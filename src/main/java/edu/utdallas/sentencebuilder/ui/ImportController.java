package edu.utdallas.sentencebuilder.ui;

import edu.utdallas.sentencebuilder.AppContext;
import edu.utdallas.sentencebuilder.model.ImportedFile;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Queue files, import them one after another with a progress bar, and show the import history. */
public final class ImportController implements Refreshable {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private ListView<Path> queue;
    @FXML private Button importButton;
    @FXML private Button cancelButton;
    @FXML private ProgressBar progress;
    @FXML private Label status;
    @FXML private TableView<ImportedFile> history;
    @FXML private TableColumn<ImportedFile, String> colName;
    @FXML private TableColumn<ImportedFile, String> colSource;
    @FXML private TableColumn<ImportedFile, Integer> colWords;
    @FXML private TableColumn<ImportedFile, Integer> colSentences;
    @FXML private TableColumn<ImportedFile, Integer> colDistinct;
    @FXML private TableColumn<ImportedFile, Integer> colNew;
    @FXML private TableColumn<ImportedFile, String> colSize;
    @FXML private TableColumn<ImportedFile, String> colTime;
    @FXML private TableColumn<ImportedFile, String> colWhen;

    private final ObservableList<Path> files = FXCollections.observableArrayList();
    private final Set<String> previouslyImported = new HashSet<>();
    private Task<Void> running;

    @FXML
    private void initialize() {
        queue.setItems(files);
        queue.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        queue.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                } else {
                    boolean dup = previouslyImported.contains(p.toAbsolutePath().toString());
                    setText(p.getFileName() + (dup ? "   (already imported before — counts will be added again)" : "")
                            + "    " + p.toAbsolutePath().getParent());
                }
            }
        });

        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().fileName()));
        colSource.setCellValueFactory(cd -> new ReadOnlyStringWrapper(label(cd.getValue().sourceType())));
        colWords.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().wordCount()));
        colSentences.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().sentenceCount()));
        colDistinct.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().distinctWords()));
        colNew.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().newWords()));
        colSize.setCellValueFactory(cd -> new ReadOnlyStringWrapper(humanBytes(cd.getValue().byteSize())));
        colTime.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().importMillis() / 1000.0 + " s"));
        colWhen.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().importedAt() == null ? "" : WHEN.format(cd.getValue().importedAt())));
        history.setPlaceholder(new Label("Nothing imported yet."));
    }

    @Override
    public void refresh() {
        Async.run(() -> AppContext.get().files.all(), rows -> {
            history.getItems().setAll(rows);
            previouslyImported.clear();
            for (ImportedFile f : rows) {
                if (f.filePath() != null) {
                    previouslyImported.add(f.filePath());
                }
            }
            queue.refresh();
        });
    }

    @FXML
    private void onAddFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose text files to import");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text files", "*.txt", "*.text", "*.md"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        List<File> chosen = fc.showOpenMultipleDialog(queue.getScene().getWindow());
        if (chosen != null) {
            for (File f : chosen) {
                Path p = f.toPath();
                if (!files.contains(p)) {
                    files.add(p);
                }
            }
            status.setText(files.size() + " file(s) queued.");
        }
    }

    @FXML
    private void onRemove() {
        files.removeAll(new ArrayList<>(queue.getSelectionModel().getSelectedItems()));
    }

    @FXML
    private void onClear() {
        files.clear();
        status.setText("No files queued.");
    }

    @FXML
    private void onImport() {
        if (files.isEmpty()) {
            status.setText("Add some files first.");
            return;
        }
        List<Path> batch = new ArrayList<>(files);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i < batch.size(); i++) {
                    if (isCancelled()) {
                        break;
                    }
                    Path p = batch.get(i);
                    String prefix = "[" + (i + 1) + "/" + batch.size() + "] " + p.getFileName() + ": ";
                    ImportedFile result = AppContext.get().importService.importFile(p, (frac, msg) -> {
                        updateProgress(frac < 0 ? -1 : frac, 1);
                        updateMessage(prefix + msg);
                    });
                    Platform.runLater(() -> {
                        files.remove(p);
                        history.getItems().add(0, result);
                        previouslyImported.add(result.filePath());
                    });
                    updateMessage(String.format("%s%,d words, %,d sentences, %,d new words in %.1f s",
                            prefix, result.wordCount(), result.sentenceCount(), result.newWords(),
                            result.importMillis() / 1000.0));
                }
                return null;
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> finish(task, null));
        task.setOnCancelled(e -> finish(task, "Import cancelled."));
        task.setOnFailed(e -> {
            finish(task, "Import failed.");
            Dialogs.error(task.getException());
        });
        running = task;
        importButton.setDisable(true);
        cancelButton.setDisable(false);
        Thread t = new Thread(task, "import");
        t.setDaemon(true);
        t.start();
    }

    private void finish(Task<Void> task, String message) {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        if (message != null) {
            status.setText(message);
        } else {
            status.setText(task.getMessage());
            progress.setProgress(1);
        }
        importButton.setDisable(false);
        cancelButton.setDisable(true);
        running = null;
    }

    @FXML
    private void onCancel() {
        if (running != null) {
            running.cancel(true);
        }
    }

    static String label(ImportedFile.SourceType t) {
        return switch (t) {
            case TEXT_FILE -> "Text file";
            case GENERATED -> "Generated";
            case AUTOCOMPLETE -> "Auto-complete";
        };
    }

    static String humanBytes(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024 * 1024) {
            return String.format("%.0f KB", b / 1024.0);
        }
        return String.format("%.1f MB", b / (1024.0 * 1024));
    }
}
