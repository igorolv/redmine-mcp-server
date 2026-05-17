package ru.it_spectrum.ai.redmine.mcp.extraction;

/**
 * Shared text post-processing for extracted text. Mirrors the legacy
 * {@code DocumentTextExtractor.normalizeExtractedText} so parser output stays byte-stable.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\t\\x0B\\f]+", " ");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.strip();
    }
}
