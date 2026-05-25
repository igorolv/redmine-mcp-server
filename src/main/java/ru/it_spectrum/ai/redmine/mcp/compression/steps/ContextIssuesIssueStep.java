package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.ContextIssue;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adapts an {@link Issue}-level step to every issue under
 * {@link IssueFullContext#contextIssues()}.
 */
public final class ContextIssuesIssueStep implements CompressionStep<IssueFullContext> {

    private final CompressionStep<Issue> delegate;

    public ContextIssuesIssueStep(CompressionStep<Issue> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return "context-issues/" + delegate.name();
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.contextIssues() == null || value.contextIssues().isEmpty()) {
            return Optional.empty();
        }
        var updated = new ArrayList<ContextIssue>(value.contextIssues().size());
        var notes = new ArrayList<String>();
        int changedIssues = 0;
        for (var contextIssue : value.contextIssues()) {
            if (contextIssue == null || contextIssue.issue() == null) {
                updated.add(contextIssue);
                continue;
            }
            var inner = delegate.apply(contextIssue.issue());
            if (inner.isEmpty()) {
                updated.add(contextIssue);
                continue;
            }
            updated.add(new ContextIssue(inner.get().value(), contextIssue.roles()));
            notes.add(inner.get().note());
            changedIssues++;
        }
        if (changedIssues == 0) {
            return Optional.empty();
        }
        String note = "compressed %d context issue payloads with %s: %s"
                .formatted(changedIssues, delegate.name(), summarize(notes));
        return Optional.of(new Compressed<>(value.withContextIssues(List.copyOf(updated)), note));
    }

    private static String summarize(List<String> notes) {
        return notes.stream()
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + "; " + right)
                .orElse("context issue payloads changed");
    }
}
