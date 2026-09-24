package edu.utdallas.sentencebuilder.corpus;

/** Callback for long-running work so the UI can show a progress bar. */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (fraction, message) -> { };

    /**
     * @param fraction 0.0 to 1.0, or -1 for "busy, unknown progress"
     * @param message  what is happening right now
     */
    void progress(double fraction, String message);
}
