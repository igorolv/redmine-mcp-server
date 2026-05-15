package ru.it_spectrum.ai.redmine.mcp.service.chunking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FixedSizeTextChunker implements ChunkingStrategy {

    @Override
    public List<TextChunk> split(String text, ChunkingOptions options) {
        if (text.isBlank()) {
            return List.of(new TextChunk(0, 0, ""));
        }

        int chunkSize = options.chunkSize();
        int overlap = options.overlap();
        var chunks = new ArrayList<TextChunk>();
        int start = 0;

        while (start < text.length()) {
            int preferredEnd = Math.min(start + chunkSize, text.length());
            int end = findChunkBoundary(text, start, preferredEnd);
            if (end <= start) {
                end = preferredEnd;
            }

            String rawChunk = text.substring(start, end);
            String chunkText = rawChunk.strip();
            if (!chunkText.isEmpty()) {
                int leadingTrim = 0;
                while (leadingTrim < rawChunk.length() && Character.isWhitespace(rawChunk.charAt(leadingTrim))) {
                    leadingTrim++;
                }

                int trailingTrim = 0;
                while (trailingTrim < rawChunk.length() - leadingTrim
                        && Character.isWhitespace(rawChunk.charAt(rawChunk.length() - 1 - trailingTrim))) {
                    trailingTrim++;
                }

                chunks.add(new TextChunk(
                        start + leadingTrim,
                        end - trailingTrim,
                        chunkText
                ));
            }

            if (end >= text.length()) {
                break;
            }

            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    private int findChunkBoundary(String text, int start, int preferredEnd) {
        if (preferredEnd >= text.length()) {
            return text.length();
        }

        int paragraphBreak = text.lastIndexOf("\n\n", preferredEnd);
        if (paragraphBreak > start + 1000) {
            return paragraphBreak;
        }

        int lineBreak = text.lastIndexOf('\n', preferredEnd);
        if (lineBreak > start + 500) {
            return lineBreak;
        }

        int sentenceBreak = Math.max(text.lastIndexOf(". ", preferredEnd), text.lastIndexOf("! ", preferredEnd));
        sentenceBreak = Math.max(sentenceBreak, text.lastIndexOf("? ", preferredEnd));
        if (sentenceBreak > start + 500) {
            return sentenceBreak + 1;
        }

        return preferredEnd;
    }
}
