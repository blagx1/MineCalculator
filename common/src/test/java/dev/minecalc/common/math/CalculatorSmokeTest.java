package dev.minecalc.common.math;

import java.math.BigDecimal;

/** Dependency-free smoke tests; run with assertions enabled or execute main directly. */
public final class CalculatorSmokeTest {
    public static void main(String[] args) throws Exception {
        expect("2 + 3 * 4", "14");
        expect("(2 + 3) * 4", "20");
        expect("200 * 15%", "30");
        expect("200 + 15%", "230");
        expect("200 - 15%", "170");
        expect("50% * 800", "400");
        expect("-5 + 2", "-3");
        expect("1,5 + 2.5", "4");
        expect("100000 / 64", "1562.5");
        expectFailure("10 / 0");
        System.out.println("MineCalculator calculator smoke tests passed.");
    }

    private static void expect(String expression, String expected) throws Exception {
        BigDecimal actual = Calculator.calculate(expression);
        String formatted = Calculator.format(actual);
        if (!expected.equals(formatted)) {
            throw new AssertionError(expression + ": expected " + expected + ", got " + formatted);
        }
    }

    private static void expectFailure(String expression) throws Exception {
        try {
            Calculator.calculate(expression);
            throw new AssertionError(expression + ": expected CalculatorException");
        } catch (CalculatorException expected) {
            // Expected.
        }
    }
}
