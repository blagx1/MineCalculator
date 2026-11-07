package dev.minecalc.common.math;

/** A safe, user-facing error raised while parsing or evaluating an expression. */
public final class CalculatorException extends Exception {
    public CalculatorException(String message) {
        super(message);
    }
}
