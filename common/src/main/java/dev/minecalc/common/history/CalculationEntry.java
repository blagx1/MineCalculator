package dev.minecalc.common.history;

import java.time.Instant;
import java.util.Objects;

public record CalculationEntry(String expression, String result, Instant createdAt) {
    public CalculationEntry {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
