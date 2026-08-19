package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.TimeEntryMutationResult;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntryMutation;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class TimeEntryMutationService {

    private static final int MAX_COMMENTS_LENGTH = 1024;

    private final RedmineMutationClient mutationClient;
    private final AiContentMarker marker;
    private final CustomFieldValuesParser customFieldValuesParser;

    public TimeEntryMutationService(RedmineMutationClient mutationClient,
                                    AiContentMarker marker,
                                    CustomFieldValuesParser customFieldValuesParser) {
        this.mutationClient = mutationClient;
        this.marker = marker;
        this.customFieldValuesParser = customFieldValuesParser;
    }

    public TimeEntryMutationResult createTimeEntry(Integer issueId, Integer projectId, double hours,
                                                   Integer activityId, String spentOn, String comments,
                                                   String customFieldsJson) {
        requireExactlyOneTarget(issueId, projectId);
        if (!Double.isFinite(hours) || hours < 0) {
            throw new IllegalArgumentException("hours must be a finite non-negative number");
        }

        String markedComments = marker.markText(comments);
        if (markedComments.length() > MAX_COMMENTS_LENGTH) {
            throw new IllegalArgumentException(
                    "comments must not exceed %d characters including the AI_EDIT marker"
                            .formatted(MAX_COMMENTS_LENGTH));
        }

        var fields = new RedmineTimeEntryMutation.Fields(
                projectId, issueId, hours, activityId, spentOn, markedComments,
                customFieldValuesParser.parse(customFieldsJson));
        var created = mutationClient.createTimeEntry(fields);
        if (created == null || created.id() <= 0) {
            throw new ResourceUnavailableException("created time entry response");
        }
        return new TimeEntryMutationResult(created.id());
    }

    private void requireExactlyOneTarget(Integer issueId, Integer projectId) {
        if ((issueId == null) == (projectId == null)) {
            throw new IllegalArgumentException("Exactly one of issueId or projectId must be provided");
        }
        if (issueId != null && issueId <= 0) {
            throw new IllegalArgumentException("issueId must be positive");
        }
        if (projectId != null && projectId <= 0) {
            throw new IllegalArgumentException("projectId must be positive");
        }
    }
}
