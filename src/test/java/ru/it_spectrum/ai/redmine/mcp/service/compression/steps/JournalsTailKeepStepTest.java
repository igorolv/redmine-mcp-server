package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalsTailKeepStep;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JournalsTailKeepStepTest {

    @Test
    void emptyWhenJournalsBelowLimit() {
        var step = new JournalsTailKeepStep(10);
        var issue = stubIssue(journals(3));
        assertThat(step.apply(issue)).isEmpty();
    }

    @Test
    void keepsOnlyLastN() {
        var step = new JournalsTailKeepStep(2);
        var issue = stubIssue(journals(5));

        var result = step.apply(issue).orElseThrow();

        assertThat(result.value().journals())
                .extracting(Issue.Journal::id)
                .containsExactly(4, 5);
        assertThat(result.note()).contains("2 most recent of 5");
    }

    private static List<Issue.Journal> journals(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> new Issue.Journal(i, new Ref(1, "u"), "note " + i,
                        "2026-01-0%dT00:00:00Z".formatted(i), List.of()))
                .toList();
    }

    private static Issue stubIssue(List<Issue.Journal> journals) {
        return new Issue(1, null, null, null, null, null, null, null, null,
                "s", "d", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.copyOf(journals), null, List.of(), null);
    }
}
