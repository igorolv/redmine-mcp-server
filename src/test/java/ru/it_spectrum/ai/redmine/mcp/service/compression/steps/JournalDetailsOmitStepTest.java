package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalDetailsOmitStep;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JournalDetailsOmitStepTest {

    @Test
    void emptyWhenJournalsAreNullOrEmpty() {
        var step = new JournalDetailsOmitStep();
        assertThat(step.apply(stubIssue(null))).isEmpty();
        assertThat(step.apply(stubIssue(List.of()))).isEmpty();
    }

    @Test
    void emptyWhenNoDetailsPresent() {
        var step = new JournalDetailsOmitStep();
        var issue = stubIssue(List.of(
                new Issue.Journal(1, new Ref(1, "u"), "note", "2026-01-01T00:00:00Z", null),
                new Issue.Journal(2, new Ref(1, "u"), "note 2", "2026-01-02T00:00:00Z", null)
        ));

        assertThat(step.apply(issue)).isEmpty();
    }

    @Test
    void nullsDetailsWhileKeepingNotesAndMetadata() {
        var step = new JournalDetailsOmitStep();
        var issue = stubIssue(List.of(
                new Issue.Journal(1, new Ref(1, "u"), "Useful note", "2026-01-01T00:00:00Z",
                        List.of(new Issue.Detail("attr", "status_id", "1", "2"))),
                new Issue.Journal(2, new Ref(1, "u"), "", "2026-01-02T00:00:00Z",
                        List.of(new Issue.Detail("attachment", "10", null, "file.txt"))),
                new Issue.Journal(3, new Ref(1, "u"), "another", "2026-01-03T00:00:00Z", null)
        ));

        var result = step.apply(issue).orElseThrow();

        assertThat(result.value().journals()).hasSize(3);
        assertThat(result.value().journals()).allSatisfy(j -> assertThat(j.details()).isNull());
        assertThat(result.value().journals())
                .extracting(Issue.Journal::id, Issue.Journal::notes, Issue.Journal::createdOn)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "Useful note", "2026-01-01T00:00:00Z"),
                        org.assertj.core.groups.Tuple.tuple(2, "", "2026-01-02T00:00:00Z"),
                        org.assertj.core.groups.Tuple.tuple(3, "another", "2026-01-03T00:00:00Z"));
        assertThat(result.note()).contains("omitted field-change details from 2 journal entries");
    }

    @Test
    void leavesAlreadyNullEntriesUntouched() {
        var step = new JournalDetailsOmitStep();
        var journals = new ArrayList<Issue.Journal>();
        journals.add(new Issue.Journal(1, new Ref(1, "u"), "note", "2026-01-01T00:00:00Z", null));
        journals.add(new Issue.Journal(2, new Ref(1, "u"), "note 2", "2026-01-02T00:00:00Z",
                List.of(new Issue.Detail("attr", "status_id", "1", "2"))));
        var issue = stubIssue(journals);

        var result = step.apply(issue).orElseThrow();

        assertThat(result.note()).contains("1 journal entries");
    }

    private static Issue stubIssue(List<Issue.Journal> journals) {
        return new Issue(1, null, null, null, null, null, null, null, null, null,
                "s", "d", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), journals == null ? null : List.copyOf(journals),
                List.of(), List.of(), null, List.of(), null);
    }
}
