package edu.utdallas.sentencebuilder.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs database work off the JavaFX thread and delivers the result back on it.
 * A separate single-thread executor keeps auto-complete writes in the order
 * the user typed them.
 */
public final class Async {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, Async::daemon);
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(Async::daemon);

    private Async() {
    }

    private static Thread daemon(Runnable r) {
        Thread t = new Thread(r, "sb-worker");
        t.setDaemon(true);
        return t;
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, Dialogs::error);
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        submit(POOL, work, onSuccess, onError);
    }

    /** Like {@link #run} but tasks execute one after another in submission order. */
    public static <T> void serial(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        submit(SERIAL, work, onSuccess, onError);
    }

    private static <T> void submit(ExecutorService ex, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        ex.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                Platform.runLater(() -> onError.accept(t));
            }
        });
    }
}
