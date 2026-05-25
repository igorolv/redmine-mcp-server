package ru.it_spectrum.ai.redmine.mcp.focus.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Journal;
import ru.it_spectrum.ai.redmine.mcp.focus.FocusStep;

import java.util.List;
import java.util.Optional;

/**
 * Keeps human notes for implementation work and drops Redmine field-change history details.
 */
public class JournalsImplementationFocusStep implements FocusStep<Issue> {

    @Override
    public String name() {
        return "journals-implementation";
    }

    @Override
    public Optional<Focused<Issue>> apply(Issue value) {
        if (value == null || value.journals() == null || value.journals().isEmpty()) {
            return Optional.empty();
        }
        var focused = focus(value.journals());
        boolean changed = focused.size() != value.journals().size()
                || value.journals().stream().anyMatch(journal -> journal.details() != null);
        if (!changed) {
            return Optional.empty();
        }
        String note = "implementation focus kept %d journal notes of %d entries and omitted journal details/history-only entries"
                .formatted(focused.size(), value.journals().size());
        return Optional.of(new Focused<>(value.withJournals(focused), note));
    }

    public static List<Journal> focus(List<Journal> journals) {
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
