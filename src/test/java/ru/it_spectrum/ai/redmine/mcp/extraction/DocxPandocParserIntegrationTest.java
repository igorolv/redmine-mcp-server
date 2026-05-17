package ru.it_spectrum.ai.redmine.mcp.extraction;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.BinaryFallbackParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.DocxPandocParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.ImagePassthroughParser;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that {@link DocxPandocParser} produces a markdown Part when pandoc is on PATH.
 * Skipped automatically when pandoc is unavailable, so CI environments without pandoc just
 * silently pass.
 */
@Tag("integration")
@EnabledIf("isPandocOnPath")
class DocxPandocParserIntegrationTest {

    @TempDir
    Path tmp;

    @Test
    void shouldEmitMarkdownPartForDocx() throws Exception {
        Path docx = writeDocx(tmp.resolve("report.docx"), "Hello from pandoc test.");
        var types = new FileTypeDetector();
        var pandoc = new PandocAvailability(
                new RedmineMcpProperties(tmp.toString()),
                new ExtractionProperties(new ExtractionProperties.Pandoc(true))
        );
        assertThat(pandoc.isAvailable())
                .as("pandoc detection failed despite @EnabledIf check")
                .isTrue();

        var pipeline = new ExtractionPipeline(List.of(
                new DocxPandocParser(types, pandoc),
                new ImagePassthroughParser(types),
                new BinaryFallbackParser(types)
        ));
        Path workDir = Files.createDirectories(tmp.resolve("work"));
        List<ExtractedPart> parts = pipeline.extract(docx, "report.docx", null, workDir);

        var mdPart = parts.stream()
                .filter(p -> "docx-markdown".equals(p.extractionType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DocxPandocParser did not emit a docx-markdown part"));

        assertThat(mdPart.content()).contains("Hello from pandoc test");
        assertThat(mdPart.localPath()).isNotNull();
        assertThat(Path.of(mdPart.localPath())).exists();
        assertThat(mdPart.producer()).isEqualTo("DocxPandocParser");
    }

    @SuppressWarnings("unused") // referenced by @EnabledIf
    static boolean isPandocOnPath() {
        try {
            var process = new ProcessBuilder("pandoc", "--version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path writeDocx(Path target, String text) throws Exception {
        try (var doc = new XWPFDocument(); OutputStream out = Files.newOutputStream(target)) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
        }
        return target;
    }
}
