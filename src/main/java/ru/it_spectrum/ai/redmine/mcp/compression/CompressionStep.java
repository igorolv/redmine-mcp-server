package ru.it_spectrum.ai.redmine.mcp.compression;

import java.util.Optional;

/**
 * Single transformation that can shrink a serialized response.
 * <p>
 * Steps are applied by {@link ResponseCompressor} in registration order, with
 * a size check between each step. Each step decides on its own whether there
 * is anything to compress in the given value.
 */
public interface CompressionStep<T> {

    /** Stable identifier used in compression notes. */
    String name();

    /**
     * Apply the compression to {@code value}.
     *
     * @return new compressed value when the step had something to do,
     *         {@link Optional#empty()} when the value was already in its
     *         compressed form (nothing to do).
     */
    Optional<Compressed<T>> apply(T value);

    /** Pairs the compressed value with a human-readable note describing what was done. */
    record Compressed<T>(T value, String note) {
    }
}
