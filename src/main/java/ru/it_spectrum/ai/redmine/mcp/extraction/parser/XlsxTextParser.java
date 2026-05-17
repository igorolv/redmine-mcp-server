package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;

import java.io.InputStream;
import java.util.ArrayList;

@Component
@Order(400)
public class XlsxTextParser extends AbstractDocumentParser {

    private final FileTypeDetector types;

    public XlsxTextParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        String ext = types.getFileExtension(in.logicalName());
        String ct = in.contentType() != null ? in.contentType() : "";
        return "xlsx".equals(ext) || ct.contains("spreadsheetml");
    }

    @Override
    protected String extractionType() {
        return "xlsx";
    }

    @Override
    protected String extractText(InputStream in) throws Exception {
        try (var wb = new XSSFWorkbook(in)) {
            var sb = new StringBuilder();
            var formatter = new DataFormatter();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                var sheet = wb.getSheetAt(i);
                sb.append("--- Sheet: %s ---\n".formatted(sheet.getSheetName()));
                for (var row : sheet) {
                    var cells = new ArrayList<String>();
                    for (var cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    if (cells.stream().anyMatch(c -> !c.isEmpty())) {
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                }
                sb.append("\n");
            }
            if (sb.isEmpty()) return "(Excel file contains no data)";
            return sb.toString();
        }
    }
}
