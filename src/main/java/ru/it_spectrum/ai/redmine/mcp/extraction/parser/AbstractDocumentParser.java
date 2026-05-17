package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;
import ru.it_spectrum.ai.redmine.mcp.extraction.TextNormalizer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Shared scaffolding for format-specific text parsers (PDF, DOCX, XLSX, PPTX).
 * Subclass returns extracted text from a stream; the base class handles
 * normalization, error wrapping (mirrors the legacy
 * {@code "(failed to extract text: %s)"} fallback) and Part emission.
 */
public abstract class AbstractDocumentParser implements DocumentParser {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Detected extraction type, e.g. "pdf", "docx". */
    protected abstract String extractionType();

    /** Extract text from an open input stream. Implementations must not close the stream. */
    protected abstract String extractText(InputStream in) throws Exception;

    @Override
    public void parse(ParseInput input, ParseSink sink) {
        String text;
        long size;
        try {
            size = Files.size(input.file());
        } catch (IOException e) {
            log.warn("Failed to stat {}: {}", input.file(), e.getMessage());
            return;
        }

        try (InputStream is = Files.newInputStream(input.file())) {
            text = extractText(is);
        } catch (Exception e) {
            log.warn("Failed to extract text from {}: {}", input.logicalName(), e.getMessage());
            text = "(failed to extract text: %s)".formatted(e.getMessage());
        }

        sink.emit(new ExtractedPart(
                input.emitName(),
                null,
                extractionType(),
                null,
                size,
                TextNormalizer.normalize(text),
                input.file().toString(),
                input.file().toUri().toString(),
                null
        ));
    }
}
