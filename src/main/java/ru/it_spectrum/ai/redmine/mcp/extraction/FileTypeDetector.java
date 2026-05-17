package ru.it_spectrum.ai.redmine.mcp.extraction;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * File-kind detection by extension and content-type. Shared by {@link ExtractionPipeline},
 * its parsers, and {@code AttachmentService}. Carries the rules that used to live inside
 * {@code DocumentTextExtractor}.
 */
@Component
public class FileTypeDetector {

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "pptx");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    public String getFileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    public boolean isDocument(String filename, String contentType) {
        String ext = getFileExtension(filename);
        String ct = nullToEmpty(contentType);
        if (DOCUMENT_EXTENSIONS.contains(ext)) return true;
        return ct.equals("application/pdf")
                || ct.contains("wordprocessingml")
                || ct.contains("spreadsheetml")
                || ct.contains("presentationml");
    }

    public boolean isArchive(String filename, String contentType) {
        String ext = getFileExtension(filename);
        if (ARCHIVE_EXTENSIONS.contains(ext)) return true;
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.equals("application/zip")
                || lower.equals("application/x-zip-compressed")
                || lower.equals("application/zip-compressed");
    }

    public boolean isPlainText(String filename, String contentType) {
        if (contentType != null) {
            if (contentType.startsWith("text/")) return true;
            if (contentType.contains("json") || contentType.contains("xml") || contentType.contains("csv")) {
                return true;
            }
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv")
                    || lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".yml")
                    || lower.endsWith(".yaml") || lower.endsWith(".xsd") || lower.endsWith(".sql")
                    || lower.endsWith(".md") || lower.endsWith(".html")
                    || lower.endsWith(".properties") || lower.endsWith(".conf");
        }
        return false;
    }

    public boolean isImage(String filename, String contentType) {
        String ext = getFileExtension(filename);
        String ct = nullToEmpty(contentType);
        return IMAGE_EXTENSIONS.contains(ext) || ct.startsWith("image/");
    }

    public String detectExtractionType(String filename, String contentType) {
        String ext = getFileExtension(filename);
        String ct = nullToEmpty(contentType);

        if ("pdf".equals(ext) || ct.equals("application/pdf")) return "pdf";
        if ("docx".equals(ext) || ct.contains("wordprocessingml")) return "docx";
        if ("xlsx".equals(ext) || ct.contains("spreadsheetml")) return "xlsx";
        if ("pptx".equals(ext) || ct.contains("presentationml")) return "pptx";
        if (isArchive(filename, contentType)) return "zip";
        return "text";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
