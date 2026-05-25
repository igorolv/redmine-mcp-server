package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Drops the field-change {@code details} of every journal entry while keeping the entry itself,
 * its free-text {@code notes}, author and timestamp.
 *
 * <p>Sits high in the budget pipeline: details are low-value next to human notes, and
 * one history-only entry can carry several detail objects of 200–400 chars each. Running
 * this step before any journal-thinning or note-truncation keeps the high-value text
 * intact for as long as possible.</p>
 */
public class JournalDetailsOmitStep implements CompressionStep<Issue> {

    @Override
    public String name() {
        return "journal-details-omit";
    }

    @Override
    public Optional<Compressed<Issue>> apply(Issue value) {
        if (value == null || value.journals() == null || value.journals().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        int affected = 0;
        var newJournals = new ArrayList<Issue.Journal>(value.journals().size());
        for (var j : value.journals()) {
            if (j.details() == null) {
                newJournals.add(j);
                continue;
            }
            changed = true;
            affected++;
            newJournals.add(new Issue.Journal(j.id(), j.user(), j.notes(), j.createdOn(), null));
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "omitted field-change details from %d journal entries; notes preserved"
                .formatted(affected);
        return Optional.of(new Compressed<>(value.withJournals(List.copyOf(newJournals)), note));
    }
}
