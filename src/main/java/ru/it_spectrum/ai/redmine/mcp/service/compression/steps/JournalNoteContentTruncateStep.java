package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Caps the length of each remaining journal note's free-text body.
 * Used as a last-resort budget step after older history has already been dropped:
 * we'd rather thin out the tail than mutilate individual notes.
 */
public class JournalNoteContentTruncateStep implements CompressionStep<Issue> {

    private final int maxChars;

    public JournalNoteContentTruncateStep(int maxChars) {
        this.maxChars = Math.max(0, maxChars);
    }

    @Override
    public String name() {
        return "journal-note-content-truncate";
    }

    @Override
    public Optional<Compressed<Issue>> apply(Issue value) {
        if (value == null || value.journals() == null || value.journals().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        int truncatedCount = 0;
        var newJournals = new ArrayList<Issue.Journal>(value.journals().size());
        for (var j : value.journals()) {
            String text = j.notes();
            if (text == null || text.length() <= maxChars) {
                newJournals.add(j);
                continue;
            }
            changed = true;
            truncatedCount++;
            String shortened = text.substring(0, maxChars)
                    + "... (truncated by response compressor; total: %d chars)".formatted(text.length());
            newJournals.add(new Issue.Journal(j.id(), j.user(), shortened, j.createdOn(), j.details()));
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "journal note bodies truncated to %d chars (%d entries affected)"
                .formatted(maxChars, truncatedCount);
        return Optional.of(new Compressed<>(value.withJournals(List.copyOf(newJournals)), note));
    }
}
