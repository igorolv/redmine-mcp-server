package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Caps the length of each remaining recent note's free-text body.
 */
public class RecentNoteContentTruncateStep implements CompressionStep<IssueFullContext> {

    private final int maxChars;

    public RecentNoteContentTruncateStep(int maxChars) {
        this.maxChars = Math.max(0, maxChars);
    }

    @Override
    public String name() {
        return "recent-note-content-truncate";
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.recentNotes() == null || value.recentNotes().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        int truncatedCount = 0;
        var newNotes = new ArrayList<Issue.Journal>(value.recentNotes().size());
        for (var j : value.recentNotes()) {
            String text = j.notes();
            if (text == null || text.length() <= maxChars) {
                newNotes.add(j);
                continue;
            }
            changed = true;
            truncatedCount++;
            String shortened = text.substring(0, maxChars) + "... (truncated by response compressor; total: %d chars)"
                    .formatted(text.length());
            newNotes.add(new Issue.Journal(j.id(), j.user(), shortened, j.createdOn(), j.details()));
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "recent-note bodies truncated to %d chars (%d entries affected)"
                .formatted(maxChars, truncatedCount);
        return Optional.of(new Compressed<>(value.withRecentNotes(List.copyOf(newNotes)), note));
    }
}
