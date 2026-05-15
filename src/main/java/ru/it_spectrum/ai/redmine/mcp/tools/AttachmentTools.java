package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextChunk;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextInfo;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentDownloadFailedException;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchRequest;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchResult;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.ImageProcessingFailedException;
import ru.it_spectrum.ai.redmine.mcp.service.NotAnImageAttachmentException;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Base64;

@Service
public class AttachmentTools {

    private final AttachmentService attachmentService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public AttachmentTools(AttachmentService attachmentService, JsonResponses json, ToolErrors errors) {
        this.attachmentService = attachmentService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "List all attachments for a specific Redmine issue. " +
            "Returns attachment names, sizes, content types, and IDs that can be used with getAttachmentContent.")
    public String listAttachments(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var maybeAttachments = attachmentService.listForIssue(issueId);
        if (maybeAttachments.isEmpty()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(new AttachmentListResult(issueId, maybeAttachments.get()));
    }

    @McpTool(description = "Get the content of an attachment from Redmine. " +
            "Supports text files (txt, log, xml, json, csv, etc.), " +
            "PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and ZIP archives with supported files. " +
            "For images use getImageAttachment instead. " +
            "For other binary files returns only metadata. " +
            "Use listAttachments first to get the attachment ID.")
    public String getAttachmentContent(
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        var maybeAttachment = attachmentService.find(attachmentId);
        if (maybeAttachment.isEmpty()) {
            return errors.notFound("attachment", "#" + attachmentId);
        }
        var attachment = maybeAttachment.get();

        var maybeText = attachmentService.extractText(attachment);
        String content = null;
        boolean truncated = false;
        String note = null;
        if (maybeText.isPresent()) {
            String text = maybeText.get();
            content = truncate(text);
            truncated = text.length() > AttachmentService.PREVIEW_LIMIT;
        } else if (attachmentService.isImage(attachment)) {
            note = "Image file. Use getImageAttachment to view.";
        } else {
            note = "Binary file. Content not displayed.";
        }

        return json.write(new AttachmentContentResult(
                attachment,
                attachmentService.detectExtractionType(attachment),
                maybeText.isPresent(),
                truncated,
                content,
                note
        ));
    }

    @McpTool(description = "Get metadata about extracted attachment text and the chunking plan. " +
            "Useful before requesting chunks from large text, PDF, DOCX, XLSX, or PPTX attachments.")
    public AttachmentTextInfo getAttachmentTextInfo(
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        try {
            return attachmentService.describeText(attachmentId);
        } catch (AttachmentNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    @McpTool(description = "Get one chunk of extracted attachment text for large documents. " +
            "Use getAttachmentTextInfo first to determine chunk count and recommended chunk size.")
    public AttachmentTextChunk getAttachmentTextChunk(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Chunk index starting from 0") int chunkIndex,
            @McpToolParam(description = "Chunk size in characters, default 12000", required = false) Integer chunkSize
    ) {
        try {
            return attachmentService.fetchChunk(attachmentId, chunkIndex, chunkSize);
        } catch (AttachmentNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    @McpTool(description = "Download an image attachment from Redmine and return it for visual analysis. " +
            "Supports PNG, JPEG, GIF, BMP, WebP. Automatically resizes large images to save tokens. " +
            "Use listAttachments first to get the attachment ID.")
    public McpSchema.CallToolResult getImageAttachment(
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Maximum image width in pixels for resizing (default 1024). " +
                    "Height is scaled proportionally.", required = false) Integer maxWidth
    ) {
        try {
            var rendered = attachmentService.renderImage(attachmentId, maxWidth);
            String base64 = Base64.getEncoder().encodeToString(rendered.data());
            String metadata = "Attachment: %s (%s, %s)".formatted(
                    rendered.filename(), rendered.contentType(), formatSize(rendered.size()));
            return McpSchema.CallToolResult.builder()
                    .addTextContent(metadata)
                    .addContent(new McpSchema.ImageContent(null, base64, rendered.mimeType()))
                    .build();
        } catch (AttachmentNotFoundException e) {
            return errorResult("Attachment #%d not found".formatted(e.attachmentId()));
        } catch (NotAnImageAttachmentException e) {
            return errorResult("Attachment #%d (%s) is not an image. Use getAttachmentContent for text/document files."
                    .formatted(e.attachmentId(), e.filename()));
        } catch (AttachmentDownloadFailedException e) {
            return errorResult("Failed to download attachment #%d".formatted(e.attachmentId()));
        } catch (ImageProcessingFailedException e) {
            return errorResult(e.getMessage());
        }
    }

    @McpTool(description = "Search for text across attachments of a specific issue or all recent issues in a project. " +
            "Extracts text from PDF, DOCX, XLSX, PPTX, ZIP archives and text files, then searches for the query. " +
            "Returns matching snippets with context. At least one of issueId or projectId must be provided.")
    public String searchAttachmentContent(
            @McpToolParam(description = "Text to search for (case-insensitive)") String query,
            @McpToolParam(description = "Issue ID to search attachments of (optional)", required = false) Integer issueId,
            @McpToolParam(description = "Project identifier to search across recent issues (optional)", required = false) String projectId,
            @McpToolParam(description = "Max issues to scan when searching by project, default 10", required = false) Integer limit
    ) {
        if (issueId == null && (projectId == null || projectId.isBlank())) {
            return errors.argument("At least one of issueId or projectId must be provided");
        }

        int issueLimit = limit != null ? Math.min(Math.max(limit, 1), 50) : 10;
        var request = new AttachmentSearchRequest(query, issueId, projectId, issueLimit);
        var result = attachmentService.search(request);

        if (!result.issueFound()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(new AttachmentSearchResponse(query, issueId, projectId, result));
    }

    private String truncate(String text) {
        if (text.length() <= AttachmentService.PREVIEW_LIMIT) return text;
        return text.substring(0, AttachmentService.PREVIEW_LIMIT)
                + "\n\n... (truncated, total length: %d chars)".formatted(text.length());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }

    private McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }

    public record AttachmentListResult(
            int issueId,
            java.util.List<ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment> attachments
    ) {
    }

    public record AttachmentContentResult(
            ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment attachment,
            String extractionType,
            boolean textExtracted,
            boolean truncated,
            String content,
            String note
    ) {
    }

    public record AttachmentSearchResponse(
            String query,
            Integer issueId,
            String projectId,
            AttachmentSearchResult result
    ) {
    }
}
