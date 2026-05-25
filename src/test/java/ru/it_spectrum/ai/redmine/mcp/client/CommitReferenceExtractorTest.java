package ru.it_spectrum.ai.redmine.mcp.client;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommitReferenceExtractorTest {

    @Test
    void extractsHookStyleReference() {
        var journal = note(2, "Аноним", "2026-05-15T14:05:42Z",
                "Commit referenced this issue: @4a900e11@\n\n"
                        + "http://asvgit.example.com:3000/SPEKTR/sskv-jws/commit/"
                        + "4a900e11f6cacbc4b92b1662f73e3d263d5e5d9b\n\n"
                        + "> #4412. Общие функции. Обнови формат сведений ЕГРЮЛ");

        var result = CommitReferenceExtractor.extractFromJournals(List.of(journal), List.of());

        assertThat(result).hasSize(1);
        var c = result.getFirst();
        assertThat(c.revision()).isEqualTo("4a900e11f6cacbc4b92b1662f73e3d263d5e5d9b");
        assertThat(c.source()).isEqualTo(Issue.Changeset.SOURCE_COMMENT_REFERENCE);
        assertThat(c.user().id()).isEqualTo(2);
        assertThat(c.committedOn()).isEqualTo("2026-05-15T14:05:42Z");
        assertThat(c.comments()).isNull();
    }

    @Test
    void extractsManualFreeFormReference() {
        var journal = note(56, "Igor Olvovsky", "2026-05-15T14:32:43Z",
                "Заглушка soapui:\n\n"
                        + "http://asvgit.example.com:3000/SPEKTR/sskv-soapUI-projects/commit/"
                        + "fec9f0096db623de9c22507687d6258798ac640c\n\n"
                        + "Some prose afterwards.");

        var result = CommitReferenceExtractor.extractFromJournals(List.of(journal), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().revision()).isEqualTo("fec9f0096db623de9c22507687d6258798ac640c");
    }

    @Test
    void deduplicatesAgainstExistingRedmineChangesetByPrefix() {
        var existing = List.of(new Issue.Changeset(
                "4a900e11f6cacbc4b92b1662f73e3d263d5e5d9b", null, "msg", "2026-05-15T14:00:00Z",
                Issue.Changeset.SOURCE_REDMINE));
        var journal = note(2, "Аноним", "2026-05-15T14:05:42Z",
                "http://example.com/repo/commit/4a900e11");

        var result = CommitReferenceExtractor.extractFromJournals(List.of(journal), existing);

        assertThat(result).isEmpty();
    }

    @Test
    void deduplicatesAcrossJournalsAndKeepsLongest() {
        var shortFirst = note(2, "Аноним", "2026-05-15T14:00:00Z",
                "http://example.com/r/commit/abcdef1");
        var longSecond = note(2, "Аноним", "2026-05-15T15:00:00Z",
                "http://example.com/r/commit/abcdef1234567890abcdef1234567890abcdef12");

        var result = CommitReferenceExtractor.extractFromJournals(
                List.of(shortFirst, longSecond), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().revision())
                .isEqualTo("abcdef1234567890abcdef1234567890abcdef12");
    }

    @Test
    void extractsGitLabDashCommitUrl() {
        var journal = note(56, "Dev", "2026-04-01T10:00:00Z",
                "Pushed: https://gitlab.example.com/group/proj/-/commit/0123456789abcdef");

        var result = CommitReferenceExtractor.extractFromJournals(List.of(journal), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().revision()).isEqualTo("0123456789abcdef");
    }

    @Test
    void extractsSvnNumericRevision() {
        var journal = note(56, "Dev", "2026-04-01T10:00:00Z",
                "See http://redmine.example.com/projects/foo/repository/revisions/12345");

        var result = CommitReferenceExtractor.extractFromJournals(List.of(journal), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().revision()).isEqualTo("12345");
    }

    @Test
    void ignoresNotesWithoutCommitUrls() {
        var journal = note(56, "Dev", "2026-04-01T10:00:00Z",
                "Просто комментарий без ссылок. Refs https://example.com/issues/42");

        var result = CommitReferenceExtractor.extractFromJournals(List.of(journal), List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresShortHexFalsePositives() {
        var journal = note(56, "Dev", "2026-04-01T10:00:00Z",
                "http://x/commit/ab");

        var result = CommitReferenceExtractor.extractFromJournals(List.of(journal), List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void handlesNullJournals() {
        assertThat(CommitReferenceExtractor.extractFromJournals(null, List.of())).isEmpty();
    }

    private static RedmineIssue.Journal note(int userId, String userName, String createdOn, String text) {
        return new RedmineIssue.Journal(
                100 + userId,
                new IdName(userId, userName),
                text,
                createdOn,
                List.of());
    }
}
