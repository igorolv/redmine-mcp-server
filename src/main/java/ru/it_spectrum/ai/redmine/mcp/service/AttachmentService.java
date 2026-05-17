package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractionPipeline;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.TextNormalizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class AttachmentService {

    public static final int PREVIEW_LIMIT = 50_000;

    private final RedmineClient client;
    private final ExtractionPipeline pipeline;
    private final FileTypeDetector types;
    private final IssueSnapshotService issueSnapshot;

    @Autowired
    public AttachmentService(RedmineClient client,
                             ExtractionPipeline pipeline,
                             FileTypeDetector types,
                             IssueSnapshotService issueSnapshot) {
        this.client = client;
        this.pipeline = pipeline;
        this.types = types;
        this.issueSnapshot = issueSnapshot;
    }

    public Optional<RedmineAttachment> find(int attachmentId) {
        return Optional.ofNullable(client.getAttachment(attachmentId));
    }

    public boolean isTextExtractable(RedmineAttachment attachment) {
        return types.isTextExtractable(attachment.filename(), attachment.contentType());
    }

    public String detectExtractionType(RedmineAttachment attachment) {
        return types.detectExtractionType(attachment.filename(), attachment.contentType());
    }

    public String fileExtension(RedmineAttachment attachment) {
        return types.getFileExtension(attachment.filename());
    }

    public boolean isImage(RedmineAttachment attachment) {
        return types.isImage(attachment.filename(), attachment.contentType());
    }

    public Optional<String> extractText(int issueId, RedmineAttachment attachment) {
        if (!isTextExtractable(attachment)) {
            return Optional.empty();
        }
        var parts = runPipeline(issueId, attachment);
        return Optional.ofNullable(flattenToText(parts));
    }

    public AttachmentContent getAttachment(int issueId, int attachmentId) {
        var attachment = findIssueAttachmentOrThrow(issueId, attachmentId);
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        boolean image = isImage(attachment);
        String extractionType = image ? "image" : detectExtractionType(attachment);
        List<AttachmentContent.Part> parts = List.of();
        String note = null;

        if (isTextExtractable(attachment)) {
            parts = runPipeline(issueId, attachment, localFile).stream()
                    .map(this::toContentPart)
                    .toList();
        } else if (image) {
            note = "Image file. Text context not available; use localPath/fileUri to access the original file.";
        } else {
            note = "Binary file. Text context not available; use localPath/fileUri to access the original file.";
        }

        boolean textExtracted = parts.stream().anyMatch(AttachmentContent.Part::textExtracted);
        boolean truncated = parts.stream().anyMatch(AttachmentContent.Part::truncated);

        return new AttachmentContent(
                Attachment.from(attachment),
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

    public void snapshotIssue(RedmineIssue issue) {
        if (issue == null) {
            return;
        }
        issueSnapshot.snapshotIssue(issue, RedmineClient.fullIssueSource(issue.id()));
    }

    // --- Internal ---

    private List<ExtractedPart> runPipeline(int issueId, RedmineAttachment attachment) {
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        return runPipeline(issueId, attachment, localFile);
    }

    private List<ExtractedPart> runPipeline(int issueId, RedmineAttachment attachment, Path localFile) {
        Path workDir = issueSnapshot.attachmentExtractedDir(issueId, attachment.id());
        return pipeline.extract(localFile, attachment.filename(), attachment.contentType(), workDir);
    }

    private static String flattenToText(List<ExtractedPart> parts) {
        var textParts = parts.stream().filter(ExtractedPart::textExtracted).toList();
        if (textParts.isEmpty()) return null;
        if (textParts.size() == 1 && textParts.getFirst().name() == null) {
            return textParts.getFirst().content();
        }
        var sb = new StringBuilder();
        for (var part : textParts) {
            sb.append("\n--- %s ---\n".formatted(part.name()));
            sb.append(part.content()).append("\n");
        }
        return TextNormalizer.normalize(sb.toString());
    }

    private String truncatePreview(String text) {
        if (text.length() <= PREVIEW_LIMIT) return text;
        return text.substring(0, PREVIEW_LIMIT)
                + "\n\n... (truncated, total length: %d chars)".formatted(text.length());
    }

    private AttachmentContent.Part toContentPart(ExtractedPart part) {
        String content = part.content();
        boolean truncated = false;
        if (content != null) {
            truncated = content.length() > PREVIEW_LIMIT;
            content = truncatePreview(content);
        }

        return new AttachmentContent.Part(
                part.name(),
                part.extractionType(),
                part.textExtracted(),
                truncated,
                content,
                part.note(),
                part.size()
        );
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
