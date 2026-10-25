package com.team23.sentencebuilder.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.DoubleConsumer;

/**
 * A parallel sentence-based {@link TextImporter} that tokenizes the whole text up front on the
 * calling thread with injected {@link Tokenizer}, which then splits sentences into chunks
 * that are counted concurrently on injected {@link ExecutorService}
 * <p>
 *
 * Each worker counts its chunk into a {@link CorpusCounts}, so no counting state shares between
 * threads. From there, calling thread merges chunk result into final counts upon completion.
 * Since counts never depends on neighboring sentences and {@link CorpusCounts#merge(CorpusCounts)}
 * is order independent, result is equal to what {@link DefaultTextImporter} produces for same text.
 * <p>
 *
 * Progress will only report to the calling thread and advances merge chunks as necessary. As tokenization
 * happens, the progress bar is at 0.
 *
 * @author Alen Jo
 */
public class ParallelTextImporter implements TextImporter {
    private static final int TASKS_PER_THREAD = 4;
    private static final int MIN_CHUNK_SIZE = 500;

    private final Tokenizer tokenizer;
    private final ExecutorService executorService;
    private final int parallelism;

    private record ChunkResult(CorpusCounts counts, int sentenceCount) {}

    /**
     * Creates an importer that tokenizes with the given tokenizer and counts on the given executor.
     *
     * @param tokenizer - tokenizer used to split text into sentences
     * @param executor - executor that runs the chunk-counting tasks
     * @param parallelism - number of worker threads the executor is expected to use
     *
     * @throws NullPointerException if tokenizer or executor is null
     * @throws IllegalArgumentException if parallelism is less than 1
     */
    public ParallelTextImporter(Tokenizer tokenizer, ExecutorService executor, int parallelism)
    {
        this.tokenizer = Objects.requireNonNull(tokenizer);
        this.executorService = Objects.requireNonNull(executor);

        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be greater than or equal to one!");

        this.parallelism = parallelism;
    }

    /**
     * Tokenizes the text, counts the sentences in parallel chunks, and merges the results.
     * <p>
     * Blocks the calling thread until every chunk is counted and merged. Progress values are
     * reported on the calling thread, are monotonically increasing, and always end with exactly
     * {@link #PROGRESS_COMPLETE}. Empty or punctuation-only text yields empty counts and
     * reports only 1.0.
     * <p>
     * If this method fails or is interrupted, all unfinished chunks are cancelled so that this
     * is atomic: either we finish ALL or nothing.
     *
     * @param text - raw text to import
     * @param progress - progress values as import advances
     *
     * @return {@link CorpusCounts} object containing all counts of text from raw text
     *
     * @throws NullPointerException if text or progress is null
     * @throws CancellationException if the calling thread is interrupted while waiting
     * @throws IllegalStateException if counting a chunk fails; the original failure is the cause
     */
    @Override
    public CorpusCounts importText(String text, DoubleConsumer progress) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(progress, "progress must not be null");

        List<List<String>> sentences = tokenizer.tokenize(text);
        int total = sentences.size();
        CorpusCounts result = new CorpusCounts();

        if (total == 0) {
            progress.accept(PROGRESS_COMPLETE);
            return result;
        }

        int targetChunks = parallelism * TASKS_PER_THREAD;
        int chunkSize = Math.max(
                MIN_CHUNK_SIZE,
                (total + targetChunks - 1) / targetChunks
        );

        CompletionService<ChunkResult> completion = new ExecutorCompletionService<>(executorService);
        List<Future<ChunkResult>> futures = new ArrayList<>();

        for (int start = 0; start < total; start += chunkSize) {
            List<List<String>> chunk = sentences.subList(
                    start,
                    Math.min(start + chunkSize, total)
            );

            futures.add(completion.submit(() -> countChunk(chunk)));
        }

        try {
            int done = 0;
            for (int ixProgress = 0; ixProgress < futures.size(); ixProgress++) {
                ChunkResult chunkResult = completion.take().get();
                result.merge(chunkResult.counts());

                done += chunkResult.sentenceCount();

                if (done < total) {
                    progress.accept((double) done / total);
                }
            }
        } catch (InterruptedException ie) {
            cancelAll(futures);
            Thread.currentThread().interrupt();
            throw new CancellationException("Import interrupted!");
        } catch (ExecutionException ee) {
            cancelAll(futures);
            throw new IllegalStateException("Imported failed", ee.getCause());
        }

        progress.accept(PROGRESS_COMPLETE);
        return result;
    }

    private static ChunkResult countChunk(List<List<String>> chunk) {
        CorpusCounts counts = new CorpusCounts();

        for (List<String> sentence : chunk) {
            counts.addSentence(sentence);
        }

        return new ChunkResult(counts, chunk.size());
    }

    private static void cancelAll(List<Future<ChunkResult>> futures) {
        futures.forEach(f -> f.cancel(true));
    }
}
