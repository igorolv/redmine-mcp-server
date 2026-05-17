package ru.it_spectrum.ai.redmine.mcp.extraction;

/**
 * Single fragment of information about a file, ready to hand to an LLM.
 * Produced by a {@link DocumentParser}; collected by {@link ExtractionPipeline}.
 *
 * <p>{@code parent} and {@code producer} are normally left {@code null} by parsers — the
 * pipeline back-fills them from the current {@link ParseInput} and from the parser that
 * is running. {@code localPath} / {@code fileUri} are set explicitly when the part refers
 * to a concrete file on disk (the source file itself, or an artefact extracted from it).</p>
 */
public record ExtractedPart(
        String name,
        String parent,
        String extractionType,
        String producer,
        Long size,
        String content,
        String localPath,
        String fileUri,
        String note,
        boolean textExtracted
) {

    public ExtractedPart(String name,
                         String parent,
                         String extractionType,
                         String producer,
                         Long size,
                         String content,
                         String localPath,
                         String fileUri,
                         String note) {
        this(name, parent, extractionType, producer, size, content, localPath, fileUri, note, content != null);
    }

    /** Builder-style copy that sets {@code producer} if not already set. */
    public ExtractedPart withProducerIfAbsent(String producer) {
        return this.producer != null ? this : new ExtractedPart(
                name, parent, extractionType, producer, size, content, localPath, fileUri, note, textExtracted);
    }

    /** Builder-style copy that sets {@code parent} if not already set. */
    public ExtractedPart withParentIfAbsent(String parent) {
        return this.parent != null ? this : new ExtractedPart(
                name, parent, extractionType, producer, size, content, localPath, fileUri, note, textExtracted);
    }
}
