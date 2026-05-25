package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Changeset;
import ru.it_spectrum.ai.redmine.mcp.api.Detail;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Journal;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionOptions;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalNoteContentTruncateStep;
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
                .mapToObj(i -> new Changeset("rev" + i, new Ref(1, "u"), bigComment,
                        "2026-01-01T00:00:00Z", Changeset.SOURCE_REDMINE))
                .toList();
        var issue = stubIssue(List.of(), changesets);

        var result = compression.compress(issue);

        assertThat(result.compressionNotes()).isNotNull();
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("commit messages trimmed"));
        assertThat(result.changesets()).extracting(Changeset::comments)
                .allMatch("Header line"::equals);
    }

    @Test
    void appliesJournalTailKeepWhenChangesetTrimNotEnough() {
        var props = new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(1800, 3, 10_000, 10_000, 5));
        var compression = TestCompression.issueCompression(props);
        var journals = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new Journal(i, new Ref(1, "u"),
                        "very long note ".repeat(20),
                        "2026-01-0%dT00:00:00Z".formatted(Math.min(i, 9)), List.of()))
                .toList();
        var issue = stubIssue(journals, List.of());

        var result = compression.compress(issue);

        assertThat(result.journals()).hasSize(3);
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("3 most recent of 20"));
    }

    @Test
    void dropsJournalDetailsBeforeAnyNoteCompression() {
        // Budget large enough that detail-drop alone fits — no tail-keep, no note truncation.
        var props = new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(3000, 30, 10_000, 10_000, 5));
        var compression = TestCompression.issueCompression(props);
        var heavyDetails = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new Detail("attr", "field_" + i,
                        "old_value_with_some_content_" + i,
                        "new_value_with_some_content_" + i))
                .toList();
        var journals = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new Journal(i, new Ref(1, "u"), "note " + i,
                        "2026-01-0%dT00:00:00Z".formatted(Math.min(i, 9)), heavyDetails))
                .toList();
        var issue = stubIssue(journals, List.of());

        var result = compression.compress(issue);

        assertThat(result.journals()).hasSize(10);
        assertThat(result.journals()).allSatisfy(j -> assertThat(j.details()).isNull());
        assertThat(result.journals()).allSatisfy(j -> assertThat(j.notes()).startsWith("note "));
        assertThat(result.compressionNotes())
                .anyMatch(s -> s.contains("omitted field-change details"));
    }

    @Test
    void detailsAreAlreadyNullByTheTimeNotesAreTruncated() {
        // Budget tight enough that we have to truncate note bodies; details must be gone first.
        var props = new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(3000, 10, 10_000, 100, 5));
        var compression = TestCompression.issueCompression(props);
        var details = List.of(
                new Detail("attr", "status_id", "1", "2"),
                new Detail("attr", "assigned_to_id", "100", "200"));
        var journals = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new Journal(i, new Ref(1, "u"),
                        "x".repeat(1000),
                        "2026-01-0%dT00:00:00Z".formatted(i), details))
                .toList();
        var issue = stubIssue(journals, List.of());

        var result = compression.compress(issue);

        assertThat(result.journals()).allSatisfy(j -> assertThat(j.details()).isNull());
        assertThat(result.journals()).anySatisfy(j ->
                assertThat(j.notes()).contains("truncated by response compressor"));
        assertThat(result.compressionNotes())
                .anyMatch(s -> s.contains("omitted field-change details"))
                .anyMatch(s -> s.contains("journal note bodies truncated"));
    }

    @Test
    void truncatesJournalNoteBodiesWhenTailKeepNotEnough() {
        var props = new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(3000, 10, 10_000, 100, 5));
        var compression = TestCompression.issueCompression(props);
        var journals = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new Journal(i, new Ref(1, "u"),
                        "x".repeat(1000),
                        "2026-01-0%dT00:00:00Z".formatted(i), List.of()))
                .toList();
        var issue = stubIssue(journals, List.of());

        var result = compression.compress(issue);

        assertThat(result.journals()).hasSize(5);
        assertThat(result.journals()).allSatisfy(j ->
                assertThat(j.notes()).startsWith("x".repeat(100))
                        .contains("truncated by response compressor")
                        .contains("total: 1000 chars"));
        assertThat(result.compressionNotes()).anyMatch(s ->
                s.contains("journal note bodies truncated to 100 chars (5 entries affected)"));
    }

    @Test
    void escalatesProgressivelyUntilBudgetIsMet() {
        // Each note ~5000 chars; 30 of them ⇒ ~155 KB. The soft tier (tail=30, chars=5000)
        // cannot fit budget=20_000, so the compressor must walk down to the medium tier.
        var props = new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(20_000, 30, 10_000, 5_000, 5));
        var compression = TestCompression.issueCompression(props);
        var journals = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> new Journal(i, new Ref(1, "u"),
                        "y".repeat(5_000),
                        "2026-01-01T00:00:00Z", List.of()))
                .toList();
        var issue = stubIssue(journals, List.of());

        var result = compression.compress(issue);

        // Medium tier: tail=20, chars=2500. Total ~50 KB at this point — still too big,
        // so the hard tier (tail=10, chars=1000) finishes the job.
        assertThat(result.journals()).hasSizeLessThanOrEqualTo(20);
        assertThat(result.compressionNotes())
                .anyMatch(s -> s.contains("journal note bodies truncated to 2500 chars"));
    }

    @Test
    void truncationStepIsIdempotentAndKeepsOriginalSize() {
        // Same step applied twice (5000 then 1000) must keep the original length in the marker.
        var step1 = new JournalNoteContentTruncateStep(5_000);
        var step2 = new JournalNoteContentTruncateStep(1_000);
        var journals = List.of(new Journal(1, new Ref(1, "u"),
                "z".repeat(8_000), "2026-01-01T00:00:00Z", List.of()));
        var issue = stubIssue(journals, List.of());

        var afterFirst = step1.apply(issue).orElseThrow().value();
        var afterSecond = step2.apply(afterFirst).orElseThrow().value();
        String body = afterSecond.journals().getFirst().notes();

        assertThat(body).startsWith("z".repeat(1_000));
        assertThat(body)
                .contains("truncated by response compressor")
                .endsWith("total: 8000 chars)");
        assertThat(body).hasSize(1_000 + "... (truncated by response compressor; total: 8000 chars)".length());
    }

    @Test
    void doesNotTruncateJournalNotesWhenWithinBudget() {
        var props = propsWithBudget(100_000);
        var compression = TestCompression.issueCompression(props);
        var journals = List.of(new Journal(1, new Ref(1, "u"),
                "x".repeat(50_000), "2026-01-01T00:00:00Z", List.of()));
        var issue = stubIssue(journals, List.of());

        var result = compression.compress(issue);

        assertThat(result.journals().getFirst().notes()).hasSize(50_000);
        assertThat(result.compressionNotes()).isNull();
    }

    @Test
    void reviewProfileKeepsRevisionsAndHumanNotes() {
        var props = propsWithBudget(100_000);
        var compression = TestCompression.issueCompression(props);
        var journals = List.of(
                new Journal(1, new Ref(1, "u"), "Useful note",
                        "2026-01-01T00:00:00Z",
                        List.of(new Detail("attr", "status_id", "1", "2"))),
                new Journal(2, new Ref(1, "u"), "",
                        "2026-01-02T00:00:00Z",
                        List.of(new Detail("attachment", "10", null, "file.txt")))
        );
        var changesets = List.of(
                new Changeset("abc123", new Ref(1, "u"), "Subject\nBody",
                        "2026-01-03T00:00:00Z", Changeset.SOURCE_REDMINE),
                new Changeset("def456", new Ref(2, "v"), "Other",
                        "2026-01-04T00:00:00Z", Changeset.SOURCE_COMMENT_REFERENCE)
        );
        var issue = stubIssue(journals, changesets);

        var result = compression.compress(issue, CompressionOptions.fromProfile("review"));

        assertThat(result.changesets()).hasSize(2);
        assertThat(result.changesets()).extracting(Changeset::revision)
                .containsExactly("abc123", "def456");
        assertThat(result.changesets()).allSatisfy(changeset -> {
            assertThat(changeset.user()).isNull();
            assertThat(changeset.comments()).isNull();
            assertThat(changeset.committedOn()).isNull();
            assertThat(changeset.source()).isNull();
        });
        assertThat(result.journals()).hasSize(1);
        assertThat(result.journals().getFirst().notes()).isEqualTo("Useful note");
        assertThat(result.journals().getFirst().details()).isNull();
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("review profile kept all 2 changeset revisions"));
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("kept 1 journal notes of 2"));
    }

    private static RedmineMcpProperties propsWithBudget(int budget) {
        return new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(budget, 30, 10_000, 10_000, 5));
    }

    private static Issue stubIssue(List<Journal> journals, List<Changeset> changesets) {
        return new Issue(1, new Ref(1, "p"), new Ref(1, "t"), new Ref(1, "s"), new Ref(1, "pr"),
                null, null, null, null,
                "subject", "desc", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.copyOf(journals),
                null, List.copyOf(changesets), null);
    }
}
