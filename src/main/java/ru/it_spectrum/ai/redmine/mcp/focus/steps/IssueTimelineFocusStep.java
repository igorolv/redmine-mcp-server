package ru.it_spectrum.ai.redmine.mcp.focus.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.focus.FocusStep;

import java.util.Optional;

/**
 * Keeps the issue timeline and commit context while omitting neighbouring data
 * that is usually irrelevant to "who did what and when" questions.
 */
public class IssueTimelineFocusStep implements FocusStep<Issue> {

    @Override
    public String name() {
        return "issue-timeline";
    }

    @Override
    public Optional<Focused<Issue>> apply(Issue value) {
        if (value == null) {
            return Optional.empty();
        }
        boolean changed = hasItems(value.attachments())
                || hasItems(value.customFields())
                || hasItems(value.related());
        if (!changed) {
            return Optional.empty();
        }
        String note = "timeline focus kept issue core fields, journals, and changesets and omitted attachments, custom fields, and related issue context";
        return Optional.of(new Focused<>(
                value.withCustomFields(null).withAttachments(null).withRelated(null),
                note));
    }

    private static boolean hasItems(java.util.List<?> items) {
        return items != null && !items.isEmpty();
    }
}
