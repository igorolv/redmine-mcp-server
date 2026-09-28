package ru.it_spectrum.ai.redmine.mcp.extraction;

/**
 * One pluggable extractor in the pipeline. Independent of other parsers — multiple
 * applicable parsers run against the same input (pandoc markdown + media extraction
 * for a single DOCX, for example). A parser may consult {@link ParseSink#hasTextPart()} to act as
 * a fallback for an earlier one.
 */
public interface DocumentParser {

    enum Phase {
        /** Format-aware parsers. Run first. */
        PRIMARY,
        /** Default-of-last-resort parsers (Tika fallback, binary stub). Run after PRIMARY. */
        FALLBACK
    }

    default Phase phase() {
        return Phase.PRIMARY;
    }

    boolean applies(ParseInput in);

    void parse(ParseInput in, ParseSink sink);
}
