/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

/**
 * Comparison operators for rule conditions.
 *
 * @since 2.0.0
 *
 * @doc.type enum
 * @doc.purpose Comparison operators for rule condition evaluation
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum Operator {

    // ── Numeric / Comparable ────────────────────────────────────────────────
    GREATER_THAN {
        @Override public boolean evaluate(Object actual, Object expected) {
            return compareNumerically(actual, expected) > 0;
        }
    },
    GREATER_THAN_OR_EQUAL {
        @Override public boolean evaluate(Object actual, Object expected) {
            return compareNumerically(actual, expected) >= 0;
        }
    },
    LESS_THAN {
        @Override public boolean evaluate(Object actual, Object expected) {
            return compareNumerically(actual, expected) < 0;
        }
    },
    LESS_THAN_OR_EQUAL {
        @Override public boolean evaluate(Object actual, Object expected) {
            return compareNumerically(actual, expected) <= 0;
        }
    },

    // ── Equality ────────────────────────────────────────────────────────────
    EQUALS {
        @Override public boolean evaluate(Object actual, Object expected) {
            if (actual == null) return expected == null;
            if (expected == null) return false;
            if (actual instanceof Number a && expected instanceof Number e) {
                return a.doubleValue() == e.doubleValue();
            }
            return actual.toString().equals(expected.toString());
        }
    },
    NOT_EQUALS {
        @Override public boolean evaluate(Object actual, Object expected) {
            return !EQUALS.evaluate(actual, expected);
        }
    },

    // ── String ──────────────────────────────────────────────────────────────
    CONTAINS {
        @Override public boolean evaluate(Object actual, Object expected) {
            return actual != null && expected != null
                    && actual.toString().contains(expected.toString());
        }
    },
    NOT_CONTAINS {
        @Override public boolean evaluate(Object actual, Object expected) {
            return !CONTAINS.evaluate(actual, expected);
        }
    },
    STARTS_WITH {
        @Override public boolean evaluate(Object actual, Object expected) {
            return actual != null && expected != null
                    && actual.toString().startsWith(expected.toString());
        }
    },
    ENDS_WITH {
        @Override public boolean evaluate(Object actual, Object expected) {
            return actual != null && expected != null
                    && actual.toString().endsWith(expected.toString());
        }
    },
    REGEX {
        @Override public boolean evaluate(Object actual, Object expected) {
            return actual != null && expected != null
                    && actual.toString().matches(expected.toString());
        }
    },

    // ── Collection ──────────────────────────────────────────────────────────
    IN {
        @Override public boolean evaluate(Object actual, Object expected) {
            if (actual == null || expected == null) return false;
            if (expected instanceof Iterable<?> list) {
                for (Object item : list) {
                    if (EQUALS.evaluate(actual, item)) return true;
                }
                return false;
            }
            return EQUALS.evaluate(actual, expected);
        }
    },
    NOT_IN {
        @Override public boolean evaluate(Object actual, Object expected) {
            return !IN.evaluate(actual, expected);
        }
    },

    // ── Null checks ─────────────────────────────────────────────────────────
    IS_NULL {
        @Override public boolean evaluate(Object actual, Object expected) {
            return actual == null;
        }
    },
    IS_NOT_NULL {
        @Override public boolean evaluate(Object actual, Object expected) {
            return actual != null;
        }
    };

    /**
     * Evaluates this operator against the given actual and expected values.
     *
     * @param actual   the value extracted from the event
     * @param expected the reference value from the rule condition
     * @return true if the condition holds
     */
    public abstract boolean evaluate(Object actual, Object expected);

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static int compareNumerically(Object actual, Object expected) {
        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Cannot compare null values numerically");
        }
        double a = toDouble(actual);
        double e = toDouble(expected);
        return Double.compare(a, e);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Cannot convert to number: " + value + " (" + value.getClass().getSimpleName() + ")");
        }
    }
}
