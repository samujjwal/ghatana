package com.ghatana.platform.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Pair.
 */
class PairTest {

    @Test
    void testOf() {
        Pair<String, Integer> pair = Pair.of("test", 42);
        
        assertNotNull(pair);
        assertEquals("test", pair.first());
        assertEquals(42, pair.second());
    }

    @Test
    void testGetFirst() {
        Pair<String, Integer> pair = Pair.of("first", 1);
        
        assertEquals("first", pair.getFirst());
        assertEquals("first", pair.first()); // Record accessor
    }

    @Test
    void testGetSecond() {
        Pair<String, Integer> pair = Pair.of("value", 100);
        
        assertEquals(100, pair.getSecond());
        assertEquals(100, pair.second()); // Record accessor
    }

    @Test
    void testNullFirstThrowsException() {
        assertThrows(NullPointerException.class, () -> 
            Pair.of(null, "second")
        );
    }

    @Test
    void testNullSecondThrowsException() {
        assertThrows(NullPointerException.class, () -> 
            Pair.of("first", null)
        );
    }

    @Test
    void testEquals() {
        Pair<String, Integer> pair1 = Pair.of("test", 42);
        Pair<String, Integer> pair2 = Pair.of("test", 42);
        Pair<String, Integer> pair3 = Pair.of("test", 43);
        Pair<String, Integer> pair4 = Pair.of("other", 42);
        
        assertEquals(pair1, pair2);
        assertNotEquals(pair1, pair3);
        assertNotEquals(pair1, pair4);
        assertNotEquals(pair1, null);
        assertNotEquals(pair1, "not a pair");
    }

    @Test
    void testHashCode() {
        Pair<String, Integer> pair1 = Pair.of("test", 42);
        Pair<String, Integer> pair2 = Pair.of("test", 42);
        
        assertEquals(pair1.hashCode(), pair2.hashCode());
    }

    @Test
    void testToString() {
        Pair<String, Integer> pair = Pair.of("key", 123);
        String str = pair.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("key"));
        assertTrue(str.contains("123"));
    }

    @Test
    void testDifferentTypes() {
        Pair<Integer, String> intString = Pair.of(1, "one");
        Pair<Boolean, Double> boolDouble = Pair.of(true, 3.14);
        Pair<String, String> stringString = Pair.of("left", "right");
        
        assertEquals(1, intString.first());
        assertEquals("one", intString.second());
        
        assertEquals(true, boolDouble.first());
        assertEquals(3.14, boolDouble.second());
        
        assertEquals("left", stringString.first());
        assertEquals("right", stringString.second());
    }

    @Test
    void testNestedPairs() {
        Pair<String, Integer> inner = Pair.of("inner", 10);
        Pair<Pair<String, Integer>, String> outer = Pair.of(inner, "outer");
        
        assertEquals(inner, outer.first());
        assertEquals("outer", outer.second());
        assertEquals("inner", outer.first().first());
        assertEquals(10, outer.first().second());
    }

    @Test
    void testImmutability() {
        String first = "original";
        Integer second = 42;
        Pair<String, Integer> pair = Pair.of(first, second);
        
        // Verify values are stored correctly
        assertEquals("original", pair.first());
        assertEquals(42, pair.second());
        
        // Modifying original variables doesn't affect pair (primitives/immutable strings)
        first = "modified";
        second = 100;
        
        assertEquals("original", pair.first());
        assertEquals(42, pair.second());
    }
}
