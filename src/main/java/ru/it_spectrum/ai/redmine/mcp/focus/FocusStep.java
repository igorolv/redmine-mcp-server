package ru.it_spectrum.ai.redmine.mcp.focus;

import java.util.Optional;

/**
 * Semantic response shaping step for a concrete caller focus.
 * Unlike budget compression, focus steps run regardless of serialized size.
 */
public interface FocusStep<T> {

    String name();

    Optional<Focused<T>> apply(T value);

    record Focused<T>(T value, String note) {
    }
}
