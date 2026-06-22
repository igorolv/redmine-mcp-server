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
import ru.it_spectrum.ai.redmine.mcp.focus.AttachmentContentFocus;
import ru.it_spectrum.ai.redmine.mcp.focus.ResponseFocus;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.tools", name = "attachment", havingValue = "true", matchIfMissing = true)
public class AttachmentTools {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTools.class);

    private final AttachmentService attachmentService;
    private final AttachmentContentFocus focusShaper;
    private final AttachmentContentCompression compression;

    public AttachmentTools(AttachmentService attachmentService, AttachmentContentFocus focusShaper,
                           AttachmentContentCompression compression) {
        this.attachmentService = attachmentService;
        this.focusShaper = focusShaper;
        this.compression = compression;
    }

    @McpTool(
            description = "Get an attachment from Redmine. Downloads the original file into the local issue " +
            "snapshot directory and returns localPath/fileUri. Extracts text into parts[] for text files, PDF, " +
            "Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and ZIP archives (one part per entry); images and " +
            "other binary files return metadata only. Extracted text is bounded by a total budget (maxChars) and " +
            "a per-part cap (partLimit); when cut, the affected part and the response are marked truncated=true. " +
            "The full file is always available via localPath/fileUri. Call getIssue first to get attachment IDs.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public AttachmentContent getAttachment(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Attachment ID number") int attachmentId,
            @McpToolParam(description = "Total character budget for extracted text across all parts", required = false) Integer maxChars,
            @McpToolParam(description = "Per-part character cap for extracted text", required = false) Integer partLimit,
            @McpToolParam(description = "Response focus: default, implementation, timeline, or full", required = false) String focus
    ) {
        return getAttachmentInternal(issueId, attachmentId, maxChars, partLimit, focus);
    }

    public AttachmentContent getAttachment(int issueId, int attachmentId) {
        return getAttachmentInternal(issueId, attachmentId, null, null, null);
    }

    public AttachmentContent getAttachment(int issueId, int attachmentId, Integer maxChars, Integer partLimit) {
        return getAttachmentInternal(issueId, attachmentId, maxChars, partLimit, null);
    }

    private AttachmentContent getAttachmentInternal(int issueId, int attachmentId,
                                                    Integer maxChars, Integer partLimit,
                                                    String focus) {
        log.info("Tool call: getAttachment (attachmentId={}, issueId={}, maxChars={}, partLimit={}, focus={})",
                attachmentId, issueId, maxChars, partLimit, focus);
        long start = System.nanoTime();
        try {
            var result = attachmentService.getAttachment(issueId, attachmentId, maxChars, partLimit);
            var focused = focusShaper.apply(result, ResponseFocus.from(focus));
            var compressed = compression.compress(focused);
            ToolLogger.completed(log, "getAttachment", start);
            return compressed;
        } catch (AttachmentNotFoundException | IssueNotFoundException | AttachmentDownloadFailedException e) {
            ToolLogger.failed(log, "getAttachment", start, e.getMessage());
            throw e;
        }
    }

}
