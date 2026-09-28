package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.it_spectrum.ai.redmine.mcp.TestRedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractionPipeline;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocxTextParserTest {

    @TempDir
    Path tmp;

    private final FileTypeDetector types = new FileTypeDetector();

    @Test
    void emitsPlainTextWhenNoEarlierParserProducedText() throws Exception {
        var parts = extract(List.of(new DocxTextParser(types)));

        var textParts = textParts(parts);
        assertThat(textParts).hasSize(1);
        assertThat(textParts.get(0).extractionType()).isEqualTo("docx");
        assertThat(textParts.get(0).content()).contains("Requirements body");
    }

    @Test
    void skipsDocxAlreadyExtractedAsMarkdown() throws Exception {
        var parts = extract(List.of(new FakeMarkdownParser(), new DocxTextParser(types)));

        var textParts = textParts(parts);
        assertThat(textParts).hasSize(1);
        assertThat(textParts.get(0).extractionType()).isEqualTo("docx-markdown");
    }

    private List<ExtractedPart> extract(List<DocumentParser> parsers) throws Exception {
        Path docx = tmp.resolve("spec.docx");
        try (var doc = new XWPFDocument(); OutputStream out = Files.newOutputStream(docx)) {
            doc.createParagraph().createRun().setText("Requirements body");
            doc.write(out);
        }
        var pipeline = new ExtractionPipeline(parsers, TestRedmineMcpProperties.withDataDir(tmp));
        return pipeline.extract(docx, "spec.docx", null, Files.createDirectories(tmp.resolve("work")));
    }

    private static List<ExtractedPart> textParts(List<ExtractedPart> parts) {
        return parts.stream().filter(ExtractedPart::textExtracted).toList();
    }

    /** Stands in for {@link DocxPandocParser}, which needs a pandoc binary. */
    private static final class FakeMarkdownParser implements DocumentParser {

        @Override
        public boolean applies(ParseInput in) {
            return in.logicalName().endsWith(".docx");
        }

        @Override
        public void parse(ParseInput in, ParseSink sink) {
            sink.emit(new ExtractedPart(in.emitName(), null, "docx-markdown", null,
                    null, "# Requirements body", null, null, null));
        }
    }
}
