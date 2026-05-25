package ru.it_spectrum.ai.redmine.mcp.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.ChangesetCommentsFirstLineStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalDetailsOmitStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalNoteContentTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalsTailKeepStep;

import java.util.List;

/**
 * Applies {@link Issue}-level compression steps and embeds the resulting notes
 * into the returned {@link Issue#compressionNotes()} field.
 */
@Service
public class IssueCompression {

    private final ResponseCompressor compressor;
    private final RedmineMcpProperties properties;

    public IssueCompression(ResponseCompressor compressor, RedmineMcpProperties properties) {
        this.compressor = compressor;
        this.properties = properties;
    }

    public Issue compress(Issue issue) {
        if (issue == null) {
            return null;
        }
        var result = compressor.fit(issue, buildBudgetSteps(), properties.response().maxChars());
        if (result.notes().isEmpty()) {
            return result.value();
        }
        return result.value().withCompressionNotes(result.notes());
    }

    List<CompressionStep<Issue>> buildBudgetSteps() {
        int tail = properties.response().journalTailKeep();
        int chars = properties.response().journalNoteChars();
        return List.of(
                new ChangesetCommentsFirstLineStep(),
                // Notes are far more valuable than field-change details: drop details first
                // so that by the time we start thinning or truncating notes, details are gone.
                new JournalDetailsOmitStep(),
                // Soft tier: drop oldest history, then trim only the longest notes.
                new JournalsTailKeepStep(tail),
                new JournalNoteContentTruncateStep(chars),
                // Medium tier.
                new JournalsTailKeepStep(Math.max(1, (tail * 2 + 2) / 3)),
                new JournalNoteContentTruncateStep(Math.max(500, chars / 2)),
                // Hard tier — last resort before giving up.
                new JournalsTailKeepStep(Math.max(1, tail / 3)),
                new JournalNoteContentTruncateStep(Math.max(200, chars / 5))
        );
    }
}
