package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class AiContentMarker {
    public static final String TOKEN = "AI_EDIT";
    public static final String TEXT_PREFIX = TOKEN + ":";
    public static final String FILE_PREFIX = TOKEN + "__";
    private static final int MAX_FILENAME_LENGTH = 255;

    public String markText(String text) {
        if (text != null && text.startsWith(TEXT_PREFIX)) {
            return text;
        }
        if (text == null || text.isBlank()) {
            return TEXT_PREFIX;
        }
        return TEXT_PREFIX + "\n\n" + text;
    }

    public String markFilename(String filename) {
        String original = filename == null || filename.isBlank() ? "attachment" : filename;
        String marked = original.startsWith(FILE_PREFIX) ? original : FILE_PREFIX + original;
        if (marked.length() <= MAX_FILENAME_LENGTH) {
            return marked;
        }

        int dot = marked.lastIndexOf('.');
        String extension = dot > FILE_PREFIX.length() ? marked.substring(dot) : "";
        if (extension.length() > MAX_FILENAME_LENGTH - FILE_PREFIX.length()) {
            extension = extension.substring(extension.length() - (MAX_FILENAME_LENGTH - FILE_PREFIX.length()));
        }
        int baseLength = Math.max(FILE_PREFIX.length(), MAX_FILENAME_LENGTH - extension.length());
        return marked.substring(0, baseLength) + extension;
    }
}
