package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ChangesetsRevisionOnlyStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ChangesetCommentsFirstLineStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.JournalNoteContentTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.JournalsReviewStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.JournalsTailKeepStep;

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
        return compress(issue, CompressionOptions.defaults());
    }

    public Issue compress(Issue issue, CompressionOptions options) {
        if (issue == null) {
            return null;
        }
        var actualOptions = options != null ? options : CompressionOptions.defaults();
        var profiled = CompressionSupport.applySteps(issue, buildProfileSteps(actualOptions.profile()));
        var result = compressor.fit(profiled.value(), buildBudgetSteps(), properties.response().maxChars());
        var notes = CompressionSupport.concatNotes(profiled.notes(), result.notes());
        if (notes.isEmpty()) {
            return result.value();
        }
        return result.value().withCompressionNotes(notes);
    }

    List<CompressionStep<Issue>> buildProfileSteps(ResponseProfile profile) {
        if (profile != null && profile.appliesProfileSteps()) {
            return List.of(
                    new ChangesetsRevisionOnlyStep(),
                    new JournalsReviewStep()
            );
        }
        return List.of();
    }

    List<CompressionStep<Issue>> buildBudgetSteps() {
        int tail = properties.response().journalTailKeep();
        int chars = properties.response().journalNoteChars();
        return List.of(
                new ChangesetCommentsFirstLineStep(),
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
