package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchRequest;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchResult;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextChunk;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextInfo;
import ru.it_spectrum.ai.redmine.mcp.model.ImageRenderResult;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.service.chunking.ChunkingOptions;
import ru.it_spectrum.ai.redmine.mcp.service.chunking.ChunkingStrategy;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private final ChunkingStrategy chunker;

    public AttachmentService(RedmineClient client, DocumentTextExtractor textExtractor, ChunkingStrategy chunker) {
        this.client = client;
        this.textExtractor = textExtractor;
        this.chunker = chunker;
    }

    public Optional<RedmineAttachment> find(int attachmentId) {
        return Optional.ofNullable(client.getAttachment(attachmentId));
    }

    public Optional<List<RedmineAttachment>> listForIssue(int issueId) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            return Optional.empty();
        }
        var attachments = issue.attachments();
        return Optional.of(attachments != null ? attachments : List.of());
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

    public Optional<String> extractText(RedmineAttachment attachment) {
        return Optional.ofNullable(textExtractor.extractText(attachment));
    }

    public AttachmentTextInfo describeText(int attachmentId) {
        var attachment = findOrThrow(attachmentId);

        String text = extractTextOrThrow(attachment);
        var options = ChunkingOptions.defaults();
        int chunkCount = chunker.countChunks(text, options);

        return new AttachmentTextInfo(
                attachment.id(),
                attachment.filename(),
                attachment.contentType(),
                true,
                textExtractor.detectExtractionType(attachment),
                text.length(),
                options.chunkSize(),
                chunkCount,
                text.length() > PREVIEW_LIMIT
        );
    }

    public AttachmentTextChunk fetchChunk(int attachmentId, int chunkIndex, Integer chunkSize) {
        var attachment = findOrThrow(attachmentId);

        String text = extractTextOrThrow(attachment);
        var options = ChunkingOptions.ofChunkSize(chunkSize);

        var chunks = chunker.split(text, options);

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

    public ImageRenderResult renderImage(int attachmentId, Integer maxWidth) {
        var attachment = findOrThrow(attachmentId);

        if (!isImage(attachment)) {
            throw new NotAnImageAttachmentException(attachmentId, attachment.filename());
        }

        byte[] imageData = client.downloadAttachment(attachment.contentUrl());
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
                String text = textExtractor.extractText(att);
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

    // --- Internal ---

    private String extractTextOrThrow(RedmineAttachment attachment) {
        String text = textExtractor.extractText(attachment);
        if (text == null) {
            throw new AttachmentNotExtractableException(attachment.id(), attachment.filename());
        }
        return text;
    }

    private List<RedmineIssue> loadIssuesForSearch(AttachmentSearchRequest request) {
        if (request.issueId() != null) {
            var issue = client.getIssue(request.issueId());
            return issue != null ? List.of(issue) : List.of();
        }

        var page = client.listIssues(request.projectId(), "*", null, null, null, null,
                "updated_on:desc", null, 0, request.issueLimit());
        var issues = new ArrayList<>(page.issues());
        var fullIssues = new ArrayList<RedmineIssue>();
        for (var issue : issues) {
            var full = client.getIssue(issue.id());
            if (full != null) {
                fullIssues.add(full);
            }
        }
        return fullIssues;
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
