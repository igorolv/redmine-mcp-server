package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Drops field-change {@code details} of every {@link IssueFullContext#recentNotes()} entry,
 * keeping the note body, author and timestamp. Recent notes are valued for their human text;
 * the structured details on these entries duplicate information available elsewhere and are
 * the cheapest thing to drop before we start truncating the bodies.
 */
public class RecentNotesDetailsOmitStep implements CompressionStep<IssueFullContext> {

    @Override
    public String name() {
        return "recent-notes-details-omit";
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.recentNotes() == null || value.recentNotes().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        int affected = 0;
        var updated = new ArrayList<Issue.Journal>(value.recentNotes().size());
        for (var j : value.recentNotes()) {
            if (j.details() == null) {
                updated.add(j);
                continue;
            }
            changed = true;
            affected++;
            updated.add(new Issue.Journal(j.id(), j.user(), j.notes(), j.createdOn(), null));
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "omitted field-change details from %d recent-note entries; notes preserved"
                .formatted(affected);
        return Optional.of(new Compressed<>(value.withRecentNotes(List.copyOf(updated)), note));
    }
}
