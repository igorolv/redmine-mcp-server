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

    @McpTool(description = "Get an attachment from Redmine. Always downloads the original file into the local " +
            "issue snapshot directory and returns localPath/fileUri. Also extracts text context into parts[] " +
            "when supported: text files, PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), and ZIP archives. " +
            "ZIP archives can produce one part per archive entry. Images and other binary files return " +
            "metadata, localPath/fileUri, and a note without text parts. " +
            "Use getIssue first when you need attachment IDs; getIssue returns the issue attachments list.")
    public String getAttachment(
            @McpToolParam(description = "Issue ID number") int issueId,
            @McpToolParam(description = "Attachment ID number") int attachmentId
    ) {
        return getAttachmentInternal(issueId, attachmentId);
    }

    private String getAttachmentInternal(int issueId, int attachmentId) {
        log.info("Tool call: getAttachment (attachmentId={}, issueId={})", attachmentId, issueId);
        long start = System.nanoTime();
        try {
            var result = attachmentService.getAttachment(issueId, attachmentId);
            ToolLogger.completed(log, "getAttachment", start);
            return json.write(result);
        } catch (AttachmentNotFoundException e) {
            ToolLogger.failed(log, "getAttachment", start, e.getMessage());
            return errors.notFound("attachment", "#" + attachmentId);
        } catch (IssueNotFoundException e) {
            ToolLogger.failed(log, "getAttachment", start, e.getMessage());
            return errors.notFound("issue", "#" + issueId);
        } catch (AttachmentDownloadFailedException e) {
            ToolLogger.failed(log, "getAttachment", start, e.getMessage());
            return errors.unavailable("attachment #" + attachmentId);
        }
    }

}
