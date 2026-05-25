package ru.it_spectrum.ai.redmine.mcp.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.IssueFullContext;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.AttachmentImagePartsCollapseStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.AttachmentTextPartsTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.ContextIssueJournalsOmitStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.ContextIssuesIssueStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.ChangesetsRevisionOnlyStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.ChangesetCommentsFirstLineStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.InnerIssueStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalDetailsOmitStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalNoteContentTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalsReviewStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.JournalsTailKeepStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.RecentNoteContentTruncateStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.RecentNotesDetailsOmitStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.RecentNotesReviewStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.RecentNotesTailKeepStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies {@link IssueFullContext}-level compression. Peripheral context
 * (attachments, contextIssues, recentNotes) is compressed first; the inner
 * {@link IssueFullContext#issue()} — the actual subject of the request — is
 * only touched once everything else has been thinned, by delegating to
 * {@link IssueCompression}'s budget pipeline.
 */
@Service
public class IssueFullContextCompression {

    private final ResponseCompressor compressor;
    private final RedmineMcpProperties properties;
    private final IssueCompression issueCompression;

    public IssueFullContextCompression(ResponseCompressor compressor,
                                       RedmineMcpProperties properties,
                                       IssueCompression issueCompression) {
        this.compressor = compressor;
        this.properties = properties;
        this.issueCompression = issueCompression;
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
        var steps = new ArrayList<CompressionStep<IssueFullContext>>();
        // Periphery first: attachments, contextIssues, recentNotes — protect the inner Issue.
        steps.add(new AttachmentImagePartsCollapseStep(response.imagePartsKeep()));
        steps.add(new ContextIssuesIssueStep(new ChangesetCommentsFirstLineStep()));
        steps.add(new AttachmentTextPartsTruncateStep(response.attachmentTextPartChars()));
        // Details are far cheaper to lose than human notes — drop them across periphery first.
        steps.add(new ContextIssuesIssueStep(new JournalDetailsOmitStep()));
        steps.add(new RecentNotesDetailsOmitStep());
        steps.add(new ContextIssuesIssueStep(new JournalsReviewStep()));
        steps.add(new ContextIssuesIssueStep(new JournalsTailKeepStep(response.journalTailKeep())));
        steps.add(new ContextIssuesIssueStep(new JournalNoteContentTruncateStep(response.journalNoteChars())));
        steps.add(new ContextIssueJournalsOmitStep());
        steps.add(new RecentNotesTailKeepStep(response.recentNotesTailKeep()));
        steps.add(new RecentNoteContentTruncateStep(response.recentNoteChars()));
        // Last resort: compress the inner Issue itself by reusing the Issue-level pipeline.
        for (var step : issueCompression.buildBudgetSteps()) {
            steps.add(new InnerIssueStep(step));
        }
        return List.copyOf(steps);
    }
}
