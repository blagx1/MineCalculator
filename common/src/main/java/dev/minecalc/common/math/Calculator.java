package dev.minecalc.common.math;

import java.math.BigDecimal;

/** Public facade for the standalone mathematical engine. */
public final class Calculator {
    private Calculator() {}

    public static BigDecimal calculate(String expression) throws CalculatorException {
        return ExpressionParser.evaluate(expression);
    }

    public static String format(BigDecimal value) {
        if (value == null) return "";
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0) return "0";
        return normalized.toPlainString();
    }
}
