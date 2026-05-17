package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;

import java.io.InputStream;

@Component
@Order(200)
public class PdfTextParser extends AbstractDocumentParser {

    private final FileTypeDetector types;

    public PdfTextParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        String ext = types.getFileExtension(in.logicalName());
        String ct = in.contentType() != null ? in.contentType() : "";
        return "pdf".equals(ext) || "application/pdf".equals(ct);
    }

    @Override
    protected String extractionType() {
        return "pdf";
    }

    @Override
    protected String extractText(InputStream in) throws Exception {
        try (var doc = Loader.loadPDF(new RandomAccessReadBuffer(in))) {
            var stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                return "(PDF contains no extractable text — possibly a scanned image)";
            }
            return text;
        }
    }
}
