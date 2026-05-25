package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.Optional;

/**
 * Adapts an {@link Issue}-level step so it can be applied to the inner
 * {@link IssueFullContext#issue()} from inside the IssueFullContext pipeline.
 */
public final class InnerIssueStep implements CompressionStep<IssueFullContext> {

    private final CompressionStep<Issue> delegate;

    public InnerIssueStep(CompressionStep<Issue> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return "issue/" + delegate.name();
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.issue() == null) {
            return Optional.empty();
        }
        var inner = delegate.apply(value.issue());
        if (inner.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Compressed<>(value.withIssue(inner.get().value()), inner.get().note()));
    }
}
