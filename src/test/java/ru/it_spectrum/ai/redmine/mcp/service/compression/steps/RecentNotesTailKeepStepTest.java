package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RecentNotesTailKeepStepTest {

    @Test
    void emptyWhenBelowLimit() {
        var step = new RecentNotesTailKeepStep(5);
        var ctx = context(journals(3));
        assertThat(step.apply(ctx)).isEmpty();
    }

    @Test
    void keepsOnlyLastN() {
        var step = new RecentNotesTailKeepStep(2);
        var ctx = context(journals(5));

        var result = step.apply(ctx).orElseThrow();

        assertThat(result.value().recentNotes())
                .extracting(Issue.Journal::id)
                .containsExactly(4, 5);
    }

    private static List<Issue.Journal> journals(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> new Issue.Journal(i, new Ref(1, "u"), "n" + i,
                        "2026-01-0%dT00:00:00Z".formatted(i), List.of()))
                .toList();
    }

    private static IssueFullContext context(List<Issue.Journal> notes) {
        return new IssueFullContext(null, null, List.of(), List.of(), List.copyOf(notes),
                new ContextStats(false, false, false), null);
    }
}
