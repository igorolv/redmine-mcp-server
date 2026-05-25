package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.ChangesetCommentsFirstLineStep;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangesetCommentsFirstLineStepTest {

    private final ChangesetCommentsFirstLineStep step = new ChangesetCommentsFirstLineStep();

    @Test
    void emptyWhenNoChangesets() {
        var issue = stubIssue(List.of());
        assertThat(step.apply(issue)).isEmpty();
    }

    @Test
    void emptyWhenAllAlreadySingleLine() {
        var issue = stubIssue(List.of(
                changeset("a", "Fix typo"),
                changeset("b", "Bump version")
        ));
        assertThat(step.apply(issue)).isEmpty();
    }

    @Test
    void trimsMultilineComments() {
        var issue = stubIssue(List.of(
                changeset("a", "Fix login bug\n\nDetailed body\nMore details"),
                changeset("b", "Refactor module\r\nlong commit body")
        ));

        var result = step.apply(issue).orElseThrow();
        var trimmed = result.value().changesets();

        assertThat(trimmed).extracting(Issue.Changeset::comments)
                .containsExactly("Fix login bug", "Refactor module");
        assertThat(result.note()).contains("2 of 2");
    }

    @Test
    void preservesRevisionAndMetadata() {
        var issue = stubIssue(List.of(changeset("abc123", "Header\nbody")));
        var result = step.apply(issue).orElseThrow();
        var cs = result.value().changesets().get(0);

        assertThat(cs.revision()).isEqualTo("abc123");
        assertThat(cs.user().name()).isEqualTo("Dev");
        assertThat(cs.source()).isEqualTo(Issue.Changeset.SOURCE_REDMINE);
    }

    private static Issue.Changeset changeset(String revision, String comments) {
        return new Issue.Changeset(revision, new Ref(1, "Dev"), comments,
                "2026-01-01T00:00:00Z", Issue.Changeset.SOURCE_REDMINE);
    }

    private static Issue stubIssue(List<Issue.Changeset> changesets) {
        return new Issue(1, null, null, null, null, null, null, null, null, null,
                "s", "d", null, null, 0, null, null, "t", "t",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.copyOf(changesets), null);
    }
}
