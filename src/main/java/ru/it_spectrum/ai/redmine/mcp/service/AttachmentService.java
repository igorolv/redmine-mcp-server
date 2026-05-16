package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentContextPart;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentResult;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AttachmentService {

    public static final int PREVIEW_LIMIT = 50_000;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private final RedmineClient client;
    private final DocumentTextExtractor textExtractor;
    private final IssueSnapshotService issueSnapshot;

    @Autowired
    public AttachmentService(RedmineClient client, DocumentTextExtractor textExtractor,
                             IssueSnapshotService issueSnapshot) {
        this.client = client;
        this.textExtractor = textExtractor;
        this.issueSnapshot = issueSnapshot;
    }

    public Optional<RedmineAttachment> find(int attachmentId) {
        return Optional.ofNullable(client.getAttachment(attachmentId));
    }

    public RedmineAttachment findOrThrow(int attachmentId) {
        return find(attachmentId).orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
    }

    public boolean isTextExtractable(RedmineAttachment attachment) {
        return textExtractor.isTextExtractable(attachment.filename(), attachment.contentType());
    }

    public String detectExtractionType(RedmineAttachment attachment) {
        return textExtractor.detectExtractionType(attachment);
    }

    public String fileExtension(RedmineAttachment attachment) {
        return textExtractor.getFileExtension(attachment.filename());
    }

    public boolean isImage(RedmineAttachment attachment) {
        String ext = fileExtension(attachment);
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";
        return (ext != null && IMAGE_EXTENSIONS.contains(ext)) || contentType.startsWith("image/");
    }

    public Optional<String> extractText(int issueId, RedmineAttachment attachment) {
        if (!isTextExtractable(attachment)) {
            return Optional.empty();
        }
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        return Optional.ofNullable(textExtractor.extractText(attachment, localFile));
    }

    public AttachmentResult getAttachment(int issueId, int attachmentId) {
        var attachment = findIssueAttachmentOrThrow(issueId, attachmentId);
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        boolean image = isImage(attachment);
        String extractionType = image ? "image" : detectExtractionType(attachment);
        List<AttachmentContextPart> parts = List.of();
        String note = null;

        if (isTextExtractable(attachment)) {
            parts = textExtractor.extractTextParts(attachment, localFile).stream()
                    .map(this::toContextPart)
                    .toList();
        } else if (image) {
            note = "Image file. Text context not available; use localPath/fileUri to access the original file.";
        } else {
            note = "Binary file. Text context not available; use localPath/fileUri to access the original file.";
        }

        boolean textExtracted = parts.stream().anyMatch(AttachmentContextPart::textExtracted);
        boolean truncated = parts.stream().anyMatch(AttachmentContextPart::truncated);

        return new AttachmentResult(
                attachment,
                localFile.toString(),
                localFile.toUri().toString(),
                localSize(localFile),
                extractionType,
                textExtracted,
                truncated,
                parts,
                note
        );
    }

    public String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }

    // --- Internal ---

    private String truncatePreview(String text) {
        if (text.length() <= PREVIEW_LIMIT) return text;
        return text.substring(0, PREVIEW_LIMIT)
                + "\n\n... (truncated, total length: %d chars)".formatted(text.length());
    }

    private AttachmentContextPart toContextPart(DocumentTextExtractor.ExtractedTextPart part) {
        String content = part.content();
        boolean truncated = false;
        if (content != null) {
            truncated = content.length() > PREVIEW_LIMIT;
            content = truncatePreview(content);
        }

        return new AttachmentContextPart(
                part.name(),
                part.extractionType(),
                part.textExtracted(),
                truncated,
                content,
                part.note(),
                part.size()
        );
    }

    public void snapshotIssue(RedmineIssue issue) {
        if (issue == null) {
            return;
        }
        issueSnapshot.snapshotIssue(issue, RedmineClient.fullIssueSource(issue.id()));
    }

    private long localSize(Path localFile) {
        try {
            return Files.size(localFile);
        } catch (IOException e) {
            return -1;
        }
    }

    private RedmineAttachment findIssueAttachmentOrThrow(int issueId, int attachmentId) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            throw new IssueNotFoundException(issueId);
        }
        snapshotIssue(issue);
        if (issue.attachments() != null) {
            for (var attachment : issue.attachments()) {
                if (attachment.id() == attachmentId) {
                    return attachment;
                }
            }
        }
        throw new AttachmentNotFoundException(attachmentId);
    }

}
