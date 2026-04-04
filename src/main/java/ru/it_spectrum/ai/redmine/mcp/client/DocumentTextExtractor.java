package ru.it_spectrum.ai.redmine.mcp.client;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;

@Component
public class DocumentTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "pptx");

    private final RedmineClient client;
    private final AttachmentTextCache textCache;

    public DocumentTextExtractor(RedmineClient client, AttachmentTextCache textCache) {
        this.client = client;
        this.textCache = textCache;
    }

    /**
     * Extract text from an attachment. Returns null if not extractable or on failure.
     * Uses cache to avoid re-downloading/re-extracting.
     */
    public String extractText(RedmineAttachment attachment) {
        String cached = textCache.get(attachment.id());
        if (cached != null) {
            return cached;
        }

        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if (!isDocument(ext, contentType) && !isPlainText(contentType, attachment.filename())) {
            return null;
        }

        byte[] content = client.downloadAttachment(attachment.contentUrl());
        if (content == null) {
            return null;
        }

        String text;
        if (isDocument(ext, contentType)) {
            text = extractDocumentText(ext, contentType, content);
        } else {
            text = new String(content, StandardCharsets.UTF_8);
        }

        text = normalizeExtractedText(text);
        textCache.put(attachment.id(), text);
        return text;
    }

    /**
     * Extract text from raw bytes given filename and content type.
     */
    public String extractFromBytes(String filename, String contentType, byte[] data) {
        String ext = getFileExtension(filename);
        String ct = contentType != null ? contentType : "";

        if (isDocument(ext, ct)) {
            return normalizeExtractedText(extractDocumentText(ext, ct, data));
        } else if (isPlainText(ct, filename)) {
            return normalizeExtractedText(new String(data, StandardCharsets.UTF_8));
        }
        return null;
    }

    public boolean isTextExtractable(String filename, String contentType) {
        String ext = getFileExtension(filename);
        String ct = contentType != null ? contentType : "";
        return isDocument(ext, ct) || isPlainText(ct, filename);
    }

    public boolean isDocument(String ext, String contentType) {
        if (DOCUMENT_EXTENSIONS.contains(ext)) return true;
        return contentType.equals("application/pdf")
                || contentType.contains("wordprocessingml")
                || contentType.contains("spreadsheetml")
                || contentType.contains("presentationml");
    }

    public boolean isPlainText(String contentType, String filename) {
        if (contentType != null) {
            if (contentType.startsWith("text/")) return true;
            if (contentType.contains("json") || contentType.contains("xml") || contentType.contains("csv"))
                return true;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv")
                    || lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".yml")
                    || lower.endsWith(".yaml") || lower.endsWith(".sql") || lower.endsWith(".md")
                    || lower.endsWith(".html") || lower.endsWith(".properties") || lower.endsWith(".conf");
        }
        return false;
    }

    public String getFileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    public String detectExtractionType(RedmineAttachment attachment) {
        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if ("pdf".equals(ext) || contentType.equals("application/pdf")) return "pdf";
        if ("docx".equals(ext) || contentType.contains("wordprocessingml")) return "docx";
        if ("xlsx".equals(ext) || contentType.contains("spreadsheetml")) return "xlsx";
        if ("pptx".equals(ext) || contentType.contains("presentationml")) return "pptx";
        return "text";
    }

    // --- Internal extraction ---

    private String extractDocumentText(String ext, String contentType, byte[] data) {
        try {
            if ("pdf".equals(ext) || contentType.equals("application/pdf")) {
                return extractPdfText(data);
            } else if ("docx".equals(ext) || contentType.contains("wordprocessingml")) {
                return extractDocxText(data);
            } else if ("xlsx".equals(ext) || contentType.contains("spreadsheetml")) {
                return extractXlsxText(data);
            } else if ("pptx".equals(ext) || contentType.contains("presentationml")) {
                return extractPptxText(data);
            }
            return "(unsupported document format)";
        } catch (Exception e) {
            log.warn("Failed to extract text from document: {}", e.getMessage());
            return "(failed to extract text: %s)".formatted(e.getMessage());
        }
    }

    private String extractPdfText(byte[] data) throws Exception {
        try (var doc = Loader.loadPDF(data)) {
            var stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                return "(PDF contains no extractable text — possibly a scanned image)";
            }
            return text;
        }
    }

    private String extractDocxText(byte[] data) throws Exception {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(data))) {
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

    private String extractXlsxText(byte[] data) throws Exception {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sb = new StringBuilder();
            var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
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

    private String extractPptxText(byte[] data) throws Exception {
        try (var pptx = new XMLSlideShow(new ByteArrayInputStream(data))) {
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

    private String normalizeExtractedText(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\t\\x0B\\f]+", " ");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.strip();
    }
}
