package ru.it_spectrum.ai.redmine.mcp.extraction;

import java.nio.file.Path;

/**
 * Output channel for a {@link DocumentParser}. {@link #emit} produces a final part for the LLM;
 * {@link #processNow} runs a derived child file through the pipeline immediately, in-line,
 * so its parts land in the collector in iteration order (e.g. ZIP entries appear in the same
 * order they were written to the archive).
 *
 * <p>{@link #hasTextPart()} and {@link #emittedCount()} report on the <em>current input</em>,
 * letting FALLBACK parsers skip themselves when a PRIMARY parser already handled the file.</p>
 */
public interface ParseSink {

    void emit(ExtractedPart part);

    void processNow(Path childFile, String childLogicalName, String contentType);

    boolean hasTextPart();

    int emittedCount();

    /**
     * True if any PRIMARY parser was applicable to this input (whether or not it emitted Parts).
     * FALLBACK parsers use this to skip themselves when the input was already recognised by a
     * format-specific parser — e.g. a ZIP whose entries are processed recursively but which
     * does not emit a Part for the archive itself.
     */
    boolean primaryApplied();
}
