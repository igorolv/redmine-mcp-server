package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;

import java.io.InputStream;
import java.util.ArrayList;

@Component
public class PptxTextParser extends AbstractDocumentParser {

    private final FileTypeDetector types;

    public PptxTextParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        String ext = types.getFileExtension(in.logicalName());
        String ct = in.contentType() != null ? in.contentType() : "";
        return "pptx".equals(ext) || ct.contains("presentationml");
    }

    @Override
    protected String extractionType() {
        return "pptx";
    }

    @Override
    protected String extractText(InputStream in) throws Exception {
        try (var pptx = new XMLSlideShow(in)) {
            var sb = new StringBuilder();
            int slideNum = 0;
            for (XSLFSlide slide : pptx.getSlides()) {
                slideNum++;
                var texts = new ArrayList<String>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            texts.add(text.strip());
                        }
                    }
                    if (shape instanceof XSLFTable table) {
                        for (int r = 0; r < table.getNumberOfRows(); r++) {
                            var cells = new ArrayList<String>();
                            for (int c = 0; c < table.getNumberOfColumns(); c++) {
                                cells.add(table.getCell(r, c).getText().strip());
                            }
                            texts.add(String.join(" | ", cells));
                        }
                    }
                }
                if (!texts.isEmpty()) {
                    sb.append("--- Slide %d ---\n".formatted(slideNum));
                    sb.append(String.join("\n", texts)).append("\n\n");
                }
            }
            if (sb.isEmpty()) return "(Presentation contains no text)";
            return sb.toString();
        }
    }
}
