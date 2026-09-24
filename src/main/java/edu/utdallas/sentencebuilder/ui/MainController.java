package edu.utdallas.sentencebuilder.ui;

import edu.utdallas.sentencebuilder.App;
import edu.utdallas.sentencebuilder.AppContext;
import edu.utdallas.sentencebuilder.config.DbSettings;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.Map;

/** Menu bar and tab switching.  Each tab reloads its data when it is shown. */
public final class MainController {

    @FXML private TabPane tabs;
    @FXML private Tab importTab;
    @FXML private Tab generateTab;
    @FXML private Tab autoCompleteTab;
    @FXML private Tab wordsTab;
    @FXML private Tab reportsTab;

    // Nested controllers are injected as "<fx:id>Controller".
    @FXML private ImportController importViewController;
    @FXML private GenerateController generateViewController;
    @FXML private AutoCompleteController autoCompleteViewController;
    @FXML private WordsController wordsViewController;
    @FXML private ReportsController reportsViewController;

    @FXML
    private void initialize() {
        Map<Tab, Refreshable> refreshers = Map.of(
                importTab, importViewController,
                generateTab, generateViewController,
                autoCompleteTab, autoCompleteViewController,
                wordsTab, wordsViewController,
                reportsTab, reportsViewController);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> {
            Refreshable r = refreshers.get(tab);
            if (r != null) {
                r.refresh();
            }
        });
        importViewController.refresh();
    }

    @FXML
    private void onSettings() {
        DbSettings current = AppContext.get().db.settings();
        DbSettingsDialog.show(current).ifPresent(s -> {
            try {
                s.save();
                Dialogs.info("Settings saved", "Restart Sentence Builder to use " + s.describe() + ".");
            } catch (Exception e) {
                Dialogs.error(e);
            }
        });
    }

    @FXML
    private void onExit() {
        Platform.exit();
    }

    @FXML
    private void onAbout() {
        Dialogs.info(App.TITLE, """
                CS4485 Senior Design, Fall 2026.

                A bigram language model: every word in the imported text is stored with \
                how often it occurs, starts and ends a sentence, and which words follow it. \
                The generators walk that table to build new sentences, and the auto-complete \
                screen learns from what you type.

                Java 21 · JavaFX · MySQL""");
    }
}
