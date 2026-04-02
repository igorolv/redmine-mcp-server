package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.AttachmentTextCache;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextChunk;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextInfo;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;

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

import io.modelcontextprotocol.spec.McpSchema;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

@Service
public class AttachmentTools {
    private static final int MAX_TEXT_LENGTH = 50_000;
    private static final int DEFAULT_CHUNK_SIZE = 12_000;
    private static final int MIN_CHUNK_SIZE = 2_000;
    private static final int MAX_CHUNK_SIZE = 20_000;
    private static final int DEFAULT_CHUNK_OVERLAP = 1_200;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "pptx");
    private static final int DEFAULT_MAX_WIDTH = 1024;

    private final RedmineClient client;
    private final AttachmentTextCache textCache;

    public AttachmentTools(RedmineClient client, AttachmentTextCache textCache) {
        this.client = client;
        this.textCache = textCache;
    }

    @McpTool(description = "List all attachments for a specific Redmine issue. " +
            "Returns attachment names, sizes, content types, and IDs that can be used with getAttachmentContent.")
    public String listAttachments(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        var attachments = issue.attachments();
        if (attachments == null || attachments.isEmpty()) {
            return "Issue #%d has no attachments".formatted(issueId);
        }

        var sb = new StringBuilder();
        sb.append("Attachments for issue #%d (%d files):\n\n".formatted(issueId, attachments.size()));

        for (var att : attachments) {
            sb.append("- [%d] %s (%s, %s)\n".formatted(
                    att.id(), att.filename(), att.contentType(), formatSize(att.filesize())));
            if (att.description() != null && !att.description().isBlank()) {
                sb.append("  Description: %s\n".formatted(att.description()));
            }
        }

        return sb.toString();
    }

    @McpTool(description = "Get the content of an attachment from Redmine. " +
            "Supports text files (txt, log, xml, json, csv, etc.), " +
            "PDF, Word (.docx), Excel (.xlsx), and PowerPoint (.pptx). " +
            "For images use getImageAttachment instead. " +
            "For other binary files returns only metadata. " +
            "Use listAttachments first to get the attachment ID.")
    public String getAttachmentContent(
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            return "Attachment #%d not found".formatted(attachmentId);
        }

        var sb = new StringBuilder();
        sb.append("Attachment: %s\n".formatted(attachment.filename()));
        sb.append("Type: %s, Size: %s\n".formatted(attachment.contentType(), formatSize(attachment.filesize())));
        sb.append("Created: %s by %s\n\n".formatted(attachment.createdOn(),
                attachment.author() != null ? attachment.author().name() : "unknown"));

        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if (isDocumentContent(ext, contentType)) {
            byte[] content = client.downloadAttachment(attachment.contentUrl());
            if (content != null) {
                sb.append("--- Content ---\n");
                sb.append(truncate(extractDocumentText(ext, contentType, content)));
            }
        } else if (isTextContent(contentType, attachment.filename())) {
            byte[] content = client.downloadAttachment(attachment.contentUrl());
            if (content != null) {
                sb.append("--- Content ---\n");
                sb.append(truncate(new String(content, StandardCharsets.UTF_8)));
            }
        } else {
            sb.append("Binary file — content not displayed. Content URL: %s\n".formatted(attachment.contentUrl()));
        }

        return sb.toString();
    }

    @McpTool(description = "Get metadata about extracted attachment text and the chunking plan. " +
            "Useful before requesting chunks from large text, PDF, DOCX, XLSX, or PPTX attachments.")
    public AttachmentTextInfo getAttachmentTextInfo(
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment #%d not found".formatted(attachmentId));
        }

        String text = extractAttachmentTextOrThrow(attachment);
        int chunkCount = countChunks(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);

        return new AttachmentTextInfo(
                attachment.id(),
                attachment.filename(),
                attachment.contentType(),
                true,
                detectExtractionType(attachment),
                text.length(),
                DEFAULT_CHUNK_SIZE,
                chunkCount,
                text.length() > MAX_TEXT_LENGTH
        );
    }

    @McpTool(description = "Get one chunk of extracted attachment text for large documents. " +
            "Use getAttachmentTextInfo first to determine chunk count and recommended chunk size.")
    public AttachmentTextChunk getAttachmentTextChunk(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Chunk index starting from 0") int chunkIndex,
            @McpToolParam(description = "Chunk size in characters, default 12000", required = false) Integer chunkSize
    ) {
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment #%d not found".formatted(attachmentId));
        }

        String text = extractAttachmentTextOrThrow(attachment);
        int actualChunkSize = normalizeChunkSize(chunkSize);
        var chunks = splitIntoChunks(text, actualChunkSize, DEFAULT_CHUNK_OVERLAP);

        if (chunkIndex < 0 || chunkIndex >= chunks.size()) {
            throw new IllegalArgumentException(
                    "Chunk index %d out of range, available 0..%d"
                            .formatted(chunkIndex, Math.max(0, chunks.size() - 1))
            );
        }

        var chunk = chunks.get(chunkIndex);
        return new AttachmentTextChunk(
                attachment.id(),
                attachment.filename(),
                chunkIndex,
                chunks.size(),
                chunk.startChar(),
                chunk.endChar(),
                chunk.text()
        );
    }

    @McpTool(description = "Download an image attachment from Redmine and return it for visual analysis. " +
            "Supports PNG, JPEG, GIF, BMP, WebP. Automatically resizes large images to save tokens. " +
            "Use listAttachments first to get the attachment ID.")
    public McpSchema.CallToolResult getImageAttachment(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Maximum image width in pixels for resizing (default 1024). " +
                    "Height is scaled proportionally.", required = false) Integer maxWidth
    ) {
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Attachment #%d not found".formatted(attachmentId))
                    .isError(true)
                    .build();
        }

        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if (!IMAGE_EXTENSIONS.contains(ext) && !contentType.startsWith("image/")) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Attachment #%d (%s) is not an image. Use getAttachmentContent for text/document files."
                            .formatted(attachmentId, attachment.filename()))
                    .isError(true)
                    .build();
        }

        byte[] imageData = client.downloadAttachment(attachment.contentUrl());
        if (imageData == null) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Failed to download attachment #%d".formatted(attachmentId))
                    .isError(true)
                    .build();
        }

        int actualMaxWidth = maxWidth != null && maxWidth > 0 ? maxWidth : DEFAULT_MAX_WIDTH;

        try {
            String mimeType = contentType.startsWith("image/") ? contentType : "image/" + ext.replace("jpg", "jpeg");
            byte[] processedData = resizeImageIfNeeded(imageData, actualMaxWidth, ext);
            String base64 = Base64.getEncoder().encodeToString(processedData);

            // If resized, output format is always PNG
            if (processedData != imageData) {
                mimeType = "image/png";
            }

            String metadata = "Attachment: %s (%s, %s)".formatted(
                    attachment.filename(), attachment.contentType(), formatSize(attachment.filesize()));

            return McpSchema.CallToolResult.builder()
                    .addTextContent(metadata)
                    .addContent(new McpSchema.ImageContent(null, base64, mimeType))
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Failed to process image #%d: %s".formatted(attachmentId, e.getMessage()))
                    .isError(true)
                    .build();
        }
    }

    // --- Image processing ---

    private byte[] resizeImageIfNeeded(byte[] imageData, int maxWidth, String ext) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) {
            return imageData;
        }

        if (image.getWidth() <= maxWidth) {
            return imageData;
        }

        int newWidth = maxWidth;
        int newHeight = (int) Math.round((double) image.getHeight() * newWidth / image.getWidth());

        var resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        var out = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", out);
        return out.toByteArray();
    }

    // --- Document text extraction ---

    private String extractAttachmentTextOrThrow(RedmineAttachment attachment) {
        String cached = textCache.get(attachment.id());
        if (cached != null) {
            return cached;
        }

        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if (!isDocumentContent(ext, contentType) && !isTextContent(contentType, attachment.filename())) {
            throw new IllegalArgumentException(
                    "Attachment #%d (%s) is not text-extractable"
                            .formatted(attachment.id(), attachment.filename())
            );
        }

        byte[] content = client.downloadAttachment(attachment.contentUrl());
        if (content == null) {
            throw new IllegalStateException("Failed to download attachment #%d".formatted(attachment.id()));
        }

        String text;
        if (isDocumentContent(ext, contentType)) {
            text = extractDocumentText(ext, contentType, content);
        } else {
            text = new String(content, StandardCharsets.UTF_8);
        }

        text = normalizeExtractedText(text);
        textCache.put(attachment.id(), text);
        return text;
    }

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
                    var cells = new java.util.ArrayList<String>();
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
                var texts = new java.util.ArrayList<String>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            texts.add(text.strip());
                        }
                    }
                    if (shape instanceof XSLFTable table) {
                        for (int r = 0; r < table.getNumberOfRows(); r++) {
                            var cells = new java.util.ArrayList<String>();
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

    // --- Text chunking ---

    private int countChunks(String text, int chunkSize, int overlap) {
        return splitIntoChunks(text, chunkSize, overlap).size();
    }

    private List<TextChunk> splitIntoChunks(String text, int chunkSize, int overlap) {
        if (text.isBlank()) {
            return List.of(new TextChunk(0, 0, ""));
        }

        var chunks = new ArrayList<TextChunk>();
        int start = 0;

        while (start < text.length()) {
            int preferredEnd = Math.min(start + chunkSize, text.length());
            int end = findChunkBoundary(text, start, preferredEnd);
            if (end <= start) {
                end = preferredEnd;
            }

            String rawChunk = text.substring(start, end);
            String chunkText = rawChunk.strip();
            if (!chunkText.isEmpty()) {
                int leadingTrim = 0;
                while (leadingTrim < rawChunk.length() && Character.isWhitespace(rawChunk.charAt(leadingTrim))) {
                    leadingTrim++;
                }

                int trailingTrim = 0;
                while (trailingTrim < rawChunk.length() - leadingTrim
                        && Character.isWhitespace(rawChunk.charAt(rawChunk.length() - 1 - trailingTrim))) {
                    trailingTrim++;
                }

                chunks.add(new TextChunk(
                        start + leadingTrim,
                        end - trailingTrim,
                        chunkText
                ));
            }

            if (end >= text.length()) {
                break;
            }

            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    private int findChunkBoundary(String text, int start, int preferredEnd) {
        if (preferredEnd >= text.length()) {
            return text.length();
        }

        int paragraphBreak = text.lastIndexOf("\n\n", preferredEnd);
        if (paragraphBreak > start + 1000) {
            return paragraphBreak;
        }

        int lineBreak = text.lastIndexOf('\n', preferredEnd);
        if (lineBreak > start + 500) {
            return lineBreak;
        }

        int sentenceBreak = Math.max(text.lastIndexOf(". ", preferredEnd), text.lastIndexOf("! ", preferredEnd));
        sentenceBreak = Math.max(sentenceBreak, text.lastIndexOf("? ", preferredEnd));
        if (sentenceBreak > start + 500) {
            return sentenceBreak + 1;
        }

        return preferredEnd;
    }

    // --- Helpers ---

    private String detectExtractionType(RedmineAttachment attachment) {
        String ext = getFileExtension(attachment.filename());
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";

        if ("pdf".equals(ext) || contentType.equals("application/pdf")) return "pdf";
        if ("docx".equals(ext) || contentType.contains("wordprocessingml")) return "docx";
        if ("xlsx".equals(ext) || contentType.contains("spreadsheetml")) return "xlsx";
        if ("pptx".equals(ext) || contentType.contains("presentationml")) return "pptx";
        return "text";
    }

    private String normalizeExtractedText(String text) {
        if (text == null) return "";

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\t\\x0B\\f]+", " ");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.strip();
    }

    private int normalizeChunkSize(Integer chunkSize) {
        if (chunkSize == null) return DEFAULT_CHUNK_SIZE;
        return Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, chunkSize));
    }

    private boolean isTextContent(String contentType, String filename) {
        if (contentType != null) {
            if (contentType.startsWith("text/")) return true;
            if (contentType.contains("json") || contentType.contains("xml") || contentType.contains("csv")) return true;
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

    private boolean isDocumentContent(String ext, String contentType) {
        if (DOCUMENT_EXTENSIONS.contains(ext)) return true;
        return contentType.equals("application/pdf")
                || contentType.contains("wordprocessingml")
                || contentType.contains("spreadsheetml")
                || contentType.contains("presentationml");
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) return text;
        return text.substring(0, MAX_TEXT_LENGTH) + "\n\n... (truncated, total length: %d chars)".formatted(text.length());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }

    private record TextChunk(int startChar, int endChar, String text) {
    }
}
