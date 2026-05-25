package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.Optional;

/**
 * Applies review journal shaping to IssueFullContext recentNotes.
 */
public class RecentNotesReviewStep implements CompressionStep<IssueFullContext> {

    @Override
    public String name() {
        return "recent-notes-review";
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.recentNotes() == null || value.recentNotes().isEmpty()) {
            return Optional.empty();
        }
        var compact = JournalsReviewStep.compact(value.recentNotes());
        boolean changed = compact.size() != value.recentNotes().size()
                || value.recentNotes().stream().anyMatch(j -> j.details() != null);
        if (!changed) {
            return Optional.empty();
        }
        String note = "review profile kept %d recent notes of %d entries and omitted recent note details/history-only entries"
                .formatted(compact.size(), value.recentNotes().size());
        return Optional.of(new Compressed<>(value.withRecentNotes(compact), note));
    }
}
