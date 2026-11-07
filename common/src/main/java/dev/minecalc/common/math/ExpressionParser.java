package dev.minecalc.common.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Recursive-descent parser for everyday arithmetic.
 *
 * Grammar:
 * expression -> term (('+' | '-') term)*
 * term       -> unary (('*' | '/') unary)*
 * unary      -> ('+' | '-') unary | postfix
 * postfix    -> primary ('%')*
 * primary    -> NUMBER | '(' expression ')'
 *
 * A postfix percentage is a fraction for multiplication/division (200 * 15% = 30).
 * When it is the direct right operand of + or -, it is relative to the left operand
 * (200 + 15% = 230, 200 - 15% = 170).
 */
public final class ExpressionParser {
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_UP);
    private static final int DIVISION_SCALE = 16;

    private final String input;
    private int position;

    private ExpressionParser(String input) {
        this.input = input;
    }

    public static BigDecimal evaluate(String expression) throws CalculatorException {
        if (expression == null || expression.isBlank()) {
            throw new CalculatorException("Введите выражение");
        }
        if (expression.length() > 512) {
            throw new CalculatorException("Выражение слишком длинное");
        }
        ExpressionParser parser = new ExpressionParser(expression);
        BigDecimal result = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Лишний символ: '" + parser.peek() + "'");
        }
        return normalize(result);
    }

    private BigDecimal parseExpression() throws CalculatorException {
        BigDecimal value = parseTerm().value();
        while (true) {
            skipWhitespace();
            if (match('+')) {
                ParsedValue right = parseTerm();
                BigDecimal amount = right.isPercentage()
                        ? value.multiply(right.value(), MC) : right.value();
                value = value.add(amount, MC);
            } else if (match('-')) {
                ParsedValue right = parseTerm();
                BigDecimal amount = right.isPercentage()
                        ? value.multiply(right.value(), MC) : right.value();
                value = value.subtract(amount, MC);
            } else {
                return value;
            }
        }
    }

    private ParsedValue parseTerm() throws CalculatorException {
        ParsedValue parsed = parseUnary();
        BigDecimal value = parsed.value();
        boolean isPercentage = parsed.isPercentage();
        while (true) {
            skipWhitespace();
            if (match('*') || match('×')) {
                value = value.multiply(parseUnary().value(), MC);
                isPercentage = false;
            } else if (match('/') || match('÷')) {
                BigDecimal divisor = parseUnary().value();
                if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                    throw new CalculatorException("Деление на ноль невозможно");
                }
                value = value.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP);
                isPercentage = false;
            } else {
                return new ParsedValue(value, isPercentage);
            }
        }
    }

    private ParsedValue parseUnary() throws CalculatorException {
        skipWhitespace();
        if (match('+')) return parseUnary();
        if (match('-')) {
            ParsedValue value = parseUnary();
            return new ParsedValue(value.value().negate(MC), value.isPercentage());
        }
        return parsePostfix();
    }

    private ParsedValue parsePostfix() throws CalculatorException {
        ParsedValue parsed = parsePrimary();
        BigDecimal value = parsed.value();
        boolean isPercentage = false;
        skipWhitespace();
        while (match('%')) {
            value = value.divide(BigDecimal.valueOf(100), MC);
            isPercentage = true;
            skipWhitespace();
        }
        return new ParsedValue(value, isPercentage);
    }

    private ParsedValue parsePrimary() throws CalculatorException {
        skipWhitespace();
        if (match('(')) {
            BigDecimal value = parseExpression();
            skipWhitespace();
            if (!match(')')) {
                throw error("Не закрыта скобка");
            }
            return new ParsedValue(value, false);
        }
        return new ParsedValue(parseNumber(), false);
    }

    private BigDecimal parseNumber() throws CalculatorException {
        skipWhitespace();
        int start = position;
        boolean hasDigit = false;
        boolean hasSeparator = false;

        while (!isAtEnd()) {
            char c = peek();
            if (Character.isDigit(c)) {
                hasDigit = true;
                position++;
            } else if ((c == '.' || c == ',') && !hasSeparator) {
                hasSeparator = true;
                position++;
            } else {
                break;
            }
        }

        if (!hasDigit) {
            throw error(isAtEnd() ? "Ожидалось число" : "Ожидалось число или скобка");
        }

        String token = input.substring(start, position).replace(',', '.');
        try {
            return new BigDecimal(token, MC);
        } catch (NumberFormatException exception) {
            throw error("Некорректное число: " + token);
        }
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) position++;
    }

    private boolean match(char expected) {
        if (!isAtEnd() && peek() == expected) {
            position++;
            return true;
        }
        return false;
    }

    private char peek() {
        return input.charAt(position);
    }

    private boolean isAtEnd() {
        return position >= input.length();
    }

    private CalculatorException error(String message) {
        return new CalculatorException(message + " (позиция " + (position + 1) + ")");
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return value.stripTrailingZeros();
    }

    private record ParsedValue(BigDecimal value, boolean isPercentage) {}
}
