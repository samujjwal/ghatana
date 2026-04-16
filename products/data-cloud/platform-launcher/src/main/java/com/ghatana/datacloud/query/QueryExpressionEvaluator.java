package com.ghatana.datacloud.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Evaluates backend-agnostic query expressions against map-backed rows.
 *
 * @doc.type class
 * @doc.purpose Shared evaluator for Data Cloud filter and sort semantics
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class QueryExpressionEvaluator {

    private QueryExpressionEvaluator() {
    }

    public static boolean matches(Map<String, Object> row, String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        String normalized = trimOuterParentheses(expression.trim());
        int orIndex = findTopLevelLogicalOperator(normalized, "OR");
        if (orIndex >= 0) {
            String left = normalized.substring(0, orIndex).trim();
            String right = normalized.substring(orIndex + 4).trim();
            return matches(row, left) || matches(row, right);
        }

        int andIndex = findTopLevelLogicalOperator(normalized, "AND");
        if (andIndex >= 0) {
            String left = normalized.substring(0, andIndex).trim();
            String right = normalized.substring(andIndex + 5).trim();
            return matches(row, left) && matches(row, right);
        }

        if (startsWithKeyword(normalized, "NOT ")) {
            return !matches(row, normalized.substring(4).trim());
        }

        return evaluateAtomicExpression(row, normalized);
    }

    public static boolean matchesCondition(Map<String, Object> row, String field, String operator, Object value) {
        Object actualValue = resolveFieldValue(row, field);
        String normalizedOperator = operator.toUpperCase(Locale.ROOT);
        return switch (normalizedOperator) {
            case "=", "EQ" -> compare(actualValue, value) == 0;
            case "!=", "NE" -> compare(actualValue, value) != 0;
            case ">", "GT" -> compare(actualValue, value) > 0;
            case ">=", "GTE" -> compare(actualValue, value) >= 0;
            case "<", "LT" -> compare(actualValue, value) < 0;
            case "<=", "LTE" -> compare(actualValue, value) <= 0;
            case "IN" -> containsValue(value, actualValue);
            case "NOT IN" -> !containsValue(value, actualValue);
            case "BETWEEN" -> matchesBetween(actualValue, value);
            case "LIKE" -> matchesLike(actualValue, Objects.toString(value, ""), false);
            case "ILIKE" -> matchesLike(actualValue, Objects.toString(value, ""), true);
            case "EXISTS" -> actualValue != null;
            default -> false;
        };
    }

    public static Comparator<Map<String, Object>> comparator(String field, boolean ascending) {
        Comparator<Map<String, Object>> comparator = Comparator.comparing(
            row -> normalizeComparable(resolveFieldValue(row, field)),
            Comparator.nullsLast(QueryExpressionEvaluator::compareNormalized));
        return ascending ? comparator : comparator.reversed();
    }

    private static boolean evaluateAtomicExpression(Map<String, Object> row, String expression) {
        String uppercase = expression.toUpperCase(Locale.ROOT);
        if (uppercase.endsWith(" IS NULL")) {
            String field = expression.substring(0, uppercase.length() - " IS NULL".length()).trim();
            return resolveFieldValue(row, field) == null;
        }
        if (uppercase.endsWith(" IS NOT NULL")) {
            String field = expression.substring(0, uppercase.length() - " IS NOT NULL".length()).trim();
            return resolveFieldValue(row, field) != null;
        }

        int betweenIndex = findTopLevelKeyword(uppercase, " BETWEEN ");
        if (betweenIndex >= 0) {
            int andIndex = findTopLevelKeyword(uppercase, " AND ", betweenIndex + 9);
            if (andIndex < 0) {
                return false;
            }
            String field = expression.substring(0, betweenIndex).trim();
            Object start = parseLiteral(expression.substring(betweenIndex + 9, andIndex).trim());
            Object end = parseLiteral(expression.substring(andIndex + 5).trim());
            return matchesCondition(row, field, "BETWEEN", new Object[]{start, end});
        }

        int inIndex = findTopLevelKeyword(uppercase, " NOT IN ");
        if (inIndex >= 0) {
            String field = expression.substring(0, inIndex).trim();
            List<Object> values = parseListLiteral(expression.substring(inIndex + 8).trim());
            return matchesCondition(row, field, "NOT IN", values);
        }

        inIndex = findTopLevelKeyword(uppercase, " IN ");
        if (inIndex >= 0) {
            String field = expression.substring(0, inIndex).trim();
            List<Object> values = parseListLiteral(expression.substring(inIndex + 4).trim());
            return matchesCondition(row, field, "IN", values);
        }

        int likeIndex = findTopLevelKeyword(uppercase, " ILIKE ");
        if (likeIndex >= 0) {
            String field = expression.substring(0, likeIndex).trim();
            Object value = parseLiteral(expression.substring(likeIndex + 7).trim());
            return matchesCondition(row, field, "ILIKE", value);
        }

        likeIndex = findTopLevelKeyword(uppercase, " LIKE ");
        if (likeIndex >= 0) {
            String field = expression.substring(0, likeIndex).trim();
            Object value = parseLiteral(expression.substring(likeIndex + 6).trim());
            return matchesCondition(row, field, "LIKE", value);
        }

        for (String operator : List.of(">=", "<=", "!=", "=", ">", "<")) {
            int operatorIndex = findTopLevelOperator(expression, operator);
            if (operatorIndex >= 0) {
                String field = expression.substring(0, operatorIndex).trim();
                Object value = parseLiteral(expression.substring(operatorIndex + operator.length()).trim());
                return matchesCondition(row, field, operator, value);
            }
        }

        return false;
    }

    private static int findTopLevelLogicalOperator(String expression, String operator) {
        return findTopLevelKeyword(expression.toUpperCase(Locale.ROOT), " " + operator + " ");
    }

    private static int findTopLevelKeyword(String expression, String keyword) {
        return findTopLevelKeyword(expression, keyword, 0);
    }

    private static int findTopLevelKeyword(String expression, String keyword, int startIndex) {
        int depth = 0;
        boolean inQuotes = false;
        for (int index = startIndex; index <= expression.length() - keyword.length(); index++) {
            char current = expression.charAt(index);
            if (current == '\'' && !isEscaped(expression, index)) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (current == '(') {
                    depth++;
                } else if (current == ')') {
                    depth = Math.max(0, depth - 1);
                }
                if (depth == 0 && expression.regionMatches(index, keyword, 0, keyword.length())) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int findTopLevelOperator(String expression, String operator) {
        int depth = 0;
        boolean inQuotes = false;
        for (int index = 0; index <= expression.length() - operator.length(); index++) {
            char current = expression.charAt(index);
            if (current == '\'' && !isEscaped(expression, index)) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (current == '(') {
                    depth++;
                } else if (current == ')') {
                    depth = Math.max(0, depth - 1);
                }
                if (depth == 0 && expression.startsWith(operator, index)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean isEscaped(String value, int index) {
        return index > 0 && value.charAt(index - 1) == '\\';
    }

    private static String trimOuterParentheses(String expression) {
        String current = expression;
        while (current.startsWith("(") && current.endsWith(")") && wrapsEntireExpression(current)) {
            current = current.substring(1, current.length() - 1).trim();
        }
        return current;
    }

    private static boolean wrapsEntireExpression(String expression) {
        int depth = 0;
        boolean inQuotes = false;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (current == '\'' && !isEscaped(expression, index)) {
                inQuotes = !inQuotes;
            }
            if (inQuotes) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0 && index < expression.length() - 1) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private static boolean startsWithKeyword(String expression, String keyword) {
        return expression.toUpperCase(Locale.ROOT).startsWith(keyword.toUpperCase(Locale.ROOT));
    }

    private static List<Object> parseListLiteral(String listLiteral) {
        String normalized = trimOuterParentheses(listLiteral.trim());
        List<String> tokens = splitTopLevel(normalized, ',');
        List<Object> values = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            if (!token.isBlank()) {
                values.add(parseLiteral(token.trim()));
            }
        }
        return values;
    }

    private static List<String> splitTopLevel(String value, char delimiter) {
        List<String> tokens = new ArrayList<>();
        int depth = 0;
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            if (currentChar == '\'' && !isEscaped(value, index)) {
                inQuotes = !inQuotes;
            }
            if (!inQuotes) {
                if (currentChar == '(') {
                    depth++;
                } else if (currentChar == ')') {
                    depth = Math.max(0, depth - 1);
                }
            }
            if (currentChar == delimiter && depth == 0 && !inQuotes) {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static Object parseLiteral(String literal) {
        String normalized = trimOuterParentheses(literal.trim());
        if (normalized.length() >= 2 && normalized.startsWith("'") && normalized.endsWith("'")) {
            return normalized.substring(1, normalized.length() - 1).replace("\\'", "'");
        }
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.parseBoolean(normalized);
        }
        if ("null".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            if (normalized.contains(".")) {
                return Double.parseDouble(normalized);
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return normalized;
        }
    }

    private static Object resolveFieldValue(Map<String, Object> row, String field) {
        if (row.containsKey(field)) {
            return row.get(field);
        }

        String[] parts = field.split("\\.");
        Object current = row;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> nestedMap)) {
                return null;
            }
            current = nestedMap.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static boolean containsValue(Object values, Object actualValue) {
        if (!(values instanceof Collection<?> collection)) {
            return false;
        }
        return collection.stream().anyMatch(candidate -> compare(actualValue, candidate) == 0);
    }

    private static boolean matchesBetween(Object actualValue, Object bounds) {
        if (!(bounds instanceof Object[] array) || array.length != 2) {
            return false;
        }
        return compare(actualValue, array[0]) >= 0 && compare(actualValue, array[1]) <= 0;
    }

    private static boolean matchesLike(Object actualValue, String pattern, boolean ignoreCase) {
        if (actualValue == null) {
            return false;
        }
        String regex = Pattern.quote(pattern)
            .replace("%", "\\E.*\\Q")
            .replace("_", "\\E.\\Q");
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        return Pattern.compile(regex, flags).matcher(actualValue.toString()).matches();
    }

    private static int compare(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }

        Object normalizedLeft = normalizeComparable(left);
        Object normalizedRight = normalizeComparable(right);
        return compareNormalized(normalizedLeft, normalizedRight);
    }

    private static Object normalizeComparable(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof CharSequence sequence) {
            Optional<Instant> parsedInstant = parseInstant(sequence.toString());
            if (parsedInstant.isPresent()) {
                return parsedInstant.get();
            }
            try {
                if (sequence.toString().contains(".")) {
                    return Double.parseDouble(sequence.toString());
                }
                return Long.parseLong(sequence.toString());
            } catch (NumberFormatException ignored) {
                return sequence.toString();
            }
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static int compareNormalized(Object left, Object right) {
        if (left instanceof Double leftDouble && right instanceof Double rightDouble) {
            return Double.compare(leftDouble, rightDouble);
        }
        if (left instanceof Long leftLong && right instanceof Long rightLong) {
            return Long.compare(leftLong, rightLong);
        }
        if (left instanceof Instant leftInstant && right instanceof Instant rightInstant) {
            return leftInstant.compareTo(rightInstant);
        }
        if (left instanceof Comparable comparable && right != null && left.getClass().isAssignableFrom(right.getClass())) {
            return comparable.compareTo(right);
        }
        return left.toString().compareTo(right.toString());
    }

    private static Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}