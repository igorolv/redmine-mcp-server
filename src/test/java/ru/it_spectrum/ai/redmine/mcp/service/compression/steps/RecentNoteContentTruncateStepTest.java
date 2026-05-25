package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentNoteContentTruncateStepTest {

    @Test
    void emptyWhenAllNotesBelowLimit() {
        var step = new RecentNoteContentTruncateStep(100);
        var ctx = context(List.of(note(1, "short")));
        assertThat(step.apply(ctx)).isEmpty();
    }

    @Test
    void truncatesLongNotes() {
        var step = new RecentNoteContentTruncateStep(10);
        var ctx = context(List.of(
                note(1, "x".repeat(50)),
                note(2, "y".repeat(3))
        ));

        var result = step.apply(ctx).orElseThrow();

        assertThat(result.value().recentNotes().get(0).notes()).startsWith("xxxxxxxxxx");
        assertThat(result.value().recentNotes().get(0).notes()).contains("total: 50");
        assertThat(result.value().recentNotes().get(1).notes()).isEqualTo("yyy");
        assertThat(result.note()).contains("10 chars (1 entries affected)");
    }

    private static Issue.Journal note(int id, String text) {
        return new Issue.Journal(id, new Ref(1, "u"), text,
                "2026-01-0%dT00:00:00Z".formatted(id), List.of());
    }

    private static IssueFullContext context(List<Issue.Journal> notes) {
        return new IssueFullContext(null, null, List.of(), List.of(), List.copyOf(notes),
                new ContextStats(false, false, false), null);
    }
}
