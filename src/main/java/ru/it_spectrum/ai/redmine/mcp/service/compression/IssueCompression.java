package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.ChangesetCommentsFirstLineStep;
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
        if (issue == null) {
            return null;
        }
        var steps = buildSteps();
        var result = compressor.fit(issue, steps, properties.response().maxChars());
        if (result.notes().isEmpty()) {
            return result.value();
        }
        return result.value().withCompressionNotes(result.notes());
    }

    List<CompressionStep<Issue>> buildSteps() {
        return List.of(
                new ChangesetCommentsFirstLineStep(),
                new JournalsTailKeepStep(properties.response().journalTailKeep())
        );
    }
}
