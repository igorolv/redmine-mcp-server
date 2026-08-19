package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.IssueMutationResult;
import ru.it_spectrum.ai.redmine.mcp.service.IssueMutationService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueMutationService.IssueFields;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class IssueWriteTools {

    private static final Logger log = LoggerFactory.getLogger(IssueWriteTools.class);

    private final IssueMutationService mutationService;

    public IssueWriteTools(IssueMutationService mutationService) {
        this.mutationService = mutationService;
    }

    @McpTool(
            description = "Create a Redmine issue.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    public IssueMutationResult createIssue(
            @McpToolParam(description = "Project identifier or numeric ID") String projectId,
            @McpToolParam(description = "") String subject,
            @McpToolParam(description = "", required = false) String description,
            @McpToolParam(description = "From listTrackers", required = false) Integer trackerId,
            @McpToolParam(description = "From listStatuses", required = false) Integer statusId,
            @McpToolParam(description = "From listPriorities", required = false) Integer priorityId,
            @McpToolParam(description = "", required = false) Integer assignedToUserId,
            @McpToolParam(description = "From listIssueCategories", required = false) Integer categoryId,
            @McpToolParam(description = "From listVersions", required = false) Integer fixedVersionId,
            @McpToolParam(description = "", required = false) Integer parentIssueId,
            @McpToolParam(description = "YYYY-MM-DD", required = false) String startDate,
            @McpToolParam(description = "YYYY-MM-DD", required = false) String dueDate,
            @McpToolParam(description = "0-100", required = false) Integer doneRatio,
            @McpToolParam(description = "", required = false) Double estimatedHours,
            @McpToolParam(description = "", required = false) Boolean isPrivate,
            @McpToolParam(description = "JSON object: field ID to scalar or scalar array, e.g. {\"10\":\"rtk\",\"11\":[\"a\",\"b\"]}", required = false) String customFieldsJson
    ) {
        log.info("Tool call: createIssue (projectId={}, trackerId={}, statusId={}, priorityId={})",
                projectId, trackerId, statusId, priorityId);
        long start = System.nanoTime();
        try {
            var result = mutationService.createIssue(fields(
                    projectId, trackerId, statusId, priorityId, subject, description,
                    categoryId, fixedVersionId, assignedToUserId, parentIssueId,
                    startDate, dueDate, doneRatio, estimatedHours, isPrivate, customFieldsJson));
            ToolLogger.completed(log, "createIssue", start);
            return result;
        } catch (RuntimeException e) {
            ToolLogger.failed(log, "createIssue", start, e.getMessage());
            throw e;
        }
    }

    @McpTool(
            description = "Update only the supplied fields of a Redmine issue.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    public IssueMutationResult updateIssue(
            @McpToolParam(description = "") int issueId,
            @McpToolParam(description = "Project identifier or numeric ID", required = false) String projectId,
            @McpToolParam(description = "", required = false) String subject,
            @McpToolParam(description = "", required = false) String description,
            @McpToolParam(description = "From listTrackers", required = false) Integer trackerId,
            @McpToolParam(description = "From listStatuses", required = false) Integer statusId,
            @McpToolParam(description = "From listPriorities", required = false) Integer priorityId,
            @McpToolParam(description = "0 clears", required = false) Integer assignedToUserId,
            @McpToolParam(description = "From listIssueCategories; 0 clears", required = false) Integer categoryId,
            @McpToolParam(description = "From listVersions; 0 clears", required = false) Integer fixedVersionId,
            @McpToolParam(description = "0 clears", required = false) Integer parentIssueId,
            @McpToolParam(description = "YYYY-MM-DD; empty string clears", required = false) String startDate,
            @McpToolParam(description = "YYYY-MM-DD; empty string clears", required = false) String dueDate,
            @McpToolParam(description = "0-100", required = false) Integer doneRatio,
            @McpToolParam(description = "", required = false) Double estimatedHours,
            @McpToolParam(description = "", required = false) Boolean isPrivate,
            @McpToolParam(description = "JSON object: field ID to scalar or scalar array", required = false) String customFieldsJson
    ) {
        log.info("Tool call: updateIssue (issueId={}, projectId={}, trackerId={}, statusId={}, priorityId={})",
                issueId, projectId, trackerId, statusId, priorityId);
        long start = System.nanoTime();
        try {
            var result = mutationService.updateIssue(issueId, fields(
                    projectId, trackerId, statusId, priorityId, subject, description,
                    categoryId, fixedVersionId, assignedToUserId, parentIssueId,
                    startDate, dueDate, doneRatio, estimatedHours, isPrivate, customFieldsJson));
            ToolLogger.completed(log, "updateIssue", start);
            return result;
        } catch (RuntimeException e) {
            ToolLogger.failed(log, "updateIssue", start, e.getMessage());
            throw e;
        }
    }

    @McpTool(
            description = "Add a note to a Redmine issue.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    public IssueMutationResult addIssueNote(
            @McpToolParam(description = "") int issueId,
            @McpToolParam(description = "") String notes
    ) {
        log.info("Tool call: addIssueNote (issueId={}, noteLength={})",
                issueId, notes != null ? notes.length() : null);
        long start = System.nanoTime();
        try {
            var result = mutationService.addIssueNote(issueId, notes);
            ToolLogger.completed(log, "addIssueNote", start);
            return result;
        } catch (RuntimeException e) {
            ToolLogger.failed(log, "addIssueNote", start, e.getMessage());
            throw e;
        }
    }

    @McpTool(
            description = "Attach a local file to a Redmine issue.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    public IssueMutationResult attachFileToIssue(
            @McpToolParam(description = "") int issueId,
            @McpToolParam(description = "") String localPath,
            @McpToolParam(description = "", required = false) String description
    ) {
        String filename = safeFilename(localPath);
        log.info("Tool call: attachFileToIssue (issueId={}, filename={})", issueId, filename);
        long start = System.nanoTime();
        try {
            var result = mutationService.attachFileToIssue(issueId, localPath, description);
            ToolLogger.completed(log, "attachFileToIssue", start);
            return result;
        } catch (RuntimeException e) {
            ToolLogger.failed(log, "attachFileToIssue", start, e.getMessage());
            throw e;
        }
    }

    private IssueFields fields(String projectId, Integer trackerId, Integer statusId, Integer priorityId,
                               String subject, String description, Integer categoryId, Integer fixedVersionId,
                               Integer assignedToId, Integer parentIssueId, String startDate, String dueDate,
                               Integer doneRatio, Double estimatedHours, Boolean isPrivate,
                               String customFieldsJson) {
        return new IssueFields(projectId, trackerId, statusId, priorityId, subject, description,
                categoryId, fixedVersionId, assignedToId, parentIssueId, startDate, dueDate,
                doneRatio, estimatedHours, isPrivate, customFieldsJson);
    }

    private String safeFilename(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return null;
        }
        try {
            return java.nio.file.Path.of(localPath).getFileName().toString();
        } catch (RuntimeException e) {
            return "<invalid>";
        }
    }
}
