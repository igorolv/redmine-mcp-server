package ru.it_spectrum.ai.redmine.mcp.extraction;

/**
 * Single fragment of information about a file, ready to hand to an LLM.
 * Produced by a {@link DocumentParser}; collected by {@link ExtractionPipeline}.
 *
 * <p>Phase 1 mirrors the legacy {@code DocumentTextExtractor.ExtractedTextPart} shape.
 * Additional fields (localPath, fileUri, producer, parent) land in Phase 2.</p>
 */
public record ExtractedPart(
        String name,
        String extractionType,
        Long size,
        String content,
        String note
) {

    public boolean textExtracted() {
        return content != null;
    }
}
