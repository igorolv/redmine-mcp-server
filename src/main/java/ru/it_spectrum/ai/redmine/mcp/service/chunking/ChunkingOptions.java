package ru.it_spectrum.ai.redmine.mcp.service.chunking;

public record ChunkingOptions(int chunkSize, int overlap) {

    public static final int DEFAULT_CHUNK_SIZE = 12_000;
    public static final int MIN_CHUNK_SIZE = 2_000;
    public static final int MAX_CHUNK_SIZE = 20_000;
    public static final int DEFAULT_OVERLAP = 1_200;

    public static ChunkingOptions defaults() {
        return new ChunkingOptions(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public static ChunkingOptions ofChunkSize(Integer chunkSize) {
        int size = chunkSize == null
                ? DEFAULT_CHUNK_SIZE
                : Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, chunkSize));
        return new ChunkingOptions(size, DEFAULT_OVERLAP);
    }
}
