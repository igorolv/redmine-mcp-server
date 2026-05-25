package ru.it_spectrum.ai.redmine.mcp.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.compression.CompressionStep;

import java.util.List;
import java.util.Optional;

/**
 * Keeps each changeset's revision/author/date but trims the {@code comments}
 * field down to its first line. The full body is recoverable from the VCS.
 */
public class ChangesetCommentsFirstLineStep implements CompressionStep<Issue> {

    @Override
    public String name() {
        return "changeset-comments-first-line";
    }

    @Override
    public Optional<Compressed<Issue>> apply(Issue value) {
        if (value == null || value.changesets() == null || value.changesets().isEmpty()) {
            return Optional.empty();
        }
        var trimmed = new java.util.ArrayList<Issue.Changeset>(value.changesets().size());
        int trimmedCount = 0;
        for (var cs : value.changesets()) {
            String original = cs.comments();
            String firstLine = firstLine(original);
            if (firstLine == null || firstLine.equals(original)) {
                trimmed.add(cs);
            } else {
                trimmed.add(new Issue.Changeset(cs.revision(), cs.user(), firstLine,
                        cs.committedOn(), cs.source()));
                trimmedCount++;
            }
        }
        if (trimmedCount == 0) {
            return Optional.empty();
        }
        String note = "changeset commit messages trimmed to first line for %d of %d entries; full messages available via VCS"
                .formatted(trimmedCount, value.changesets().size());
        return Optional.of(new Compressed<>(value.withChangesets(List.copyOf(trimmed)), note));
    }

    private static String firstLine(String text) {
        if (text == null) return null;
        int idx = text.indexOf('\n');
        if (idx < 0) {
            return text;
        }
        String head = text.substring(0, idx);
        if (!head.isEmpty() && head.charAt(head.length() - 1) == '\r') {
            head = head.substring(0, head.length() - 1);
        }
        return head;
    }
}
