package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.AttachmentImagePartsCollapseStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.AttachmentTextPartsTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ChangesetCommentsFirstLineStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.InnerIssueStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.JournalsTailKeepStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.RecentNoteContentTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.RecentNotesTailKeepStep;

import java.util.List;

/**
 * Applies {@link IssueFullContext}-level compression. Step order is cheap → costly:
 * image parts (lowest information value) are collapsed first; description-bearing
 * cuts (commit messages, attachment text, recent notes) come later.
 */
@Service
public class IssueFullContextCompression {

    private final ResponseCompressor compressor;
    private final RedmineMcpProperties properties;

    public IssueFullContextCompression(ResponseCompressor compressor, RedmineMcpProperties properties) {
        this.compressor = compressor;
        this.properties = properties;
    }

    public IssueFullContext compress(IssueFullContext context) {
        if (context == null) {
            return null;
        }
        var steps = buildSteps();
        var result = compressor.fit(context, steps, properties.response().maxChars());
        if (result.notes().isEmpty()) {
            return result.value();
        }
        return result.value().withCompressionNotes(result.notes());
    }

    List<CompressionStep<IssueFullContext>> buildSteps() {
        var response = properties.response();
        return List.of(
                new AttachmentImagePartsCollapseStep(response.imagePartsKeep()),
                new InnerIssueStep(new ChangesetCommentsFirstLineStep()),
                new AttachmentTextPartsTruncateStep(response.attachmentTextPartChars()),
                new InnerIssueStep(new JournalsTailKeepStep(response.journalTailKeep())),
                new RecentNotesTailKeepStep(response.recentNotesTailKeep()),
                new RecentNoteContentTruncateStep(response.recentNoteChars())
        );
    }
}
