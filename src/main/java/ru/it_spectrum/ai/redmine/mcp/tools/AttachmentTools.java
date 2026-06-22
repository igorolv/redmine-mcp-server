package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentDownloadFailedException;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.compression.AttachmentContentCompression;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "attachment", havingValue = "true", matchIfMissing = true)
public class AttachmentTools {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTools.class);

    private final AttachmentService attachmentService;
    private final AttachmentContentCompression compression;

    public AttachmentTools(AttachmentService attachmentService,
                           AttachmentContentCompression compression) {
        this.attachmentService = attachmentService;
        this.compression = compression;
    }

    @McpTool(
            description = "Get a Redmine attachment: downloads the file locally and extracts its text for text " +
            "files, PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and ZIP archives; images and other " +
            "binaries return metadata only. Call getIssue first to get attachment IDs.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public AttachmentContent getAttachment(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Total character budget for extracted text across all parts", required = false) Integer maxChars,
            @McpToolParam(description = "Per-part character cap for extracted text", required = false) Integer partLimit
    ) {
        return getAttachmentInternal(issueId, attachmentId, maxChars, partLimit);
    }

    public AttachmentContent getAttachment(int issueId, int attachmentId) {
        return getAttachmentInternal(issueId, attachmentId, null, null);
    }

    private AttachmentContent getAttachmentInternal(int issueId, int attachmentId,
                                                    Integer maxChars, Integer partLimit) {
        log.info("Tool call: getAttachment (attachmentId={}, issueId={}, maxChars={}, partLimit={})",
                attachmentId, issueId, maxChars, partLimit);
        long start = System.nanoTime();
        try {
            var result = attachmentService.getAttachment(issueId, attachmentId, maxChars, partLimit);
            var compressed = compression.compress(result);
            ToolLogger.completed(log, "getAttachment", start);
            return compressed;
        } catch (AttachmentNotFoundException | IssueNotFoundException | AttachmentDownloadFailedException e) {
            ToolLogger.failed(log, "getAttachment", start, e.getMessage());
            throw e;
        }
    }

}
