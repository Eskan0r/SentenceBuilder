module com.team23.sentencebuilder {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.team23.sentencebuilder to javafx.fxml;
    exports com.team23.sentencebuilder;
}