package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;

import java.io.InputStream;

/**
 * Plain-text DOCX extraction via POI. Fallback for {@link DocxPandocParser}, which runs first:
 * when pandoc already emitted the markdown text for this input, this parser skips itself so the
 * same document is not returned twice and does not consume the attachment text budget twice.
 */
@Component
@Order(300)
public class DocxTextParser extends AbstractDocumentParser {

    private final FileTypeDetector types;

    public DocxTextParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        String ext = types.getFileExtension(in.logicalName());
        String ct = in.contentType() != null ? in.contentType() : "";
        return "docx".equals(ext) || ct.contains("wordprocessingml");
    }

    @Override
    public void parse(ParseInput input, ParseSink sink) {
        if (sink.hasTextPart()) {
            return;
        }
        super.parse(input, sink);
    }

    @Override
    protected String extractionType() {
        return "docx";
    }

    @Override
    protected String extractText(InputStream in) throws Exception {
        try (var doc = new XWPFDocument(in)) {
            var sb = new StringBuilder();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    String text = para.getText();
                    if (text != null && !text.isBlank()) {
                        sb.append(text).append("\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    sb.append("\n[Table]\n");
                    for (var row : table.getRows()) {
                        var cells = row.getTableCells().stream()
                                .map(c -> c.getText().strip())
                                .toList();
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                    sb.append("\n");
                }
            }
            if (sb.isEmpty()) return "(Word document contains no text)";
            return sb.toString();
        }
    }
}
