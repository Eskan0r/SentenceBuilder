package com.team23.sentencebuilder;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Entry point of the application. Loads the tabbed main window; every tab
 * lives in its own FXML file under {@code resources/com/team23/sentencebuilder/ui}.
 *
 * @author Ronak Chavva
 */
public class SentenceBuilderApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(SentenceBuilderApp.class.getResource("ui/main-view.fxml"));
        Scene scene = new Scene(loader.load());

        stage.setTitle("Sentence Builder");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
