package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextChunk;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextInfo;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.service.chunking.ChunkingOptions;
import ru.it_spectrum.ai.redmine.mcp.service.chunking.ChunkingStrategy;

import io.modelcontextprotocol.spec.McpSchema;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

@Service
public class AttachmentTools {
    private static final int MAX_TEXT_LENGTH = 50_000;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final int DEFAULT_MAX_WIDTH = 1024;

    private final RedmineClient client;
    private final DocumentTextExtractor textExtractor;
    private final ChunkingStrategy chunker;

    public AttachmentTools(RedmineClient client, DocumentTextExtractor textExtractor, ChunkingStrategy chunker) {
        this.client = client;
        this.textExtractor = textExtractor;
        this.chunker = chunker;
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
            "PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and ZIP archives with supported files. " +
            "For images use getImageAttachment instead. " +
            "For other binary files returns only metadata. " +
            "Use listAttachments first to get the attachment ID.")
    public String getAttachmentContent(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            McpSyncRequestContext context
    ) {
        ProgressSupport.report(context, 0, 3, "Loading attachment metadata");
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            return "Attachment #%d not found".formatted(attachmentId);
        }

        var sb = new StringBuilder();
        sb.append("Attachment: %s\n".formatted(attachment.filename()));
        sb.append("Type: %s, Size: %s\n".formatted(attachment.contentType(), formatSize(attachment.filesize())));
        sb.append("Created: %s by %s\n\n".formatted(attachment.createdOn(),
                attachment.author() != null ? attachment.author().name() : "unknown"));

        ProgressSupport.report(context, 1, 3, "Extracting attachment content");
        String text = textExtractor.extractText(attachment);
        if (text != null) {
            sb.append("--- Content ---\n");
            sb.append(truncate(text));
        } else {
            String ext = textExtractor.getFileExtension(attachment.filename());
            if (IMAGE_EXTENSIONS.contains(ext)) {
                sb.append("Image file — use getImageAttachment to view. Content URL: %s\n"
                        .formatted(attachment.contentUrl()));
            } else {
                sb.append("Binary file — content not displayed. Content URL: %s\n"
                        .formatted(attachment.contentUrl()));
            }
        }

        ProgressSupport.done(context, "Attachment content ready");
        return sb.toString();
    }

    public String getAttachmentContent(int attachmentId) {
        return getAttachmentContent(attachmentId, null);
    }

    @McpTool(description = "Get metadata about extracted attachment text and the chunking plan. " +
            "Useful before requesting chunks from large text, PDF, DOCX, XLSX, or PPTX attachments.")
    public AttachmentTextInfo getAttachmentTextInfo(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            McpSyncRequestContext context
    ) {
        ProgressSupport.report(context, 0, 3, "Loading attachment metadata");
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment #%d not found".formatted(attachmentId));
        }

        ProgressSupport.report(context, 1, 3, "Extracting attachment text");
        String text = extractTextOrThrow(attachment);
        var options = ChunkingOptions.defaults();
        int chunkCount = chunker.countChunks(text, options);
        ProgressSupport.done(context, "Attachment text info ready");

        return new AttachmentTextInfo(
                attachment.id(),
                attachment.filename(),
                attachment.contentType(),
                true,
                textExtractor.detectExtractionType(attachment),
                text.length(),
                options.chunkSize(),
                chunkCount,
                text.length() > MAX_TEXT_LENGTH
        );
    }

    public AttachmentTextInfo getAttachmentTextInfo(int attachmentId) {
        return getAttachmentTextInfo(attachmentId, null);
    }

    @McpTool(description = "Get one chunk of extracted attachment text for large documents. " +
            "Use getAttachmentTextInfo first to determine chunk count and recommended chunk size.")
    public AttachmentTextChunk getAttachmentTextChunk(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Chunk index starting from 0") int chunkIndex,
            @McpToolParam(description = "Chunk size in characters, default 12000", required = false) Integer chunkSize,
            McpSyncRequestContext context
    ) {
        ProgressSupport.report(context, 0, 4, "Loading attachment metadata");
        var attachment = client.getAttachment(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment #%d not found".formatted(attachmentId));
        }

        ProgressSupport.report(context, 1, 4, "Extracting attachment text");
        String text = extractTextOrThrow(attachment);
        var options = ChunkingOptions.ofChunkSize(chunkSize);
        ProgressSupport.report(context, 2, 4, "Building text chunks");
        var chunks = chunker.split(text, options);

        if (chunkIndex < 0 || chunkIndex >= chunks.size()) {
            throw new IllegalArgumentException(
                    "Chunk index %d out of range, available 0..%d"
                            .formatted(chunkIndex, Math.max(0, chunks.size() - 1))
            );
        }

        var chunk = chunks.get(chunkIndex);
        ProgressSupport.done(context, "Attachment text chunk ready");
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

    public AttachmentTextChunk getAttachmentTextChunk(int attachmentId, int chunkIndex, Integer chunkSize) {
        return getAttachmentTextChunk(attachmentId, chunkIndex, chunkSize, null);
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

        String ext = textExtractor.getFileExtension(attachment.filename());
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

    @McpTool(description = "Search for text across attachments of a specific issue or all recent issues in a project. " +
            "Extracts text from PDF, DOCX, XLSX, PPTX, ZIP archives and text files, then searches for the query. " +
            "Returns matching snippets with context. At least one of issueId or projectId must be provided.")
    public String searchAttachmentContent(
            @McpToolParam(description = "Text to search for (case-insensitive)") String query,
            @McpToolParam(description = "Issue ID to search attachments of (optional)", required = false) Integer issueId,
            @McpToolParam(description = "Project identifier to search across recent issues (optional)", required = false) String projectId,
            @McpToolParam(description = "Max issues to scan when searching by project, default 10", required = false) Integer limit,
            McpSyncRequestContext context
    ) {
        if (issueId == null && (projectId == null || projectId.isBlank())) {
            return "At least one of issueId or projectId must be provided";
        }

        ProgressSupport.stage(context, "Preparing attachment search");

        int actualLimit = limit != null ? Math.min(Math.max(limit, 1), 50) : 10;
        int contextRadius = 100;

        List<RedmineIssue> issues = new ArrayList<>();
        if (issueId != null) {
            var issue = client.getIssue(issueId);
            if (issue == null) {
                return "Issue #%d not found".formatted(issueId);
            }
            issues.add(issue);
        } else {
            var page = client.listIssues(projectId, "*", null, null, null, null,
                    "updated_on:desc", null, 0, actualLimit);
            issues.addAll(page.issues());
            var fullIssues = new ArrayList<RedmineIssue>();
            int totalIssues = issues.size();
            int loadedIssues = 0;
            for (var issue : issues) {
                var full = client.getIssue(issue.id());
                loadedIssues++;
                if (loadedIssues == 1 || loadedIssues == totalIssues || loadedIssues % 5 == 0) {
                    ProgressSupport.report(context, loadedIssues, Math.max(totalIssues, 1),
                            "Loaded issue details %d/%d".formatted(loadedIssues, totalIssues));
                }
                if (full != null) {
                    fullIssues.add(full);
                }
            }
            issues = fullIssues;
        }

        int extractableAttachments = issues.stream()
                .filter(issue -> issue.attachments() != null)
                .mapToInt(issue -> (int) issue.attachments().stream()
                        .filter(att -> textExtractor.isTextExtractable(att.filename(), att.contentType()))
                        .count())
                .sum();
        if (extractableAttachments > 0) {
            ProgressSupport.report(context, 0, extractableAttachments,
                    "Scanning %d extractable attachments".formatted(extractableAttachments));
        }

        String queryLower = query.toLowerCase(Locale.ROOT);
        var sb = new StringBuilder();
        String scope = issueId != null ? "issue #%d".formatted(issueId)
                : "project %s".formatted(projectId);
        sb.append("Attachment content search for \"%s\" in %s\n".formatted(query, scope));

        int totalMatches = 0;
        int matchingAttachments = 0;
        int matchingIssues = 0;
        int scannedAttachments = 0;
        int processedAttachments = 0;
        int scannedIssues = 0;

        for (var issue : issues) {
            if (issue.attachments() == null || issue.attachments().isEmpty()) {
                continue;
            }
            scannedIssues++;
            boolean issueHasMatch = false;

            for (var att : issue.attachments()) {
                if (!textExtractor.isTextExtractable(att.filename(), att.contentType())) {
                    continue;
                }

                scannedAttachments++;
                processedAttachments++;
                if (extractableAttachments > 0
                        && (processedAttachments == 1 || processedAttachments == extractableAttachments
                        || processedAttachments % 5 == 0)) {
                    ProgressSupport.report(context, processedAttachments, extractableAttachments,
                            "Scanning attachment %d/%d: %s"
                                    .formatted(processedAttachments, extractableAttachments, att.filename()));
                }
                String text = textExtractor.extractText(att);
                if (text == null) {
                    continue;
                }

                String textLower = text.toLowerCase(Locale.ROOT);
                var matches = new ArrayList<String>();
                int searchFrom = 0;
                while (searchFrom < textLower.length() && matches.size() < 5) {
                    int idx = textLower.indexOf(queryLower, searchFrom);
                    if (idx < 0) break;

                    int snippetStart = Math.max(0, idx - contextRadius);
                    int snippetEnd = Math.min(text.length(), idx + query.length() + contextRadius);
                    String snippet = text.substring(snippetStart, snippetEnd)
                            .replace("\n", " ").strip();
                    if (snippetStart > 0) snippet = "..." + snippet;
                    if (snippetEnd < text.length()) snippet = snippet + "...";
                    matches.add(snippet);

                    searchFrom = idx + query.length();
                    totalMatches++;
                }

                if (!matches.isEmpty()) {
                    if (!issueHasMatch) {
                        sb.append("\nIssue #%d: %s\n".formatted(issue.id(), issue.subject()));
                        issueHasMatch = true;
                        matchingIssues++;
                    }
                    matchingAttachments++;
                    sb.append("  [%d] %s (%s, %s)\n".formatted(
                            att.id(), att.filename(), att.contentType(), formatSize(att.filesize())));
                    for (var snippet : matches) {
                        sb.append("    %s\n".formatted(snippet));
                    }
                }
            }
        }

        if (totalMatches == 0) {
            sb.append("\nNo matches found");
        }
        sb.append("\nFound %d matches in %d attachments across %d issues (scanned %d attachments in %d issues)\n"
                .formatted(totalMatches, matchingAttachments, matchingIssues, scannedAttachments, scannedIssues));
        ProgressSupport.done(context, "Attachment search finished");

        return sb.toString();
    }

    public String searchAttachmentContent(String query, Integer issueId, String projectId, Integer limit) {
        return searchAttachmentContent(query, issueId, projectId, limit, null);
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

    // --- Text helpers ---

    private String extractTextOrThrow(RedmineAttachment attachment) {
        String text = textExtractor.extractText(attachment);
        if (text == null) {
            throw new IllegalArgumentException(
                    "Attachment #%d (%s) is not text-extractable"
                            .formatted(attachment.id(), attachment.filename())
            );
        }
        return text;
    }

    // --- Helpers ---

    private String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) return text;
        return text.substring(0, MAX_TEXT_LENGTH) + "\n\n... (truncated, total length: %d chars)".formatted(text.length());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
