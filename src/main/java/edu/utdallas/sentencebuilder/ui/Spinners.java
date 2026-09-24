package edu.utdallas.sentencebuilder.ui;

import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

/** Makes an integer Spinner accept typed values (JavaFX only commits them on Enter by default). */
public final class Spinners {

    private Spinners() {
    }

    public static void integer(Spinner<Integer> spinner, int min, int max, int initial) {
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial));
        spinner.setEditable(true);
        spinner.focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow) {
                commit(spinner);
            }
        });
    }

    public static int value(Spinner<Integer> spinner) {
        commit(spinner);
        return spinner.getValue();
    }

    private static void commit(Spinner<Integer> spinner) {
        SpinnerValueFactory<Integer> vf = spinner.getValueFactory();
        try {
            Integer typed = vf.getConverter().fromString(spinner.getEditor().getText());
            if (typed != null) {
                vf.setValue(typed);
            }
        } catch (RuntimeException badInput) {
            spinner.getEditor().setText(vf.getConverter().toString(vf.getValue()));
        }
    }
}
