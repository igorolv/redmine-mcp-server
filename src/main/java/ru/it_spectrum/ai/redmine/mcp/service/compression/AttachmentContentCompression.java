package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.AttachmentContentImagePartsCollapseStep;
import ru.it_spectrum.ai.redmine.mcp.service.compression.steps.AttachmentContentTextPartsTruncateStep;

import java.util.List;

/**
 * Applies the response-size pipeline to a single {@link AttachmentContent}
 * returned by {@code getAttachment}. Step order is cheap → costly:
 * image parts first, then text-part truncation.
 */
@Service
public class AttachmentContentCompression {

    private final ResponseCompressor compressor;
    private final RedmineMcpProperties properties;

    public AttachmentContentCompression(ResponseCompressor compressor, RedmineMcpProperties properties) {
        this.compressor = compressor;
        this.properties = properties;
    }

    public AttachmentContent compress(AttachmentContent content) {
        if (content == null) {
            return null;
        }
        var steps = buildSteps();
        var result = compressor.fit(content, steps, properties.response().maxChars());
        if (result.notes().isEmpty()) {
            return result.value();
        }
        return result.value().withCompressionNotes(result.notes());
    }

    List<CompressionStep<AttachmentContent>> buildSteps() {
        var response = properties.response();
        return List.of(
                new AttachmentContentImagePartsCollapseStep(response.imagePartsKeep()),
                new AttachmentContentTextPartsTruncateStep(response.attachmentTextPartChars())
        );
    }
}
