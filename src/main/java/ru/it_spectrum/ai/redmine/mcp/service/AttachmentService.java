package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentContentResult;
import ru.it_spectrum.ai.redmine.mcp.model.ImageRenderResult;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;

@Service
public class AttachmentService {

    public static final int PREVIEW_LIMIT = 50_000;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final int DEFAULT_MAX_IMAGE_WIDTH = 1024;

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
        return IMAGE_EXTENSIONS.contains(ext) || contentType.startsWith("image/");
    }

    public Optional<String> extractText(int issueId, RedmineAttachment attachment) {
        if (!isTextExtractable(attachment)) {
            return Optional.empty();
        }
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        return Optional.ofNullable(textExtractor.extractText(attachment, localFile));
    }

    public AttachmentContentResult readContent(int issueId, int attachmentId) {
        var attachment = findIssueAttachmentOrThrow(issueId, attachmentId);

        var maybeText = extractText(issueId, attachment);
        String content = null;
        boolean truncated = false;
        String note = null;
        if (maybeText.isPresent()) {
            String text = maybeText.get();
            content = truncatePreview(text);
            truncated = text.length() > PREVIEW_LIMIT;
        } else if (isImage(attachment)) {
            note = "Image file. Use getImageAttachment to view.";
        } else {
            note = "Binary file. Content not displayed.";
        }

        return new AttachmentContentResult(
                attachment,
                detectExtractionType(attachment),
                maybeText.isPresent(),
                truncated,
                content,
                note
        );
    }

    public ImageRenderResult renderImage(int issueId, int attachmentId, Integer maxWidth) {
        var attachment = findIssueAttachmentOrThrow(issueId, attachmentId);

        if (!isImage(attachment)) {
            throw new NotAnImageAttachmentException(attachmentId, attachment.filename());
        }

        byte[] imageData = loadAttachmentBytes(attachment, issueId);
        if (imageData == null) {
            throw new AttachmentDownloadFailedException(attachmentId);
        }

        int actualMaxWidth = maxWidth != null && maxWidth > 0 ? maxWidth : DEFAULT_MAX_IMAGE_WIDTH;
        String contentType = attachment.contentType() != null ? attachment.contentType() : "";
        String ext = fileExtension(attachment);

        try {
            String mimeType = contentType.startsWith("image/") ? contentType : "image/" + ext.replace("jpg", "jpeg");
            byte[] processedData = resizeImageIfNeeded(imageData, actualMaxWidth);
            if (processedData != imageData) {
                mimeType = "image/png";
            }
            return new ImageRenderResult(
                    attachment.id(),
                    attachment.filename(),
                    attachment.contentType(),
                    attachment.filesize(),
                    processedData,
                    mimeType
            );
        } catch (Exception e) {
            throw new ImageProcessingFailedException(attachmentId, e);
        }
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

    public void snapshotIssue(RedmineIssue issue) {
        if (issue == null) {
            return;
        }
        issueSnapshot.snapshotIssue(issue, RedmineClient.fullIssueSource(issue.id()));
    }

    private byte[] loadAttachmentBytes(RedmineAttachment attachment, int issueId) {
        var localFile = issueSnapshot.materializeAttachment(issueId, attachment);
        try {
            return Files.readAllBytes(localFile);
        } catch (IOException e) {
            throw new AttachmentDownloadFailedException(attachment.id(), e);
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

    private byte[] resizeImageIfNeeded(byte[] imageData, int maxWidth) throws Exception {
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
}
