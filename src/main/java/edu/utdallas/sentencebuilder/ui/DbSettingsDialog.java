package edu.utdallas.sentencebuilder.ui;

import edu.utdallas.sentencebuilder.config.DbSettings;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/** Lets the user choose the built-in database or enter MySQL connection details. */
public final class DbSettingsDialog {

    private DbSettingsDialog() {
    }

    public static Optional<DbSettings> show(DbSettings current) {
        Dialog<DbSettings> dialog = new Dialog<>();
        dialog.setTitle("Database settings");
        dialog.setHeaderText("Where should Sentence Builder keep its data?");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ToggleGroup kind = new ToggleGroup();
        RadioButton embedded = new RadioButton("Built-in database (no server needed, stored in " + DbSettings.USER_DIR + ")");
        RadioButton mysql = new RadioButton("MySQL / MariaDB server");
        embedded.setToggleGroup(kind);
        mysql.setToggleGroup(kind);

        DbSettings mysqlDefaults = current.isEmbedded()
                ? new DbSettings("jdbc:mysql://localhost:3306/sentence_builder?createDatabaseIfNotExist=true", "root", "")
                : current;
        TextField url = new TextField(mysqlDefaults.url());
        url.setPrefColumnCount(48);
        TextField user = new TextField(mysqlDefaults.user());
        PasswordField password = new PasswordField();
        password.setText(mysqlDefaults.password());
        url.disableProperty().bind(embedded.selectedProperty());
        user.disableProperty().bind(embedded.selectedProperty());
        password.disableProperty().bind(embedded.selectedProperty());
        (current.isEmbedded() ? embedded : mysql).setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(embedded, 0, 0, 2, 1);
        grid.add(mysql, 0, 1, 2, 1);
        grid.addRow(2, new Label("JDBC URL"), url);
        grid.addRow(3, new Label("User"), user);
        grid.addRow(4, new Label("Password"), password);
        Label hint = new Label("Saved to " + DbSettings.USER_FILE + ". Takes effect after a restart.");
        hint.getStyleClass().add("hint");
        grid.add(hint, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            return embedded.isSelected()
                    ? DbSettings.embedded()
                    : new DbSettings(url.getText().trim(), user.getText().trim(), password.getText());
        });
        return dialog.showAndWait();
    }
}
