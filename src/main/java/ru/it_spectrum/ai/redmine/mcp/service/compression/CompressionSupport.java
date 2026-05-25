package ru.it_spectrum.ai.redmine.mcp.service.compression;

import java.util.ArrayList;
import java.util.List;

final class CompressionSupport {

    private CompressionSupport() {
    }

    static <T> CompressionResult<T> applySteps(T value, List<? extends CompressionStep<T>> steps) {
        T current = value;
        var notes = new ArrayList<String>();
        for (var step : steps) {
            var maybe = step.apply(current);
            if (maybe.isEmpty()) {
                continue;
            }
            current = maybe.get().value();
            notes.add(maybe.get().note());
        }
        return new CompressionResult<>(current, -1, List.copyOf(notes), false);
    }

    static List<String> concatNotes(List<String> first, List<String> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return List.of();
        }
        var notes = new ArrayList<String>();
        if (first != null) {
            notes.addAll(first);
        }
        if (second != null) {
            notes.addAll(second);
        }
        return List.copyOf(notes);
    }
}
