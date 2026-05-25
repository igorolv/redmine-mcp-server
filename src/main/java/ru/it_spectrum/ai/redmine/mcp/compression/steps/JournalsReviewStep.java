package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Journal;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.List;
import java.util.Optional;

/**
 * Keeps human notes for review and drops Redmine field-change history details.
 */
public class JournalsReviewStep implements CompressionStep<Issue> {

    @Override
    public String name() {
        return "journals-review";
    }

    @Override
    public Optional<Compressed<Issue>> apply(Issue value) {
        if (value == null || value.journals() == null || value.journals().isEmpty()) {
            return Optional.empty();
        }
        var compact = compact(value.journals());
        boolean changed = compact.size() != value.journals().size()
                || value.journals().stream().anyMatch(j -> j.details() != null);
        if (!changed) {
            return Optional.empty();
        }
        String note = "review profile kept %d journal notes of %d entries and omitted journal details/history-only entries"
                .formatted(compact.size(), value.journals().size());
        return Optional.of(new Compressed<>(value.withJournals(compact), note));
    }

    public static List<Journal> compact(List<Journal> journals) {
        if (journals == null) {
            return null;
        }
        return journals.stream()
                .filter(journal -> journal.notes() != null && !journal.notes().isBlank())
                .map(journal -> new Journal(
                        journal.id(),
                        journal.user(),
                        journal.notes(),
                        journal.createdOn(),
                        null))
                .toList();
    }
}
