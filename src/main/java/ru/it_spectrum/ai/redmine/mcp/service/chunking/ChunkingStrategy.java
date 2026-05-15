package ru.it_spectrum.ai.redmine.mcp.service.chunking;

import java.util.List;

public interface ChunkingStrategy {

    List<TextChunk> split(String text, ChunkingOptions options);

    default int countChunks(String text, ChunkingOptions options) {
        return split(text, options).size();
    }
}
