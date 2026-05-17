package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Emits an "image" Part with a localPath/fileUri reference for image files. Fires for nested
 * images (e.g. an image extracted from a ZIP or a DOCX media folder) — top-level image
 * attachments are gated by {@code AttachmentService} and never reach the pipeline.
 */
@Component
public class ImagePassthroughParser implements DocumentParser {

    private final FileTypeDetector types;

    public ImagePassthroughParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        return types.isImage(in.logicalName(), in.contentType());
    }

    @Override
    public void parse(ParseInput in, ParseSink sink) {
        long size;
        try {
            size = Files.size(in.file());
        } catch (IOException e) {
            size = 0;
        }
        sink.emit(new ExtractedPart(
                in.emitName(),
                null,
                "image",
                null,
                size,
                null,
                in.file().toString(),
                in.file().toUri().toString(),
                "Image file. Use localPath/fileUri to access the original."
        ));
    }
}
