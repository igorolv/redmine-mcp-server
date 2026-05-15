package ru.it_spectrum.ai.redmine.mcp.service.chunking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeTextChunkerTest {

    private final FixedSizeTextChunker chunker = new FixedSizeTextChunker();

    @Test
    void shouldReturnSingleEmptyChunkForBlankText() {
        var chunks = chunker.split("", ChunkingOptions.defaults());
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).isEmpty();
        assertThat(chunks.getFirst().startChar()).isZero();
        assertThat(chunks.getFirst().endChar()).isZero();
    }

    @Test
    void shouldReturnSingleEmptyChunkForWhitespaceOnly() {
        var chunks = chunker.split("   \n\t  ", ChunkingOptions.defaults());
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).isEmpty();
    }

    @Test
    void shouldReturnSingleChunkForTextShorterThanChunkSize() {
        String text = "Short document content";
        var chunks = chunker.split(text, new ChunkingOptions(1000, 100));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).isEqualTo(text);
        assertThat(chunks.getFirst().endChar()).isEqualTo(text.length());
    }

    @Test
    void shouldSplitLongTextIntoMultipleChunks() {
        String text = "x".repeat(10_000);
        var chunks = chunker.split(text, new ChunkingOptions(2_000, 200));
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(2_000));
    }

    @Test
    void shouldPreferParagraphBoundaryWhenAvailable() {
        String before = "a".repeat(1_500);
        String after = "b".repeat(1_500);
        String text = before + "\n\n" + after;
        var chunks = chunker.split(text, new ChunkingOptions(2_000, 100));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.getFirst().text()).doesNotContain("b");
    }

    @Test
    void shouldOverlapBetweenChunks() {
        String text = "x".repeat(5_000);
        var chunks = chunker.split(text, new ChunkingOptions(2_000, 500));
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        int firstEnd = chunks.get(0).endChar();
        int secondStart = chunks.get(1).startChar();
        assertThat(secondStart).isLessThan(firstEnd);
        assertThat(firstEnd - secondStart).isLessThanOrEqualTo(500);
    }

    @Test
    void countChunksShouldMatchSplitSize() {
        String text = "y".repeat(8_000);
        var options = new ChunkingOptions(2_000, 200);
        assertThat(chunker.countChunks(text, options))
                .isEqualTo(chunker.split(text, options).size());
    }

    @Test
    void defaultsShouldUse12000And1200() {
        var defaults = ChunkingOptions.defaults();
        assertThat(defaults.chunkSize()).isEqualTo(12_000);
        assertThat(defaults.overlap()).isEqualTo(1_200);
    }

    @Test
    void ofChunkSizeShouldClampBelowMinimum() {
        var options = ChunkingOptions.ofChunkSize(500);
        assertThat(options.chunkSize()).isEqualTo(ChunkingOptions.MIN_CHUNK_SIZE);
    }

    @Test
    void ofChunkSizeShouldClampAboveMaximum() {
        var options = ChunkingOptions.ofChunkSize(50_000);
        assertThat(options.chunkSize()).isEqualTo(ChunkingOptions.MAX_CHUNK_SIZE);
    }

    @Test
    void ofChunkSizeShouldUseDefaultForNull() {
        var options = ChunkingOptions.ofChunkSize(null);
        assertThat(options.chunkSize()).isEqualTo(ChunkingOptions.DEFAULT_CHUNK_SIZE);
    }
}
