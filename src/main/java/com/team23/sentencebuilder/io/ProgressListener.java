package com.team23.sentencebuilder.io;

/**
 * How a long running import reports how far along it is.
 *
 * @author Ronak Chavva
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * Called while work proceeds. Implementations may be called from a
     * background thread, so anything that touches the UI must hop to the
     * JavaFX application thread first.
     *
     * @param fraction - how much of the work is done, from 0.0 to 1.0
     * @param message - a short human readable description of the current step
     */
    void onProgress(double fraction, String message);
}
