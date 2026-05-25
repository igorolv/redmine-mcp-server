package ru.it_spectrum.ai.redmine.mcp.service.compression.steps;

import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.service.compression.CompressionStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Caps the length of each remaining journal note's free-text body.
 * Used as a last-resort budget step after older history has already been dropped:
 * we'd rather thin out the tail than mutilate individual notes.
 *
 * <p>The step is idempotent: re-applying with a smaller {@code maxChars} re-truncates
 * the already-truncated body while preserving the original size recorded in the marker.</p>
 */
public class JournalNoteContentTruncateStep implements CompressionStep<Issue> {

    private static final String TRUNCATION_SUFFIX_PREFIX = "... (truncated by response compressor; total: ";
    private static final String TRUNCATION_SUFFIX_TAIL = " chars)";
    private static final Pattern TRUNCATION_MARKER = Pattern.compile(
            Pattern.quote(TRUNCATION_SUFFIX_PREFIX) + "(\\d+)" + Pattern.quote(TRUNCATION_SUFFIX_TAIL) + "$");

    private final int maxChars;

    public JournalNoteContentTruncateStep(int maxChars) {
        this.maxChars = Math.max(0, maxChars);
    }

    @Override
    public String name() {
        return "journal-note-content-truncate";
    }

    @Override
    public Optional<Compressed<Issue>> apply(Issue value) {
        if (value == null || value.journals() == null || value.journals().isEmpty()) {
            return Optional.empty();
        }
        boolean changed = false;
        int truncatedCount = 0;
        var newJournals = new ArrayList<Issue.Journal>(value.journals().size());
        for (var j : value.journals()) {
            String text = j.notes();
            if (text == null || text.length() <= maxChars) {
                newJournals.add(j);
                continue;
            }
            int originalLen;
            String body;
            Matcher matcher = TRUNCATION_MARKER.matcher(text);
            if (matcher.find()) {
                originalLen = Integer.parseInt(matcher.group(1));
                body = text.substring(0, matcher.start());
            } else {
                originalLen = text.length();
                body = text;
            }
            if (body.length() <= maxChars) {
                newJournals.add(j);
                continue;
            }
            changed = true;
            truncatedCount++;
            String shortened = body.substring(0, maxChars)
                    + TRUNCATION_SUFFIX_PREFIX + originalLen + TRUNCATION_SUFFIX_TAIL;
            newJournals.add(new Issue.Journal(j.id(), j.user(), shortened, j.createdOn(), j.details()));
        }
        if (!changed) {
            return Optional.empty();
        }
        String note = "journal note bodies truncated to %d chars (%d entries affected)"
                .formatted(maxChars, truncatedCount);
        return Optional.of(new Compressed<>(value.withJournals(List.copyOf(newJournals)), note));
    }
}
