package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchRequest;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class AttachmentService {

    public static final int PREVIEW_LIMIT = 50_000;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final int DEFAULT_MAX_IMAGE_WIDTH = 1024;
    private static final int MAX_MATCHES_PER_ATTACHMENT = 5;
    private static final int SEARCH_CONTEXT_RADIUS = 100;

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

    public AttachmentSearchResult search(AttachmentSearchRequest request) {
        List<RedmineIssue> issues = loadIssuesForSearch(request);
        boolean issueFound = request.issueId() == null || !issues.isEmpty();
        if (!issueFound) {
            return new AttachmentSearchResult(false, List.of(),
                    new AttachmentSearchResult.SearchCounters(0, 0, 0, 0, 0));
        }

        int extractableAttachments = (int) issues.stream()
                .filter(issue -> issue.attachments() != null)
                .flatMap(issue -> issue.attachments().stream())
                .filter(att -> textExtractor.isTextExtractable(att.filename(), att.contentType()))
                .count();
        String queryLower = request.query().toLowerCase(Locale.ROOT);
        var matchedIssues = new ArrayList<AttachmentSearchResult.IssueMatches>();
        int totalMatches = 0;
        int matchingAttachments = 0;
        int scannedAttachments = 0;
        int processedAttachments = 0;
        int scannedIssues = 0;

        for (var issue : issues) {
            if (issue.attachments() == null || issue.attachments().isEmpty()) {
                continue;
            }
            scannedIssues++;
            var attachmentMatches = new ArrayList<AttachmentSearchResult.AttachmentMatches>();

            for (var att : issue.attachments()) {
                if (!textExtractor.isTextExtractable(att.filename(), att.contentType())) {
                    continue;
                }
                scannedAttachments++;
                processedAttachments++;
                String text = extractText(issue.id(), att).orElse(null);
                if (text == null) {
                    continue;
                }

                String textLower = text.toLowerCase(Locale.ROOT);
                var snippets = new ArrayList<String>();
                int searchFrom = 0;
                while (searchFrom < textLower.length() && snippets.size() < MAX_MATCHES_PER_ATTACHMENT) {
                    int idx = textLower.indexOf(queryLower, searchFrom);
                    if (idx < 0) break;

                    int snippetStart = Math.max(0, idx - SEARCH_CONTEXT_RADIUS);
                    int snippetEnd = Math.min(text.length(), idx + request.query().length() + SEARCH_CONTEXT_RADIUS);
                    String snippet = text.substring(snippetStart, snippetEnd)
                            .replace("\n", " ").strip();
                    if (snippetStart > 0) snippet = "..." + snippet;
                    if (snippetEnd < text.length()) snippet = snippet + "...";
                    snippets.add(snippet);

                    searchFrom = idx + request.query().length();
                    totalMatches++;
                }

                if (!snippets.isEmpty()) {
                    matchingAttachments++;
                    attachmentMatches.add(new AttachmentSearchResult.AttachmentMatches(
                            att.id(), att.filename(), att.contentType(), att.filesize(), snippets));
                }
            }

            if (!attachmentMatches.isEmpty()) {
                matchedIssues.add(new AttachmentSearchResult.IssueMatches(
                        issue.id(), issue.subject(), attachmentMatches));
            }
        }

        var counters = new AttachmentSearchResult.SearchCounters(
                totalMatches, matchingAttachments, matchedIssues.size(), scannedAttachments, scannedIssues);
        return new AttachmentSearchResult(true, matchedIssues, counters);
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

    private List<RedmineIssue> loadIssuesForSearch(AttachmentSearchRequest request) {
        if (request.issueId() != null) {
            var issue = client.getIssue(request.issueId());
            snapshotIssue(issue);
            return issue != null ? List.of(issue) : List.of();
        }

        var page = client.listIssues(request.projectId(), "*", null, null, null, null,
                "updated_on:desc", null, 0, request.issueLimit());
        var issues = new ArrayList<>(page.issues());
        var fullIssues = new ArrayList<RedmineIssue>();
        for (var issue : issues) {
            var full = client.getIssue(issue.id());
            if (full != null) {
                snapshotIssue(full);
                fullIssues.add(full);
            }
        }
        return fullIssues;
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
