package ru.it_spectrum.ai.redmine.mcp.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class ApiCollections {

    private ApiCollections() {
    }

    static <S, T> List<T> mapNonNull(List<S> source, Function<S, T> mapper) {
        if (source == null) {
            return null;
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(mapper)
                .filter(Objects::nonNull)
                .toList();
    }
}
