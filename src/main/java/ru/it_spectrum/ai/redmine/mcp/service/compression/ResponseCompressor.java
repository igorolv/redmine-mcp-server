package ru.it_spectrum.ai.redmine.mcp.service.compression;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Iteratively applies {@link CompressionStep}s until the JSON-serialized form
 * of the value fits within the budget, or every step has been tried.
 */
@Service
public class ResponseCompressor {

    private static final Logger log = LoggerFactory.getLogger(ResponseCompressor.class);

    private final ObjectMapper mapper;

    public ResponseCompressor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> CompressionResult<T> fit(T value, List<? extends CompressionStep<T>> steps, int budgetChars) {
        int size = measure(value);
        if (size <= budgetChars) {
            return new CompressionResult<>(value, size, List.of(), false);
        }
        var notes = new ArrayList<String>();
        T current = value;
        for (var step : steps) {
            var maybe = step.apply(current);
            if (maybe.isEmpty()) {
                continue;
            }
            current = maybe.get().value();
            notes.add(maybe.get().note());
            size = measure(current);
            log.debug("Compression step {} applied, size now {} chars", step.name(), size);
            if (size <= budgetChars) {
                return new CompressionResult<>(current, size, List.copyOf(notes), false);
            }
        }
        notes.add("response still exceeds budget of %d chars after all compression steps (current size: %d)"
                .formatted(budgetChars, size));
        return new CompressionResult<>(current, size, List.copyOf(notes), true);
    }

    private int measure(Object value) {
        try {
            return mapper.writeValueAsString(value).length();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize value for size measurement", e);
        }
    }
}
