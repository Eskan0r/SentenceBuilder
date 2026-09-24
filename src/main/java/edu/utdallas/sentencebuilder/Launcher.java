package edu.utdallas.sentencebuilder;

/**
 * Entry point for the shaded jar.  JavaFX refuses to start when the main class
 * itself extends {@code Application} and the platform is on the classpath
 * instead of the module path, so this thin wrapper exists purely to make
 * {@code java -jar sentence-builder.jar} work.  IntelliJ and
 * {@code mvn javafx:run} use {@link App} directly.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
