package ru.it_spectrum.ai.redmine.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.IssueMutationResult;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueMutation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class IssueMutationService {

    private static final Logger log = LoggerFactory.getLogger(IssueMutationService.class);

    private final RedmineMutationClient mutationClient;
    private final RedmineClient client;
    private final IssueSnapshotService snapshotService;
    private final AiContentMarker marker;
    private final CustomFieldValuesParser customFieldValuesParser;

    public IssueMutationService(RedmineMutationClient mutationClient,
                                RedmineClient client,
                                IssueSnapshotService snapshotService,
                                AiContentMarker marker,
                                CustomFieldValuesParser customFieldValuesParser) {
        this.mutationClient = mutationClient;
        this.client = client;
        this.snapshotService = snapshotService;
        this.marker = marker;
        this.customFieldValuesParser = customFieldValuesParser;
    }

    public IssueMutationResult createIssue(IssueFields input) {
        requireText(input.projectId(), "projectId");
        requireText(input.subject(), "subject");

        var created = mutationClient.createIssue(toMutationFields(input, true));
        if (created == null || created.id() <= 0) {
            throw new ResourceUnavailableException("created issue response");
        }
        refreshAfterWrite(created.id(), "POST /issues.json");
        return result(created.id(), null, null);
    }

    public IssueMutationResult updateIssue(int issueId, IssueFields input) {
        var fields = toMutationFields(input, input.description() != null);
        if (!fields.hasChanges()) {
            throw new IllegalArgumentException("At least one issue field must be provided");
        }
        mutationClient.updateIssue(issueId, fields);
        refreshAfterWrite(issueId, "PUT /issues/%d.json".formatted(issueId));
        return result(issueId, null, null);
    }

    public IssueMutationResult addIssueNote(int issueId, String notes) {
        requireText(notes, "notes");
        var before = getIssueOrThrow(issueId);
        Set<Integer> previousJournalIds = journalIds(before);

        mutationClient.updateIssue(issueId, emptyFields(null, marker.markText(notes), null));
        var refreshed = refreshAfterWrite(issueId, "PUT /issues/%d.json (note)".formatted(issueId));
        Integer journalId = refreshed == null || refreshed.journals() == null ? null : refreshed.journals().stream()
                .filter(journal -> !previousJournalIds.contains(journal.id()))
                .map(RedmineIssue.Journal::id)
                .max(Integer::compareTo)
                .orElse(null);
        return result(issueId, journalId, null);
    }

    public IssueMutationResult attachFileToIssue(int issueId, String localPath, String description) {
        Path path = readableFile(localPath);
        var before = getIssueOrThrow(issueId);
        Set<Integer> previousAttachmentIds = attachmentIds(before);
        String filename = marker.markFilename(path.getFileName().toString());
        String contentType = contentType(path);

        String token = mutationClient.upload(filename, path);
        if (token == null || token.isBlank()) {
            throw new ResourceUnavailableException("Redmine upload token");
        }
        var upload = new RedmineIssueMutation.UploadReference(token, filename, contentType, description);
        mutationClient.updateIssue(issueId, emptyFields(null, null, List.of(upload)));

        var refreshed = refreshAfterWrite(issueId, "POST /uploads.json + PUT /issues/%d.json".formatted(issueId));
        Integer attachmentId = refreshed == null || refreshed.attachments() == null ? null : refreshed.attachments().stream()
                .filter(attachment -> !previousAttachmentIds.contains(attachment.id()))
                .map(RedmineAttachment::id)
                .max(Integer::compareTo)
                .orElse(null);
        return result(issueId, null, attachmentId);
    }

    private RedmineIssueMutation.Fields toMutationFields(IssueFields input, boolean markDescription) {
        String description = markDescription ? marker.markText(input.description()) : input.description();
        return new RedmineIssueMutation.Fields(
                input.projectId(), input.trackerId(), input.statusId(), input.priorityId(),
                input.subject(), description, input.categoryId(), input.fixedVersionId(),
                input.assignedToId(), input.parentIssueId(), input.startDate(), input.dueDate(),
                input.doneRatio(), input.estimatedHours(), input.isPrivate(),
                customFieldValuesParser.parse(input.customFieldsJson()), null, null);
    }

    private RedmineIssueMutation.Fields emptyFields(String description, String notes,
                                                     List<RedmineIssueMutation.UploadReference> uploads) {
        return new RedmineIssueMutation.Fields(
                null, null, null, null, null, description,
                null, null, null, null, null, null, null, null, null,
                null, notes, uploads);
    }

    private RedmineIssue refreshAfterWrite(int issueId, String source) {
        try {
            var issue = client.getIssue(issueId);
            if (issue == null) {
                log.warn("Redmine write succeeded but issue {} could not be refreshed", issueId);
                return null;
            }
            snapshotService.snapshotIssue(issue, source);
            return issue;
        } catch (RuntimeException e) {
            log.warn("Redmine write succeeded but issue {} could not be refreshed: {}", issueId, e.getMessage());
            return null;
        }
    }

    private RedmineIssue getIssueOrThrow(int issueId) {
        var issue = client.getIssue(issueId);
        if (issue == null) {
            throw new IssueNotFoundException(issueId);
        }
        return issue;
    }

    private Set<Integer> journalIds(RedmineIssue issue) {
        var ids = new HashSet<Integer>();
        if (issue.journals() != null) {
            issue.journals().forEach(journal -> ids.add(journal.id()));
        }
        return ids;
    }

    private Set<Integer> attachmentIds(RedmineIssue issue) {
        var ids = new HashSet<Integer>();
        if (issue.attachments() != null) {
            issue.attachments().forEach(attachment -> ids.add(attachment.id()));
        }
        return ids;
    }

    private IssueMutationResult result(int issueId, Integer journalId, Integer attachmentId) {
        return new IssueMutationResult(issueId, journalId, attachmentId);
    }

    private Path readableFile(String localPath) {
        requireText(localPath, "localPath");
        try {
            Path path = Path.of(localPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalArgumentException("localPath must point to a readable regular file");
            }
            return path;
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("localPath is invalid", e);
        }
    }

    private String contentType(Path path) {
        try {
            String detected = Files.probeContentType(path);
            return detected != null ? detected : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public record IssueFields(
            String projectId,
            Integer trackerId,
            Integer statusId,
            Integer priorityId,
            String subject,
            String description,
            Integer categoryId,
            Integer fixedVersionId,
            Integer assignedToId,
            Integer parentIssueId,
            String startDate,
            String dueDate,
            Integer doneRatio,
            Double estimatedHours,
            Boolean isPrivate,
            String customFieldsJson
    ) {
    }
}
