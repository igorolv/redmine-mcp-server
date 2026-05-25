package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.ContextIssue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Drops context issue journals. The surrounding issue role and issue summary stay
 * in the response; full history can be fetched with getIssue for that issue.
 */
public final class ContextIssueJournalsOmitStep implements CompressionStep<IssueFullContext> {

    @Override
    public String name() {
        return "context-issues-journals-omit";
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.contextIssues() == null || value.contextIssues().isEmpty()) {
            return Optional.empty();
        }
        var updated = new ArrayList<ContextIssue>(value.contextIssues().size());
        int changedIssues = 0;
        int omittedEntries = 0;
        for (var contextIssue : value.contextIssues()) {
            if (contextIssue == null || contextIssue.issue() == null || contextIssue.issue().journals() == null) {
                updated.add(contextIssue);
                continue;
            }
            changedIssues++;
            omittedEntries += contextIssue.issue().journals().size();
            updated.add(new ContextIssue(contextIssue.issue().withJournals(null), contextIssue.roles()));
        }
        if (changedIssues == 0) {
            return Optional.empty();
        }
        String note = "omitted %d journal entries from %d context issue payloads; fetch a specific context issue with getIssue for its full history"
                .formatted(omittedEntries, changedIssues);
        return Optional.of(new Compressed<>(value.withContextIssues(List.copyOf(updated)), note));
    }
}
