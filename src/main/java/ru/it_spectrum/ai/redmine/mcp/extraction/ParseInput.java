package ru.it_spectrum.ai.redmine.mcp.extraction;

import java.nio.file.Path;

/**
 * A single file handed to the parser pipeline.
 *
 * <p>{@code logicalName} carries the file's name (for type detection and for use as a Part name
 * when nested). Parsers should call {@link #emitName()} when populating
 * {@code ExtractedPart.name}: the top-level attachment uses {@code null} to preserve the legacy
 * "single root part has no name" convention; nested entries use {@code logicalName}.</p>
 */
public record ParseInput(
        Path file,
        String logicalName,
        String contentType,
        Path workDir,
        int depth,
        ParseContext ctx
) {

    public String emitName() {
        return depth == 0 ? null : logicalName;
    }
}
