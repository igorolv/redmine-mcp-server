package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.api.ContextAttachment;
import ru.it_spectrum.ai.redmine.mcp.api.ContextIssue;
import ru.it_spectrum.ai.redmine.mcp.api.ContextRole;
import ru.it_spectrum.ai.redmine.mcp.api.ContextStats;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionOptions;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IssueFullContextCompressionTest {

    @Test
    void noNotesWhenWithinBudget() {
        var props = props(100_000, 5);
        var compression = TestCompression.contextCompression(props);
        var ctx = contextWith(null, List.of(), List.of());

        var result = compression.compress(ctx);

        assertThat(result.compressionNotes()).isNull();
    }

    @Test
    void collapsesImagePartsFirst() {
        var props = props(800, 2);
        var compression = TestCompression.contextCompression(props);
        var parts = new ArrayList<AttachmentContent.Part>();
        IntStream.range(0, 30).forEach(i -> parts.add(imagePart("img" + i)));
        var ctx = contextWith(parts, List.of(), List.of());

        var result = compression.compress(ctx);

        assertThat(result.compressionNotes()).isNotNull();
        assertThat(result.compressionNotes().get(0)).contains("collapsed");
        var newParts = result.attachments().get(0).content().parts();
        assertThat(newParts).hasSize(3);
        assertThat(newParts.get(2).name()).contains("28 more image parts collapsed");
    }

    @Test
    void reviewProfileAppliesToInnerIssueAndRecentNotes() {
        var props = props(100_000, 5);
        var compression = TestCompression.contextCompression(props);
        var journals = List.of(
                new Issue.Journal(1, new Ref(1, "u"), "Issue note", "2026-01-01T00:00:00Z",
                        List.of(new Issue.Detail("attr", "status_id", "1", "2"))),
                new Issue.Journal(2, new Ref(1, "u"), "", "2026-01-02T00:00:00Z",
                        List.of(new Issue.Detail("attachment", "10", null, "file.txt")))
        );
        var recentNotes = List.of(
                new Issue.Journal(3, new Ref(2, "v"), "Recent note", "2026-01-03T00:00:00Z",
                        List.of(new Issue.Detail("attr", "subject", "old", "new")))
        );
        var ctx = contextWith(null, journals, recentNotes)
                .withIssue(contextWith(null, journals, recentNotes).issue().withChangesets(List.of(
                        new Issue.Changeset("abc123", new Ref(1, "u"), "Message",
                                "2026-01-04T00:00:00Z", Issue.Changeset.SOURCE_REDMINE))));

        var result = compression.compress(ctx, CompressionOptions.fromProfile("review"));

        assertThat(result.issue().changesets().getFirst().revision()).isEqualTo("abc123");
        assertThat(result.issue().changesets().getFirst().comments()).isNull();
        assertThat(result.issue().changesets().getFirst().source()).isNull();
        assertThat(result.issue().journals()).hasSize(1);
        assertThat(result.issue().journals().getFirst().details()).isNull();
        assertThat(result.recentNotes()).hasSize(1);
        assertThat(result.recentNotes().getFirst().details()).isNull();
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("changeset revisions"));
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("recent notes"));
    }

    @Test
    void reviewProfileOmitsContextIssueJournals() {
        var props = props(100_000, 5);
        var compression = TestCompression.contextCompression(props);
        var contextJournals = List.of(
                new Issue.Journal(10, new Ref(1, "u"), "Large context note", "2026-01-01T00:00:00Z",
                        List.of(new Issue.Detail("attr", "description", "x".repeat(1000), "y".repeat(1000)))),
                new Issue.Journal(11, new Ref(1, "u"), "", "2026-01-02T00:00:00Z",
                        List.of(new Issue.Detail("attr", "status_id", "1", "2")))
        );
        var contextChangesets = List.of(new Issue.Changeset("ctx123", new Ref(1, "u"),
                "Message", "2026-01-03T00:00:00Z", Issue.Changeset.SOURCE_REDMINE));
        var ctx = contextWith(null, List.of(), List.of(), List.of(
                contextIssue(2, contextJournals, contextChangesets)
        ));

        var result = compression.compress(ctx, CompressionOptions.fromProfile("review"));
        var contextIssue = result.contextIssues().getFirst().issue();

        assertThat(contextIssue.journals()).isNull();
        assertThat(contextIssue.changesets()).hasSize(1);
        assertThat(contextIssue.changesets().getFirst().revision()).isEqualTo("ctx123");
        assertThat(contextIssue.changesets().getFirst().source()).isNull();
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("context issue payloads"));
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("omitted 2 journal entries"));
    }

    @Test
    void budgetCompressionOmitsContextIssueJournalsBeforeMainIssueJournals() {
        var props = props(4_000, 5);
        var compression = TestCompression.contextCompression(props);
        var mainJournals = List.of(new Issue.Journal(1, new Ref(1, "u"), "Main issue note",
                "2026-01-01T00:00:00Z", List.of()));
        var contextJournals = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new Issue.Journal(i, new Ref(1, "u"), "x".repeat(5_000),
                        "2026-01-01T00:00:00Z", List.of()))
                .toList();
        var ctx = contextWith(null, mainJournals, List.of(), List.of(
                contextIssue(2, contextJournals, List.of())
        ));

        var result = compression.compress(ctx);

        assertThat(result.contextIssues().getFirst().issue().journals()).isNull();
        assertThat(result.issue().journals()).hasSize(1);
        assertThat(result.issue().journals().getFirst().notes()).isEqualTo("Main issue note");
        assertThat(result.compressionNotes()).anyMatch(s -> s.contains("context issue payloads"));
    }

    @Test
    void budgetDropsDetailsBeforeTouchingNoteBodies() {
        // Budget tight enough to force into note-truncation; details must be gone first
        // both on the inner issue and on recentNotes.
        var props = new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(5000, 10, 10, 10_000, 200, 200, 5));
        var compression = TestCompression.contextCompression(props);
        var heavyDetails = List.of(
                new Issue.Detail("attr", "status_id", "1", "2"),
                new Issue.Detail("attr", "assigned_to_id", "100", "200"));
        var journals = IntStream.rangeClosed(1, 4)
                .mapToObj(i -> new Issue.Journal(i, new Ref(1, "u"),
                        "x".repeat(800),
                        "2026-01-0%dT00:00:00Z".formatted(i), heavyDetails))
                .toList();
        var recentNotes = IntStream.rangeClosed(1, 4)
                .mapToObj(i -> new Issue.Journal(100 + i, new Ref(1, "u"),
                        "y".repeat(800),
                        "2026-01-0%dT00:00:00Z".formatted(i), heavyDetails))
                .toList();
        var ctx = contextWith(null, journals, recentNotes);

        var result = compression.compress(ctx);

        assertThat(result.issue().journals()).allSatisfy(j -> assertThat(j.details()).isNull());
        assertThat(result.recentNotes()).allSatisfy(j -> assertThat(j.details()).isNull());
        assertThat(result.compressionNotes())
                .anyMatch(s -> s.contains("omitted field-change details from") && s.contains("journal entries"))
                .anyMatch(s -> s.contains("omitted field-change details from") && s.contains("recent-note entries"));
    }

    private static RedmineMcpProperties props(int budget, int imagePartsKeep) {
        return new RedmineMcpProperties(null, null, null, null, null, null, null,
                new RedmineMcpProperties.Response(budget, 30, 20, 10_000, 10_000, 10_000, imagePartsKeep));
    }

    private static AttachmentContent.Part imagePart(String name) {
        return new AttachmentContent.Part(name, null, "image", "ImageParser",
                false, false, null, "/tmp/" + name, "file:///tmp/" + name, null, null);
    }

    private static IssueFullContext contextWith(List<AttachmentContent.Part> parts,
                                                List<Issue.Journal> journals,
                                                List<Issue.Journal> recentNotes) {
        return contextWith(parts, journals, recentNotes, List.of());
    }

    private static IssueFullContext contextWith(List<AttachmentContent.Part> parts,
                                                List<Issue.Journal> journals,
                                                List<Issue.Journal> recentNotes,
                                                List<ContextIssue> contextIssues) {
        var attachments = parts == null ? List.<ContextAttachment>of()
                : List.of(new ContextAttachment("issue", 1,
                        new AttachmentContent(new Attachment(1, "f", 0, null, null, null, null, null),
                                "/tmp/f", "file:///tmp/f", 0, "image", true, false,
                                List.copyOf(parts), null)));
        var issue = new Issue(1, new Ref(1, "p"), new Ref(1, "t"), new Ref(1, "s"), new Ref(1, "pr"),
                null, null, null, null, null,
                "subject", "desc", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.copyOf(journals), List.of(), List.of(), null, List.of(), null);
        return new IssueFullContext(issue, null, List.copyOf(contextIssues), attachments, List.copyOf(recentNotes),
                new ContextStats(false, false, false), null);
    }

    private static ContextIssue contextIssue(int id, List<Issue.Journal> journals,
                                             List<Issue.Changeset> changesets) {
        var issue = new Issue(id, new Ref(1, "p"), new Ref(1, "t"), new Ref(1, "s"), new Ref(1, "pr"),
                null, null, null, null, null,
                "context subject", "context desc", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.copyOf(journals), List.of(), List.of(),
                null, List.copyOf(changesets), null);
        return new ContextIssue(issue, List.of(new ContextRole(ContextRole.Kind.RELATED, "relates", 1, 1, id, null)));
    }
}
