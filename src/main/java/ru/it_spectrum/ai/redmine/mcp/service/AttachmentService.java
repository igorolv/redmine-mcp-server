package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractionPipeline;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AttachmentService {

    private final RedmineClient client;
    private final ExtractionPipeline pipeline;
    private final FileTypeDetector types;
    private final IssueSnapshotService issueSnapshot;
    private final RedmineMcpProperties properties;

    public AttachmentService(RedmineClient client,
                             ExtractionPipeline pipeline,
                             FileTypeDetector types,
                             IssueSnapshotService issueSnapshot,
                             RedmineMcpProperties properties) {
        this.client = client;
        this.pipeline = pipeline;
        this.types = types;
        this.issueSnapshot = issueSnapshot;
        this.properties = properties;
    }

    public Optional<RedmineAttachment> find(int attachmentId) {
        return Optional.ofNullable(client.getAttachment(attachmentId));
    }

    public AttachmentContent getAttachment(int issueId, int attachmentId) {
        var attachment = findIssueAttachmentOrThrow(issueId, attachmentId);
        return getAttachmentContent(issueId, attachment, properties.attachment().previewLimit());
    }

    public AttachmentContent getAttachmentContent(int issueId, RedmineAttachment attachment, int previewLimit) {
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        var parts = runPipeline(issueId, attachment, localFile).stream()
                .map(part -> toContentPart(part, previewLimit))
                .toList();
        return buildAttachmentContent(attachment, localFile, parts);
    }

    public AttachmentContent getAttachmentContentWithinTextBudget(int issueId, RedmineAttachment attachment,
                                                                  int textBudget) {
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        var parts = new ArrayList<AttachmentContent.Part>();
        int remaining = Math.max(0, textBudget);

        for (var extracted : runPipeline(issueId, attachment, localFile)) {
            int partBudget = extracted.textExtracted()
                    ? remaining
                    : properties.attachment().previewLimit();
            var part = toContentPart(extracted, partBudget);
            parts.add(part);
            if (part.textExtracted() && part.content() != null) {
                remaining -= Math.min(remaining, part.content().length());
            }
        }
        return buildAttachmentContent(attachment, localFile, parts);
    }

    private AttachmentContent buildAttachmentContent(RedmineAttachment attachment, Path localFile,
                                                     List<AttachmentContent.Part> parts) {
        boolean image = types.isImage(attachment.filename(), attachment.contentType());
        String extractionType = image
                ? "image"
                : types.detectExtractionType(attachment.filename(), attachment.contentType());

        boolean textExtracted = parts.stream().anyMatch(AttachmentContent.Part::textExtracted);
        boolean truncated = parts.stream().anyMatch(AttachmentContent.Part::truncated);
        String note = textExtracted ? null : noTextNote(image);

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

    AttachmentContent.Part toContentPart(ExtractedPart part, int previewLimit) {
        String content = part.content();
        boolean truncated = false;
        if (content != null) {
            truncated = content.length() > previewLimit;
            content = truncatePreview(content, previewLimit);
        }

        return new AttachmentContent.Part(
                part.name(),
                part.parent(),
                part.extractionType(),
                part.producer(),
                part.textExtracted(),
                truncated,
                content,
                part.localPath(),
                part.fileUri(),
                part.note(),
                part.size()
        );
    }

    private String truncatePreview(String text, int previewLimit) {
        if (text.length() <= previewLimit) return text;
        return text.substring(0, previewLimit)
                + "\n\n... (truncated, total length: %d chars)".formatted(text.length());
    }

    private String noTextNote(boolean image) {
        if (image) {
            return "Image file. Text context not available; use localPath/fileUri to access the file.";
        }
        return "No text content was extracted. Use localPath/fileUri on the returned parts to inspect the file.";
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
