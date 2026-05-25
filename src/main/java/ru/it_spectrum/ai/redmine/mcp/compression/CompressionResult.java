package ru.it_spectrum.ai.redmine.mcp.compression;

import java.util.List;

/**
 * Outcome of a {@link ResponseCompressor#fit} run: the (possibly compressed)
 * value, the final serialized size in characters and the human-readable notes
 * collected from each applied step.
 */
public record CompressionResult<T>(T value, int sizeChars, List<String> notes, boolean overBudget) {
}
