package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.List;
import java.util.Optional;

/**
 * Keeps all changeset revisions but removes verbose commit metadata from the tool response.
 * The complete Redmine issue snapshot remains available on disk.
 */
public class ChangesetsRevisionOnlyStep implements CompressionStep<Issue> {

    @Override
    public String name() {
        return "changesets-revision-only";
    }

    @Override
    public Optional<Compressed<Issue>> apply(Issue value) {
        if (value == null || value.changesets() == null || value.changesets().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        var compact = new java.util.ArrayList<Issue.Changeset>(value.changesets().size());
        for (var cs : value.changesets()) {
            if (cs.user() != null || cs.comments() != null || cs.committedOn() != null) {
                changed = true;
            }
            compact.add(new Issue.Changeset(cs.revision(), null, null, null, cs.source()));
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "review profile kept all %d changeset revisions and omitted commit comments, users, and timestamps"
                .formatted(value.changesets().size());
        return Optional.of(new Compressed<>(value.withChangesets(List.copyOf(compact)), note));
    }
}
