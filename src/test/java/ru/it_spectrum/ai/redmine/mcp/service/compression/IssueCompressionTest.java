package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IssueCompressionTest {

    @Test
    void returnsValueUnchangedWhenWithinBudget() {
        var props = propsWithBudget(100_000);
        var compression = TestCompression.issueCompression(props);
        var issue = stubIssue(List.of(), List.of());

        var result = compression.compress(issue);

        assertThat(result.compressionNotes()).isNull();
    }

    @Test
    void appliesChangesetTrimAndAttachesNotes() {
        var props = propsWithBudget(800);
        var compression = TestCompression.issueCompression(props);
        var bigComment = "Header line\n" + "body ".repeat(200);
        var changesets = IntStream.range(0, 10)
                .mapToObj(i -> new Issue.Changeset("rev" + i, new Ref(1, "u"), bigComment,
                        "2026-01-01T00:00:00Z", Issue.Changeset.SOURCE_REDMINE))
                .toList();
        var issue = stubIssue(List.of(), changesets);

        var result = compression.compress(issue);

        assertThat(result.compressionNotes()).isNotNull();
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("commit messages trimmed"));
        assertThat(result.changesets()).extracting(Issue.Changeset::comments)
                .allMatch("Header line"::equals);
    }

    @Test
    void appliesJournalTailKeepWhenChangesetTrimNotEnough() {
        var props = new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(500, 3, 20, 10_000, 10_000, 5));
        var compression = TestCompression.issueCompression(props);
        var journals = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new Issue.Journal(i, new Ref(1, "u"),
                        "very long note ".repeat(20),
                        "2026-01-0%dT00:00:00Z".formatted(Math.min(i, 9)), List.of()))
                .toList();
        var issue = stubIssue(journals, List.of());

        var result = compression.compress(issue);

        assertThat(result.journals()).hasSize(3);
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("3 most recent of 20"));
    }

    private static RedmineMcpProperties propsWithBudget(int budget) {
        return new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(budget, 30, 20, 10_000, 10_000, 5));
    }

    private static Issue stubIssue(List<Issue.Journal> journals, List<Issue.Changeset> changesets) {
        return new Issue(1, new Ref(1, "p"), new Ref(1, "t"), new Ref(1, "s"), new Ref(1, "pr"),
                null, null, null, null, null,
                "subject", "desc", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.copyOf(journals), List.of(), List.of(),
                List.copyOf(changesets), null);
    }
}
