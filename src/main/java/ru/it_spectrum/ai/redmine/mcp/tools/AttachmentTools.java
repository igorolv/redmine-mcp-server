package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentDownloadFailedException;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentNotFoundException;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueNotFoundException;

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

    @McpTool(description = "Download an attachment from Redmine into the local issue snapshot directory " +
            "and return the original file path and file URI. " +
            "Use getIssue first when you need attachment IDs; getIssue returns the issue attachments list.")
    public String getAttachmentFile(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        return getAttachmentFileInternal(issueId, attachmentId);
    }

    private String getAttachmentFileInternal(int issueId, int attachmentId) {
        log.info("Tool call: getAttachmentFile (attachmentId={}, issueId={})", attachmentId, issueId);
        long start = System.nanoTime();
        try {
            var result = attachmentService.materializeFile(issueId, attachmentId);
            ToolLogger.completed(log, "getAttachmentFile", start);
            return json.write(result);
        } catch (AttachmentNotFoundException e) {
            ToolLogger.failed(log, "getAttachmentFile", start, e.getMessage());
            return errors.notFound("attachment", "#" + attachmentId);
        } catch (IssueNotFoundException e) {
            ToolLogger.failed(log, "getAttachmentFile", start, e.getMessage());
            return errors.notFound("issue", "#" + issueId);
        } catch (AttachmentDownloadFailedException e) {
            ToolLogger.failed(log, "getAttachmentFile", start, e.getMessage());
            return errors.unavailable("attachment #" + attachmentId);
        }
    }

    @McpTool(description = "Get text context extracted from an attachment. " +
            "Supports text files (txt, log, xml, json, csv, etc.), " +
            "PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and ZIP archives with supported files. " +
            "Returns a structured parts array; ZIP archives can produce one part per archive entry. " +
            "For images and other binary files returns metadata and a note; use getAttachmentFile for the original file. " +
            "Use getIssue first when you need attachment IDs; getIssue returns the issue attachments list.")
    public String getAttachmentContext(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        return getAttachmentContextInternal(issueId, attachmentId);
    }

    private String getAttachmentContextInternal(int issueId, int attachmentId) {
        log.info("Tool call: getAttachmentContext (attachmentId={}, issueId={})", attachmentId, issueId);
        long start = System.nanoTime();
        try {
            var result = attachmentService.readContext(issueId, attachmentId);
            ToolLogger.completed(log, "getAttachmentContext", start);
            return json.write(result);
        } catch (AttachmentNotFoundException e) {
            ToolLogger.failed(log, "getAttachmentContext", start, e.getMessage());
            return errors.notFound("attachment", "#" + attachmentId);
        } catch (IssueNotFoundException e) {
            ToolLogger.failed(log, "getAttachmentContext", start, e.getMessage());
            return errors.notFound("issue", "#" + issueId);
        } catch (AttachmentDownloadFailedException e) {
            ToolLogger.failed(log, "getAttachmentContext", start, e.getMessage());
            return errors.unavailable("attachment #" + attachmentId);
        }
    }

}
