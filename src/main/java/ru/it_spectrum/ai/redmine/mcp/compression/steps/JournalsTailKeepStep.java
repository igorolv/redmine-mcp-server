package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.List;
import java.util.Optional;

/**
 * Keeps the last {@code tailKeep} journal entries and drops everything older.
 */
public class JournalsTailKeepStep implements CompressionStep<Issue> {

    private final int tailKeep;

    public JournalsTailKeepStep(int tailKeep) {
        this.tailKeep = Math.max(0, tailKeep);
    }

    @Override
    public String name() {
        return "journals-tail-keep";
    }

    @Override
    public Optional<Compressed<Issue>> apply(Issue value) {
        if (value == null || value.journals() == null) {
            return Optional.empty();
        }
        int total = value.journals().size();
        if (total <= tailKeep) {
            return Optional.empty();
        }
        var kept = List.copyOf(value.journals().subList(total - tailKeep, total));
        String note = "kept %d most recent of %d journal entries; older history omitted"
                .formatted(tailKeep, total);
        return Optional.of(new Compressed<>(value.withJournals(kept), note));
    }
}
