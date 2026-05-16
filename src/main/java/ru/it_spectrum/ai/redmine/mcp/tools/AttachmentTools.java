package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentDownloadFailedException;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchRequest;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentSearchResponse;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.ImageProcessingFailedException;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.NotAnImageAttachmentException;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Base64;

@Service
public class AttachmentTools {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTools.class);

    private final AttachmentService attachmentService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public AttachmentTools(AttachmentService attachmentService, JsonResponses json, ToolErrors errors) {
        this.attachmentService = attachmentService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get the content of an attachment from Redmine. " +
            "Supports text files (txt, log, xml, json, csv, etc.), " +
            "PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and ZIP archives with supported files. " +
            "For images use getImageAttachment instead. " +
            "For other binary files returns only metadata. " +
            "Use getIssue first when you need attachment IDs; getIssue returns the issue attachments list.")
    public String getAttachmentContent(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        return getAttachmentContentInternal(issueId, attachmentId);
    }

    private String getAttachmentContentInternal(int issueId, int attachmentId) {
        log.info("Tool call: getAttachmentContent (attachmentId={}, issueId={})", attachmentId, issueId);
        long start = System.nanoTime();
        try {
            var result = attachmentService.readContent(issueId, attachmentId);
            ToolLogger.completed(log, "getAttachmentContent", start);
            return json.write(result);
        } catch (AttachmentNotFoundException e) {
            ToolLogger.failed(log, "getAttachmentContent", start, e.getMessage());
            return errors.notFound("attachment", "#" + attachmentId);
        } catch (IssueNotFoundException e) {
            ToolLogger.failed(log, "getAttachmentContent", start, e.getMessage());
            return errors.notFound("issue", "#" + issueId);
        } catch (AttachmentDownloadFailedException e) {
            ToolLogger.failed(log, "getAttachmentContent", start, e.getMessage());
            return errors.unavailable("attachment #" + attachmentId);
        }
    }

    @McpTool(description = "Download an image attachment from Redmine and return it for visual analysis. " +
            "Supports PNG, JPEG, GIF, BMP, WebP. Automatically resizes large images to save tokens. " +
            "Use getIssue first when you need attachment IDs; getIssue returns the issue attachments list.")
    public McpSchema.CallToolResult getImageAttachment(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Maximum image width in pixels for resizing (default 1024). " +
                    "Height is scaled proportionally.", required = false) Integer maxWidth
    ) {
        return getImageAttachmentInternal(issueId, attachmentId, maxWidth);
    }

    private McpSchema.CallToolResult getImageAttachmentInternal(int issueId, int attachmentId, Integer maxWidth) {
        log.info("Tool call: getImageAttachment (attachmentId={}, maxWidth={}, issueId={})",
                attachmentId, maxWidth, issueId);
        long start = System.nanoTime();
        try {
            var rendered = attachmentService.renderImage(issueId, attachmentId, maxWidth);
            String base64 = Base64.getEncoder().encodeToString(rendered.data());
            String metadata = "Attachment: %s (%s, %s)".formatted(
                    rendered.filename(), rendered.contentType(), attachmentService.formatSize(rendered.size()));
            var result = McpSchema.CallToolResult.builder()
                    .addTextContent(metadata)
                    .addContent(new McpSchema.ImageContent(null, base64, rendered.mimeType()))
                    .build();
            ToolLogger.completed(log, "getImageAttachment", start);
            return result;
        } catch (AttachmentNotFoundException e) {
            ToolLogger.failed(log, "getImageAttachment", start, e.getMessage());
            return errorResult("Attachment #%d not found".formatted(e.attachmentId()));
        } catch (IssueNotFoundException e) {
            ToolLogger.failed(log, "getImageAttachment", start, e.getMessage());
            return errorResult("Issue #%d not found".formatted(e.issueId()));
        } catch (NotAnImageAttachmentException e) {
            ToolLogger.failed(log, "getImageAttachment", start, e.getMessage());
            return errorResult("Attachment #%d (%s) is not an image. Use getAttachmentContent for text/document files."
                    .formatted(e.attachmentId(), e.filename()));
        } catch (AttachmentDownloadFailedException e) {
            ToolLogger.failed(log, "getImageAttachment", start, e.getMessage());
            return errorResult("Failed to download attachment #%d".formatted(e.attachmentId()));
        } catch (ImageProcessingFailedException e) {
            ToolLogger.failed(log, "getImageAttachment", start, e.getMessage());
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
        log.info("Tool call: searchAttachmentContent (query={}, issueId={}, projectId={}, limit={})",
                query, issueId, projectId, limit);
        long start = System.nanoTime();
        if (issueId == null && (projectId == null || projectId.isBlank())) {
            ToolLogger.failed(log, "searchAttachmentContent", start, "At least one of issueId or projectId must be provided");
            return errors.argument("At least one of issueId or projectId must be provided");
        }

        int issueLimit = limit != null ? Math.min(Math.max(limit, 1), 50) : 10;
        var request = new AttachmentSearchRequest(query, issueId, projectId, issueLimit);
        var result = attachmentService.search(request);
        ToolLogger.completed(log, "searchAttachmentContent", start);

        if (!result.issueFound()) {
            return errors.notFound("issue", "#" + issueId);
        }
        return json.write(new AttachmentSearchResponse(query, issueId, projectId, result));
    }

    private McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }

}
