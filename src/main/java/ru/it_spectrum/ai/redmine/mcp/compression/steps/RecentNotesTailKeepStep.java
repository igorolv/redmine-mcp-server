package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.List;
import java.util.Optional;

/**
 * Keeps the last {@code tailKeep} recentNotes entries.
 */
public class RecentNotesTailKeepStep implements CompressionStep<IssueFullContext> {

    private final int tailKeep;

    public RecentNotesTailKeepStep(int tailKeep) {
        this.tailKeep = Math.max(0, tailKeep);
    }

    @Override
    public String name() {
        return "recent-notes-tail-keep";
    }

    @Override
    public Optional<Compressed<IssueFullContext>> apply(IssueFullContext value) {
        if (value == null || value.recentNotes() == null) {
            return Optional.empty();
        }
        int total = value.recentNotes().size();
        if (total <= tailKeep) {
            return Optional.empty();
        }
        var kept = List.copyOf(value.recentNotes().subList(total - tailKeep, total));
        String note = "kept %d most recent of %d recent-note entries; older notes omitted"
                .formatted(tailKeep, total);
        return Optional.of(new Compressed<>(value.withRecentNotes(kept), note));
    }
}
