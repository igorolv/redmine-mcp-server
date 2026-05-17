package ru.it_spectrum.ai.redmine.mcp.extraction;

import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.BinaryFallbackParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.DocxMediaExtractor;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.DocxPandocParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.DocxTextParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.ImagePassthroughParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.PdfTextParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.PlainTextParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.PptxTextParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.XlsxTextParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.parser.ZipParser;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueSnapshotService;

import java.util.List;

/** Wires {@link ExtractionPipeline} with the production parser set for tests. */
public final class ExtractionTestPipelines {

    private ExtractionTestPipelines() {
    }

    public static FileTypeDetector defaultFileTypeDetector() {
        return new FileTypeDetector();
    }

    public static ExtractionPipeline defaultPipeline(FileTypeDetector types) {
        return new ExtractionPipeline(List.of(
                new PlainTextParser(types),
                new PdfTextParser(types),
                new DocxTextParser(types),
                new DocxPandocParser(types, PandocAvailability.disabled()),
                new DocxMediaExtractor(types),
                new XlsxTextParser(types),
                new PptxTextParser(types),
                new ZipParser(types),
                new ImagePassthroughParser(types),
                new BinaryFallbackParser(types)
        ));
    }

    public static AttachmentService newAttachmentService(RedmineClient client, IssueSnapshotService snapshot) {
        var types = defaultFileTypeDetector();
        var pipeline = defaultPipeline(types);
        return new AttachmentService(client, pipeline, types, snapshot);
    }
}
