package edu.utdallas.sentencebuilder.corpus;

import edu.utdallas.sentencebuilder.db.Database;
import edu.utdallas.sentencebuilder.model.GeneratedSentence;
import edu.utdallas.sentencebuilder.model.ImportedFile;
import edu.utdallas.sentencebuilder.text.Tokenizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads text into the database.  Two entry points: a file on disk, or a list
 * of sentences the program generated itself (the "feed it back" feature).
 * Both go through the same tokenizer and the same {@link CorpusWriter}, so the
 * program's own output is treated exactly like a book.
 */
public final class ImportService {

    private final CorpusWriter writer;

    public ImportService(Database db) {
        this.writer = new CorpusWriter(db);
    }

    /** Imports one text file.  Progress 0–0.6 is reading, 0.6–1.0 is writing to MySQL. */
    public ImportedFile importFile(Path file, ProgressListener progress) throws IOException, SQLException {
        long started = System.currentTimeMillis();
        long size = Files.size(file);
        CorpusCounts counts = new CorpusCounts();
        Tokenizer.SentenceBuilder sb = new Tokenizer.SentenceBuilder(counts::addSentence);

        progress.progress(0, "Reading " + file.getFileName() + "…");
        try (InputStream raw = Files.newInputStream(file);
             CountingInputStream in = new CountingInputStream(raw);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in,
                     StandardCharsets.UTF_8.newDecoder()
                             .onMalformedInput(CodingErrorAction.REPLACE)
                             .onUnmappableCharacter(CodingErrorAction.REPLACE)), 1 << 16)) {
            String line;
            long lines = 0;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Import cancelled");
                }
                sb.feedLine(line);
                if (++lines % 500 == 0 && size > 0) {
                    double frac = Math.min(1.0, (double) in.count() / size);
                    progress.progress(0.6 * frac, String.format("Reading… %,d words so far", counts.tokenCount()));
                }
            }
            sb.finish();
        }
        progress.progress(0.6, String.format("Read %,d words in %,d sentences", counts.tokenCount(), counts.sentenceCount()));
        return writer.merge(counts, file.getFileName().toString(), file.toAbsolutePath().toString(),
                ImportedFile.SourceType.TEXT_FILE, size, started, progress);
    }

    /** Feeds generated sentences back into the corpus as a pseudo-file called {@code generated-<timestamp>}. */
    public ImportedFile importGenerated(List<GeneratedSentence> sentences, ProgressListener progress) throws SQLException {
        long started = System.currentTimeMillis();
        CorpusCounts counts = new CorpusCounts();
        long bytes = 0;
        for (GeneratedSentence s : sentences) {
            counts.addSentence(s.words());
            bytes += s.text().length();
        }
        String name = "generated-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + " (" + sentences.size() + " sentences)";
        progress.progress(0.6, "Saving " + sentences.size() + " generated sentences…");
        return writer.merge(counts, name, null, ImportedFile.SourceType.GENERATED, bytes, started, progress);
    }

    /** Wraps an InputStream and counts bytes so we can report progress against the file size. */
    private static final class CountingInputStream extends InputStream {
        private final InputStream in;
        private long count;

        CountingInputStream(InputStream in) {
            this.in = in;
        }

        long count() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = in.read(buf, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
