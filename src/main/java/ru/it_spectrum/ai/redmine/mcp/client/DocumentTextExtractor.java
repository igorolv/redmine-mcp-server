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
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocumentTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "pptx");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip");
    private static final int MAX_ARCHIVE_DEPTH = 1;
    private static final int MAX_ARCHIVE_ENTRIES = 100;
    private static final int MAX_ARCHIVE_ENTRY_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ARCHIVE_TOTAL_BYTES = 50 * 1024 * 1024;

    /**
     * Extract text from an already downloaded attachment file.
     */
    public String extractText(RedmineAttachment attachment, Path localFile) {
        var parts = extractTextParts(attachment, localFile).stream()
                .filter(ExtractedTextPart::textExtracted)
                .toList();
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1 && parts.getFirst().name() == null) {
            return parts.getFirst().content();
        }

        var text = new StringBuilder();
        for (var part : parts) {
            text.append("\n--- %s ---\n".formatted(part.name()));
            text.append(part.content()).append("\n");
        }
        return normalizeExtractedText(text.toString());
    }

    /**
     * Extract text parts from an already downloaded attachment file.
     * ZIP archives are represented as separate parts per archive entry.
     */
    public List<ExtractedTextPart> extractTextParts(RedmineAttachment attachment, Path localFile) {
        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if (!isDocument(ext, contentType)
                && !isArchive(ext, contentType)
                && !isPlainText(contentType, attachment.filename())) {
            return List.of();
        }

        try {
            byte[] data = Files.readAllBytes(localFile);
            if (isArchive(ext, contentType)) {
                return extractZipTextParts(attachment.filename(), data, 0);
            }

            String text = extractFromBytes(attachment.filename(), contentType, data);
            if (text == null) {
                return List.of();
            }
            return List.of(new ExtractedTextPart(
                    null,
                    detectExtractionType(attachment.filename(), contentType),
                    (long) data.length,
                    text,
                    null
            ));
        } catch (IOException e) {
            log.warn("Failed to read materialized attachment #{} ({}): {}",
                    attachment.id(), localFile, e.getMessage());
            return List.of();
        }
    }

    /**
     * Extract text from raw bytes given filename and content type.
     */
    public String extractFromBytes(String filename, String contentType, byte[] data) {
        return extractFromBytes(filename, contentType, data, 0);
    }

    private String extractFromBytes(String filename, String contentType, byte[] data, int archiveDepth) {
        String ext = getFileExtension(filename);
        String ct = contentType != null ? contentType : "";

        if (isDocument(ext, ct)) {
            return normalizeExtractedText(extractDocumentText(ext, ct, data));
        } else if (isArchive(ext, ct)) {
            if (archiveDepth >= MAX_ARCHIVE_DEPTH) {
                return null;
            }
            return normalizeExtractedText(extractZipText(filename, data, archiveDepth));
        } else if (isPlainText(ct, filename)) {
            return normalizeExtractedText(new String(data, StandardCharsets.UTF_8));
        }
        return null;
    }

    public boolean isTextExtractable(String filename, String contentType) {
        String ext = getFileExtension(filename);
        String ct = contentType != null ? contentType : "";
        return isDocument(ext, ct) || isArchive(ext, ct) || isPlainText(ct, filename);
    }

    public boolean isDocument(String ext, String contentType) {
        if (DOCUMENT_EXTENSIONS.contains(ext)) return true;
        return contentType.equals("application/pdf")
                || contentType.contains("wordprocessingml")
                || contentType.contains("spreadsheetml")
                || contentType.contains("presentationml");
    }

    public boolean isArchive(String ext, String contentType) {
        if (ARCHIVE_EXTENSIONS.contains(ext)) return true;
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.equals("application/zip")
                || lower.equals("application/x-zip-compressed")
                || lower.equals("application/zip-compressed");
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
                    || lower.endsWith(".yaml") || lower.endsWith(".xsd") || lower.endsWith(".sql") || lower.endsWith(".md")
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
        return detectExtractionType(attachment.filename(), attachment.contentType());
    }

    public String detectExtractionType(String filename, String contentType) {
        String ext = getFileExtension(filename);
        String ct = contentType != null ? contentType : "";

        if ("pdf".equals(ext) || ct.equals("application/pdf")) return "pdf";
        if ("docx".equals(ext) || ct.contains("wordprocessingml")) return "docx";
        if ("xlsx".equals(ext) || ct.contains("spreadsheetml")) return "xlsx";
        if ("pptx".equals(ext) || ct.contains("presentationml")) return "pptx";
        if ("zip".equals(ext) || isArchive(ext, ct)) return "zip";
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

    private String extractZipText(String filename, byte[] data, int archiveDepth) {
        var parts = extractZipTextParts(filename, data, archiveDepth);
        if (parts.size() == 1 && !parts.getFirst().textExtracted() && parts.getFirst().note() != null) {
            return parts.getFirst().note();
        }

        var manifest = new StringBuilder();
        var extractedText = new StringBuilder();
        int entries = parts.size();
        long extracted = parts.stream().filter(ExtractedTextPart::textExtracted).count();
        long skipped = entries - extracted;

        manifest.append("ZIP archive: %s\n".formatted(filename));
        manifest.append("--- ZIP entries ---\n");
        for (var part : parts) {
            if (part.textExtracted()) {
                manifest.append("- %s (extracted, %s)\n".formatted(part.name(), formatBytes(part.size())));
                extractedText.append("\n--- %s ---\n".formatted(part.name()));
                extractedText.append(part.content()).append("\n");
            } else {
                manifest.append("- %s (%s)\n".formatted(part.name(), part.note()));
            }
        }
        if (entries == 0) {
            manifest.append("- (archive contains no files)\n");
        }

        manifest.append("\nEntries scanned: %d, extracted: %d, skipped: %d\n"
                .formatted(entries, extracted, skipped));
        manifest.append(extractedText);
        return manifest.toString();
    }

    private List<ExtractedTextPart> extractZipTextParts(String filename, byte[] data, int archiveDepth) {
        var parts = new ArrayList<ExtractedTextPart>();
        int entries = 0;
        long[] totalBytes = {0};

        try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    parts.add(new ExtractedTextPart(
                            "...",
                            "zip-entry",
                            null,
                            null,
                            "skipped remaining entries (limit: %d)".formatted(MAX_ARCHIVE_ENTRIES)
                    ));
                    break;
                }

                String entryName = normalizeZipEntryName(entry.getName());
                if (!isSafeZipEntryName(entryName)) {
                    parts.add(new ExtractedTextPart(
                            entry.getName(),
                            "zip-entry",
                            null,
                            null,
                            "skipped, unsafe entry name"
                    ));
                    continue;
                }

                byte[] entryBytes;
                try {
                    entryBytes = readZipEntry(zip, entryName, totalBytes);
                } catch (ArchiveReadLimitException e) {
                    parts.add(new ExtractedTextPart(
                            entryName,
                            detectExtractionType(entryName, null),
                            null,
                            null,
                            "skipped, %s".formatted(e.getMessage())
                    ));
                    parts.add(new ExtractedTextPart(
                            "...",
                            "zip-entry",
                            null,
                            null,
                            "stopped archive extraction after reaching safety limits"
                    ));
                    break;
                }

                String text = extractFromBytes(entryName, null, entryBytes, archiveDepth + 1);
                if (text == null) {
                    parts.add(new ExtractedTextPart(
                            entryName,
                            detectExtractionType(entryName, null),
                            (long) entryBytes.length,
                            null,
                            "skipped, not text-extractable"
                    ));
                    continue;
                }

                parts.add(new ExtractedTextPart(
                        entryName,
                        detectExtractionType(entryName, null),
                        (long) entryBytes.length,
                        text,
                        null
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from ZIP archive: {}", e.getMessage());
            return List.of(new ExtractedTextPart(
                    filename,
                    "zip",
                    (long) data.length,
                    null,
                    "failed to extract ZIP archive: %s".formatted(e.getMessage())
            ));
        }

        return parts;
    }

    private byte[] readZipEntry(ZipInputStream zip, String entryName, long[] totalBytes) throws IOException {
        var out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (out.size() + read > MAX_ARCHIVE_ENTRY_BYTES) {
                throw new ArchiveReadLimitException("%s exceeds per-file limit %s"
                        .formatted(entryName, formatBytes(MAX_ARCHIVE_ENTRY_BYTES)));
            }
            if (totalBytes[0] + read > MAX_ARCHIVE_TOTAL_BYTES) {
                throw new ArchiveReadLimitException("archive exceeds total limit %s"
                        .formatted(formatBytes(MAX_ARCHIVE_TOTAL_BYTES)));
            }
            out.write(buffer, 0, read);
            totalBytes[0] += read;
        }
        return out.toByteArray();
    }

    private String normalizeZipEntryName(String entryName) {
        return entryName == null ? "" : entryName.replace('\\', '/');
    }

    private boolean isSafeZipEntryName(String entryName) {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*")) {
            return false;
        }
        for (String part : entryName.split("/")) {
            if ("..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }

    private String normalizeExtractedText(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\t\\x0B\\f]+", " ");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.strip();
    }

    private static class ArchiveReadLimitException extends IOException {
        private ArchiveReadLimitException(String message) {
            super(message);
        }
    }

    public record ExtractedTextPart(
            String name,
            String extractionType,
            Long size,
            String content,
            String note
    ) {
        public boolean textExtracted() {
            return content != null;
        }
    }
}
