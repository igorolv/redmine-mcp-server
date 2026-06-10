package ru.it_spectrum.ai.redmine.mcp.focus.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.focus.FocusStep;

import java.util.Optional;

/**
 * Keeps only issue identity, core fields and changesets, omitting everything else.
 */
public class IssueChangesetsFocusStep implements FocusStep<Issue> {

    @Override
    public String name() {
        return "issue-changesets";
    }

    @Override
    public Optional<Focused<Issue>> apply(Issue value) {
        if (value == null) {
            return Optional.empty();
        }
        var stripped = new Issue(
                value.id(),
                value.project(),
                value.tracker(),
                value.status(),
                null,
                null,
                null,
                null,
                null,
                value.subject(),
                null,
                null,
                null,
                value.doneRatio(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                value.changesets(),
                null,
                null
        );
        var note = "changesets focus kept issue id, project, tracker, status, subject and changesets and omitted description, journals, attachments, custom fields, related issue context, dates and people";
        return Optional.of(new Focused<>(stripped, note));
    }
}
