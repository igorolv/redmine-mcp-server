package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.AttachmentImagePartsCollapseStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.AttachmentTextPartsTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ContextIssueJournalsOmitStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ContextIssuesIssueStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ChangesetsRevisionOnlyStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ChangesetCommentsFirstLineStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.InnerIssueStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.JournalNoteContentTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.JournalsReviewStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.JournalsTailKeepStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.RecentNoteContentTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.RecentNotesReviewStep;
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
        return compress(context, CompressionOptions.defaults());
    }

    public IssueFullContext compress(IssueFullContext context, CompressionOptions options) {
        if (context == null) {
            return null;
        }
        var actualOptions = options != null ? options : CompressionOptions.defaults();
        var profiled = CompressionSupport.applySteps(context, buildProfileSteps(actualOptions.profile()));
        var result = compressor.fit(profiled.value(), buildBudgetSteps(), properties.response().maxChars());
        var notes = CompressionSupport.concatNotes(profiled.notes(), result.notes());
        if (notes.isEmpty()) {
            return result.value();
        }
        return result.value().withCompressionNotes(notes);
    }

    List<CompressionStep<IssueFullContext>> buildProfileSteps(ResponseProfile profile) {
        if (profile != null && profile.appliesProfileSteps()) {
            return List.of(
                    new InnerIssueStep(new ChangesetsRevisionOnlyStep()),
                    new InnerIssueStep(new JournalsReviewStep()),
                    new RecentNotesReviewStep(),
                    new ContextIssuesIssueStep(new ChangesetsRevisionOnlyStep()),
                    new ContextIssueJournalsOmitStep()
            );
        }
        return List.of();
    }

    List<CompressionStep<IssueFullContext>> buildBudgetSteps() {
        var response = properties.response();
        return List.of(
                new AttachmentImagePartsCollapseStep(response.imagePartsKeep()),
                new InnerIssueStep(new ChangesetCommentsFirstLineStep()),
                new ContextIssuesIssueStep(new ChangesetCommentsFirstLineStep()),
                new AttachmentTextPartsTruncateStep(response.attachmentTextPartChars()),
                new ContextIssuesIssueStep(new JournalsReviewStep()),
                new ContextIssuesIssueStep(new JournalsTailKeepStep(response.journalTailKeep())),
                new ContextIssuesIssueStep(new JournalNoteContentTruncateStep(response.journalNoteChars())),
                new ContextIssueJournalsOmitStep(),
                new InnerIssueStep(new JournalsTailKeepStep(response.journalTailKeep())),
                new RecentNotesTailKeepStep(response.recentNotesTailKeep()),
                new RecentNoteContentTruncateStep(response.recentNoteChars())
        );
    }
}
