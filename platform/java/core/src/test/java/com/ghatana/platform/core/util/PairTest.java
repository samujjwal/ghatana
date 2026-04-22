package com.ghatana.platform.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Pair.
 */
class PairTest {

    @Test
    void testOf() { // GH-90000
        Pair<String, Integer> pair = Pair.of("test", 42); // GH-90000

        assertNotNull(pair); // GH-90000
        assertEquals("test", pair.first()); // GH-90000
        assertEquals(42, pair.second()); // GH-90000
    }

    @Test
    void testGetFirst() { // GH-90000
        Pair<String, Integer> pair = Pair.of("first", 1); // GH-90000

        assertEquals("first", pair.getFirst()); // GH-90000
        assertEquals("first", pair.first()); // Record accessor // GH-90000
    }

    @Test
    void testGetSecond() { // GH-90000
        Pair<String, Integer> pair = Pair.of("value", 100); // GH-90000

        assertEquals(100, pair.getSecond()); // GH-90000
        assertEquals(100, pair.second()); // Record accessor // GH-90000
    }

    @Test
    void testNullFirstThrowsException() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
            Pair.of(null, "second") // GH-90000
        );
    }

    @Test
    void testNullSecondThrowsException() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
            Pair.of("first", null) // GH-90000
        );
    }

    @Test
    void testEquals() { // GH-90000
        Pair<String, Integer> pair1 = Pair.of("test", 42); // GH-90000
        Pair<String, Integer> pair2 = Pair.of("test", 42); // GH-90000
        Pair<String, Integer> pair3 = Pair.of("test", 43); // GH-90000
        Pair<String, Integer> pair4 = Pair.of("other", 42); // GH-90000

        assertEquals(pair1, pair2); // GH-90000
        assertNotEquals(pair1, pair3); // GH-90000
        assertNotEquals(pair1, pair4); // GH-90000
        assertNotEquals(pair1, null); // GH-90000
        assertNotEquals(pair1, "not a pair"); // GH-90000
    }

    @Test
    void testHashCode() { // GH-90000
        Pair<String, Integer> pair1 = Pair.of("test", 42); // GH-90000
        Pair<String, Integer> pair2 = Pair.of("test", 42); // GH-90000

        assertEquals(pair1.hashCode(), pair2.hashCode()); // GH-90000
    }

    @Test
    void testToString() { // GH-90000
        Pair<String, Integer> pair = Pair.of("key", 123); // GH-90000
        String str = pair.toString(); // GH-90000

        assertNotNull(str); // GH-90000
        assertTrue(str.contains("key [GH-90000]"));
        assertTrue(str.contains("123 [GH-90000]"));
    }

    @Test
    void testDifferentTypes() { // GH-90000
        Pair<Integer, String> intString = Pair.of(1, "one"); // GH-90000
        Pair<Boolean, Double> boolDouble = Pair.of(true, 3.14); // GH-90000
        Pair<String, String> stringString = Pair.of("left", "right"); // GH-90000

        assertEquals(1, intString.first()); // GH-90000
        assertEquals("one", intString.second()); // GH-90000

        assertEquals(true, boolDouble.first()); // GH-90000
        assertEquals(3.14, boolDouble.second()); // GH-90000

        assertEquals("left", stringString.first()); // GH-90000
        assertEquals("right", stringString.second()); // GH-90000
    }

    @Test
    void testNestedPairs() { // GH-90000
        Pair<String, Integer> inner = Pair.of("inner", 10); // GH-90000
        Pair<Pair<String, Integer>, String> outer = Pair.of(inner, "outer"); // GH-90000

        assertEquals(inner, outer.first()); // GH-90000
        assertEquals("outer", outer.second()); // GH-90000
        assertEquals("inner", outer.first().first()); // GH-90000
        assertEquals(10, outer.first().second()); // GH-90000
    }

    @Test
    void testImmutability() { // GH-90000
        String first = "original";
        Integer second = 42;
        Pair<String, Integer> pair = Pair.of(first, second); // GH-90000

        // Verify values are stored correctly
        assertEquals("original", pair.first()); // GH-90000
        assertEquals(42, pair.second()); // GH-90000

        // Modifying original variables doesn't affect pair (primitives/immutable strings) // GH-90000
        first = "modified";
        second = 100;

        assertEquals("original", pair.first()); // GH-90000
        assertEquals(42, pair.second()); // GH-90000
    }
}
