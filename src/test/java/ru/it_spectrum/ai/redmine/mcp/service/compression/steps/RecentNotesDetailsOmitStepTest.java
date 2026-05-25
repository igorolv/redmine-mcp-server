package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.RecentNotesDetailsOmitStep;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentNotesDetailsOmitStepTest {

    @Test
    void emptyWhenRecentNotesAreNullOrEmpty() {
        var step = new RecentNotesDetailsOmitStep();
        assertThat(step.apply(stubContext(null))).isEmpty();
        assertThat(step.apply(stubContext(List.of()))).isEmpty();
    }

    @Test
    void emptyWhenNoDetailsPresent() {
        var step = new RecentNotesDetailsOmitStep();
        var ctx = stubContext(List.of(
                new Issue.Journal(1, new Ref(1, "u"), "note", "2026-01-01T00:00:00Z", null)
        ));

        assertThat(step.apply(ctx)).isEmpty();
    }

    @Test
    void nullsDetailsWhileKeepingNoteBodies() {
        var step = new RecentNotesDetailsOmitStep();
        var ctx = stubContext(List.of(
                new Issue.Journal(1, new Ref(1, "u"), "Recent note", "2026-01-01T00:00:00Z",
                        List.of(new Issue.Detail("attr", "subject", "old", "new"))),
                new Issue.Journal(2, new Ref(1, "u"), "Another", "2026-01-02T00:00:00Z",
                        List.of(new Issue.Detail("attr", "status_id", "1", "2"))),
                new Issue.Journal(3, new Ref(1, "u"), "Untouched", "2026-01-03T00:00:00Z", null)
        ));

        var result = step.apply(ctx).orElseThrow();

        assertThat(result.value().recentNotes()).hasSize(3);
        assertThat(result.value().recentNotes()).allSatisfy(j -> assertThat(j.details()).isNull());
        assertThat(result.value().recentNotes())
                .extracting(Issue.Journal::notes)
                .containsExactly("Recent note", "Another", "Untouched");
        assertThat(result.note()).contains("omitted field-change details from 2 recent-note entries");
    }

    private static IssueFullContext stubContext(List<Issue.Journal> recentNotes) {
        var issue = new Issue(1, null, null, null, null, null, null, null, null,
                "s", "d", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.of(), null, List.of(), null);
        return new IssueFullContext(issue, null, List.of(), List.of(),
                recentNotes == null ? null : List.copyOf(recentNotes),
                new ContextStats(false, false, false), null);
    }
}
