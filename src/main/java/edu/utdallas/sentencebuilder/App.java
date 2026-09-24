package edu.utdallas.sentencebuilder;

import edu.utdallas.sentencebuilder.config.DbSettings;
import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.ui.DbSettingsDialog;
import edu.utdallas.sentencebuilder.ui.Dialogs;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.Optional;

/**
 * JavaFX application entry point.
 * <p>
 * Start-up connects to the configured database.  On a first run with no saved
 * settings and no reachable MySQL server it offers the built-in database, so
 * the program can be tried without installing anything.
 */
public final class App extends Application {

    public static final String TITLE = "Sentence Builder";

    @Override
    public void start(Stage stage) throws Exception {
        Database db = connectOrAsk();
        if (db == null) {
            Platform.exit();
            return;
        }
        AppContext.init(db);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setTitle(TITLE + "  —  " + db.settings().describe());
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        stage.setOnHidden(e -> AppContext.get().close());
        stage.show();
    }

    /** Keeps trying to connect until it works or the user gives up. */
    private static Database connectOrAsk() {
        DbSettings settings = DbSettings.load();
        boolean firstRun = !DbSettings.userFileExists();
        while (true) {
            try {
                return new Database(settings);
            } catch (SQLException e) {
                Optional<DbSettings> next = firstRun && !settings.isEmbedded()
                        ? offerEmbedded(settings, e)
                        : askForSettings(settings, e);
                if (next.isEmpty()) {
                    return null;
                }
                settings = next.get();
                firstRun = false;
                try {
                    settings.save();
                } catch (Exception saveErr) {
                    Dialogs.error("Could not save settings", saveErr.getMessage());
                }
            }
        }
    }

    /** First run and MySQL is not reachable: the built-in database is the easy way out. */
    private static Optional<DbSettings> offerEmbedded(DbSettings current, SQLException e) {
        ButtonType builtIn = new ButtonType("Use built-in database");
        ButtonType mysql = new ButtonType("MySQL settings…");
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "No MySQL server answered at " + current.describe() + ".\n\n"
                + "You can use the built-in database instead: it needs no setup and is stored in "
                + DbSettings.USER_DIR + ". You can switch to MySQL later from Database > Settings.\n\n"
                + "(" + rootMessage(e) + ")",
                builtIn, mysql, ButtonType.CANCEL);
        a.setTitle("Choose a database");
        a.setHeaderText("Welcome to " + TITLE);
        a.setResizable(true);
        Optional<ButtonType> choice = a.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
            return Optional.empty();
        }
        return choice.get() == builtIn ? Optional.of(DbSettings.embedded()) : DbSettingsDialog.show(current);
    }

    private static Optional<DbSettings> askForSettings(DbSettings current, SQLException e) {
        Dialogs.error("Could not open the database",
                "Check that the server is running and the settings are right.\n\n" + rootMessage(e));
        return DbSettingsDialog.show(current);
    }

    static String rootMessage(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
