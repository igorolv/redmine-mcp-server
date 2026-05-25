package ru.it_spectrum.ai.redmine.mcp.service.compression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseCompressorTest {

    private final ResponseCompressor compressor = TestCompression.compressor();

    @Test
    void returnsValueUnchangedWhenWithinBudget() {
        var result = compressor.fit("ok", List.of(failingStep()), 100);

        assertThat(result.value()).isEqualTo("ok");
        assertThat(result.notes()).isEmpty();
        assertThat(result.overBudget()).isFalse();
    }

    @Test
    void stopsAtFirstStepThatBringsValueWithinBudget() {
        var step1 = step("trim", s -> "x".repeat(20));
        var step2 = step("trim-more", s -> "x".repeat(5));

        var result = compressor.fit("x".repeat(100), List.of(step1, step2), 25);

        assertThat(result.value()).hasSize(20);
        assertThat(result.notes()).containsExactly("trim");
        assertThat(result.overBudget()).isFalse();
    }

    @Test
    void appliesMultipleStepsWhenFirstDoesNotSuffice() {
        var step1 = step("a", s -> "x".repeat(60));
        var step2 = step("b", s -> "x".repeat(10));

        var result = compressor.fit("x".repeat(100), List.of(step1, step2), 20);

        assertThat(result.notes()).containsExactly("a", "b");
        assertThat(result.value()).hasSize(10);
        assertThat(result.overBudget()).isFalse();
    }

    @Test
    void skipsStepsThatReturnEmpty() {
        var noop = new CompressionStep<String>() {
            public String name() { return "noop"; }
            public Optional<Compressed<String>> apply(String v) { return Optional.empty(); }
        };
        var trim = step("trim", s -> "x".repeat(5));

        var result = compressor.fit("x".repeat(50), List.of(noop, trim), 10);

        assertThat(result.notes()).containsExactly("trim");
    }

    @Test
    void marksOverBudgetWhenStepsExhausted() {
        var trim = step("trim", s -> "x".repeat(40));

        var result = compressor.fit("x".repeat(100), List.of(trim), 10);

        assertThat(result.overBudget()).isTrue();
        assertThat(result.notes()).hasSize(2);
        assertThat(result.notes().get(1)).contains("still exceeds budget");
    }

    private static CompressionStep<String> step(String name, java.util.function.UnaryOperator<String> op) {
        return new CompressionStep<>() {
            public String name() { return name; }
            public Optional<Compressed<String>> apply(String v) {
                return Optional.of(new Compressed<>(op.apply(v), name));
            }
        };
    }

    private static CompressionStep<String> failingStep() {
        return new CompressionStep<>() {
            public String name() { return "should-not-run"; }
            public Optional<Compressed<String>> apply(String v) {
                throw new AssertionError("Step should not be invoked when value is within budget");
            }
        };
    }
}
