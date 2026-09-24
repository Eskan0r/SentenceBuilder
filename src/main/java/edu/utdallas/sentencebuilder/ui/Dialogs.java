package edu.utdallas.sentencebuilder.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/** Small helpers for the handful of dialogs the app needs. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        TextArea details = new TextArea(sw.toString());
        details.setEditable(false);
        details.setPrefRowCount(12);
        a.getDialogPane().setContent(new VBox(8, new Label(root.getMessage() == null ? "" : root.getMessage()), details));
        a.setResizable(true);
        a.showAndWait();
    }

    public static void error(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.setResizable(true);
        a.showAndWait();
    }

    public static void info(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.setResizable(true);
        a.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }
}
