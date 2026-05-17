package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.PandocAvailability;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Converts a DOCX to GitHub-Flavoured Markdown via the {@code pandoc} executable and emits the
 * markdown as a {@code docx-markdown} Part. Embedded media is written to
 * {@code <workDir>/docx/<hash>/media/} via {@code --extract-media} so the markdown's image
 * references stay valid on disk; the media files themselves are exposed as Parts by
 * {@link DocxMediaExtractor} (which runs unconditionally), not by this parser.
 *
 * <p>Runs alongside {@link DocxTextParser} — both parsers emit independent Parts for the same
 * DOCX (POI plain text + pandoc markdown). Output is cached at
 * {@code <workDir>/docx/<hash>/pandoc.md}; subsequent calls reuse it without re-running pandoc.</p>
 */
@Component
@Order(310)
public class DocxPandocParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocxPandocParser.class);
    private static final int PANDOC_TIMEOUT_SECONDS = 30;

    private final FileTypeDetector types;
    private final PandocAvailability pandoc;

    public DocxPandocParser(FileTypeDetector types, PandocAvailability pandoc) {
        this.types = types;
        this.pandoc = pandoc;
    }

    @Override
    public boolean applies(ParseInput in) {
        if (!pandoc.isAvailable()) return false;
        String ext = types.getFileExtension(in.logicalName());
        String ct = in.contentType() != null ? in.contentType() : "";
        return "docx".equals(ext) || ct.contains("wordprocessingml");
    }

    @Override
    public void parse(ParseInput in, ParseSink sink) {
        Path workSubdir = in.workDir().resolve("docx").resolve(hashSegment(in.logicalName()));
        Path mediaDir = workSubdir.resolve("media");
        Path mdFile = workSubdir.resolve("pandoc.md");

        try {
            if (!Files.exists(mdFile)) {
                Files.createDirectories(workSubdir);
                runPandoc(in.file(), mdFile, mediaDir);
            }
            emitMarkdownPart(in, mdFile, sink);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Pandoc extraction failed for {}: {}", in.logicalName(), e.getMessage());
            sink.emit(new ExtractedPart(
                    in.emitName(),
                    null,
                    "docx-markdown",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "pandoc extraction failed: " + e.getMessage()
            ));
        }
    }

    private void emitMarkdownPart(ParseInput in, Path mdFile, ParseSink sink) throws IOException {
        String markdown = Files.readString(mdFile, StandardCharsets.UTF_8);
        sink.emit(new ExtractedPart(
                in.emitName(),
                null,
                "docx-markdown",
                null,
                Files.size(mdFile),
                markdown,
                mdFile.toString(),
                mdFile.toUri().toString(),
                null
        ));
    }

    private void runPandoc(Path docx, Path mdOut, Path mediaDir) throws IOException, InterruptedException {
        var executable = pandoc.executable().orElseThrow(() -> new IOException("pandoc executable unavailable"));
        var cmd = List.of(
                executable.toString(),
                "-f", "docx",
                "-t", "gfm",
                "--extract-media=" + mediaDir.toString(),
                docx.toString(),
                "-o", mdOut.toString()
        );
        var process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        boolean finished = process.waitFor(PANDOC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("pandoc timed out after " + PANDOC_TIMEOUT_SECONDS + "s");
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("pandoc exited " + process.exitValue() + ": " + output.strip());
        }
    }

    private static String hashSegment(String logicalName) {
        if (logicalName == null) return "root";
        return Integer.toHexString(logicalName.hashCode());
    }
}
