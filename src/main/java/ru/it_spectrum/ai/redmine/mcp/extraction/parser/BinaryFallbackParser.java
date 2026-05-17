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
 * Final FALLBACK: if no PRIMARY parser produced anything for a nested file (e.g. an image
 * unpacked from a ZIP), emit a stub Part so the LLM can still see the entry. Mirrors the
 * legacy {@code "skipped, not text-extractable"} note used by the old in-archive flow.
 */
@Component
public class BinaryFallbackParser implements DocumentParser {

    private final FileTypeDetector types;

    public BinaryFallbackParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public Phase phase() {
        return Phase.FALLBACK;
    }

    @Override
    public boolean applies(ParseInput in) {
        // Top-level non-text attachments are handled by AttachmentService directly; pipeline
        // is only invoked for "is text-extractable" inputs at depth 0. So this fallback is
        // effectively for nested entries.
        return true;
    }

    @Override
    public void parse(ParseInput in, ParseSink sink) {
        if (sink.emittedCount() > 0 || sink.primaryApplied()) {
            return;
        }
        long size;
        try {
            size = Files.size(in.file());
        } catch (IOException e) {
            size = 0;
        }
        sink.emit(new ExtractedPart(
                in.emitName(),
                types.detectExtractionType(in.logicalName(), in.contentType()),
                size,
                null,
                "skipped, not text-extractable"
        ));
    }
}
