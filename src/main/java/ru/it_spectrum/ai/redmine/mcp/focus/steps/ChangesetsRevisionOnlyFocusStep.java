package ru.it_spectrum.ai.redmine.mcp.focus.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Changeset;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.focus.FocusStep;

import java.util.List;
import java.util.Optional;

/**
 * Keeps all changeset revisions while removing verbose commit metadata from an
 * implementation-focused response. The complete issue snapshot remains on disk.
 */
public class ChangesetsRevisionOnlyFocusStep implements FocusStep<Issue> {

    @Override
    public String name() {
        return "changesets-revision-only";
    }

    @Override
    public Optional<Focused<Issue>> apply(Issue value) {
        if (value == null || value.changesets() == null || value.changesets().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        var focused = new java.util.ArrayList<Changeset>(value.changesets().size());
        for (var changeset : value.changesets()) {
            if (changeset.user() != null || changeset.comments() != null
                    || changeset.committedOn() != null || changeset.source() != null) {
                changed = true;
            }
            focused.add(new Changeset(changeset.revision(), null, null, null, null));
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "implementation focus kept all %d changeset revisions and omitted commit comments, users, timestamps, and sources"
                .formatted(value.changesets().size());
        return Optional.of(new Focused<>(value.withChangesets(List.copyOf(focused)), note));
    }
}
