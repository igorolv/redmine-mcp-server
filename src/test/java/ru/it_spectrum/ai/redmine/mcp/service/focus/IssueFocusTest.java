package ru.it_spectrum.ai.redmine.mcp.service.focus;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Attachment;
import ru.it_spectrum.ai.redmine.mcp.api.Changeset;
import ru.it_spectrum.ai.redmine.mcp.api.CustomFieldValue;
import ru.it_spectrum.ai.redmine.mcp.api.Detail;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Journal;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.focus.IssueFocus;
import ru.it_spectrum.ai.redmine.mcp.focus.ResponseFocus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IssueFocusTest {

    private final IssueFocus focus = new IssueFocus();

    @Test
    void implementationFocusKeepsRevisionsAndHumanNotes() {
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

        var result = focus.apply(issue, ResponseFocus.IMPLEMENTATION);

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
        assertThat(result.focusNotes()).anyMatch(s -> s.contains("implementation focus kept all 2 changeset revisions"));
        assertThat(result.focusNotes()).anyMatch(s -> s.contains("kept 1 journal notes of 2"));
        assertThat(result.compressionNotes()).isNull();
    }

    @Test
    void timelineFocusKeepsTimelineAndOmitsNeighbouringContext() {
        var issue = stubIssue(List.of(new Journal(1, new Ref(1, "u"), "note",
                        "2026-01-01T00:00:00Z",
                        List.of(new Detail("attr", "status_id", "1", "2")))),
                List.of(new Changeset("abc123", new Ref(1, "u"), "message",
                        "2026-01-01T00:00:00Z", Changeset.SOURCE_REDMINE)))
                .withCustomFields(List.of(new CustomFieldValue("cf", List.of("value"))))
                .withAttachments(List.of(new Attachment(10, "file.txt", 10, "text/plain", null, null,
                        new Ref(1, "u"), "2026-01-01T00:00:00Z")));

        var result = focus.apply(issue, ResponseFocus.TIMELINE);

        assertThat(result.journals()).hasSize(1);
        assertThat(result.journals().getFirst().details()).hasSize(1);
        assertThat(result.changesets()).hasSize(1);
        assertThat(result.attachments()).isNull();
        assertThat(result.customFields()).isNull();
        assertThat(result.focusNotes()).anyMatch(s -> s.contains("timeline focus kept issue core fields"));
    }

    private static Issue stubIssue(List<Journal> journals, List<Changeset> changesets) {
        return new Issue(1, new Ref(1, "p"), new Ref(1, "t"), new Ref(1, "s"), new Ref(1, "pr"),
                null, null, null, null,
                "subject", "desc", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.copyOf(journals),
                null, List.copyOf(changesets), null, null);
    }
}
