package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.regex.Pattern;

/**
 * Prepends the configured {@code redmine-mcp.write.*-prefix} markers to AI-written content.
 * An empty prefix leaves the content untouched.
 */
@Component
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiContentMarker {
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String MULTILINE_SEPARATOR = "\n\n";
    private static final String SINGLE_LINE_SEPARATOR = " ";
    // Characters Redmine's Attachment#sanitize_filename would silently replace, plus control chars.
    private static final Pattern FORBIDDEN_FILENAME_CHARS = Pattern.compile("[/\\\\?%*:|\"'<>\\p{Cntrl}]");

    private final RedmineMcpProperties.Write write;

    public AiContentMarker(RedmineMcpProperties properties) {
        this.write = properties.write();
        String filenamePrefix = write.attachmentFilenamePrefix();
        if (FORBIDDEN_FILENAME_CHARS.matcher(filenamePrefix).find()) {
            throw new IllegalStateException(
                    "redmine-mcp.write.attachment-filename-prefix must not contain / \\ ? % * : | \" ' < > or control characters");
        }
        if (filenamePrefix.length() >= MAX_FILENAME_LENGTH) {
            throw new IllegalStateException(
                    "redmine-mcp.write.attachment-filename-prefix must be shorter than %d characters"
                            .formatted(MAX_FILENAME_LENGTH));
        }
    }

    public String markIssueDescription(String text) {
        return mark(text, write.issueDescriptionPrefix(), MULTILINE_SEPARATOR);
    }

    public String markIssueNote(String text) {
        return mark(text, write.issueNotePrefix(), MULTILINE_SEPARATOR);
    }

    public String markTimeEntryComment(String text) {
        return mark(text, write.timeEntryCommentPrefix(), SINGLE_LINE_SEPARATOR);
    }

    public String markWikiComment(String text) {
        return mark(text, write.wikiCommentPrefix(), SINGLE_LINE_SEPARATOR);
    }

    public String markFilename(String filename) {
        String prefix = write.attachmentFilenamePrefix();
        String original = filename == null || filename.isBlank() ? "attachment" : filename;
        String marked = prefix.isEmpty() || original.startsWith(prefix) ? original : prefix + original;
        if (marked.length() <= MAX_FILENAME_LENGTH) {
            return marked;
        }

        int dot = marked.lastIndexOf('.');
        String extension = dot > prefix.length() ? marked.substring(dot) : "";
        if (extension.length() > MAX_FILENAME_LENGTH - prefix.length()) {
            extension = extension.substring(extension.length() - (MAX_FILENAME_LENGTH - prefix.length()));
        }
        int baseLength = Math.max(prefix.length(), MAX_FILENAME_LENGTH - extension.length());
        return marked.substring(0, baseLength) + extension;
    }

    private static String mark(String text, String prefix, String separator) {
        if (prefix.isEmpty() || (text != null && text.startsWith(prefix))) {
            return text;
        }
        if (text == null || text.isBlank()) {
            return prefix;
        }
        return prefix + separator + text;
    }
}
