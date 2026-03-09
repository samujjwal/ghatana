package com.ghatana.core.event.query;

import com.ghatana.platform.domain.domain.event.Event;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents an aggregation operation that can be applied to a collection of events.
 */
@FunctionalInterface
public interface Aggregation {
    
    /**
     * Applies the aggregation to the given list of events.
     * 
     * @param events The events to aggregate
     * @return The result of the aggregation
     */
    Object apply(List<Event> events);
    
    // Standard aggregation functions
    
    /**
     * Creates a count aggregation that counts the number of events.
     * 
     * @return A count aggregation
     */
    static Aggregation count() {
        return events -> (long) events.size();
    }
    
    /**
     * Creates a sum aggregation that sums the values of a numeric field.
     * 
     * @param field The field to sum
     * @return A sum aggregation
     */
    static Aggregation sum(String field) {
        return events -> events.stream()
            .mapToDouble(e -> {
                try {
                    return (double) e.get(field);
                } catch (Exception ex) {
                    return 0.0;
                }
            })
            .sum();
    }
    
    /**
     * Creates an average aggregation that calculates the mean of a numeric field.
     * 
     * @param field The field to average
     * @return An average aggregation
     */
    static Aggregation avg(String field) {
        return events -> events.stream()
            .mapToDouble(e -> {
                try {
                    return (double) e.get(field);
                } catch (Exception ex) {
                    return Double.NaN;
                }
            })
            .average()
            .orElse(Double.NaN);
    }
    
    /**
     * Creates a minimum aggregation that finds the minimum value of a numeric field.
     * 
     * @param field The field to find the minimum of
     * @return A minimum aggregation
     */
    static Aggregation min(String field) {
        return events -> events.stream()
            .mapToDouble(e -> {
                try {
                    return (double) e.get(field);
                } catch (Exception ex) {
                    return Double.POSITIVE_INFINITY;
                }
            })
            .min()
            .orElse(Double.NaN);
    }
    
    /**
     * Creates a maximum aggregation that finds the maximum value of a numeric field.
     * 
     * @param field The field to find the maximum of
     * @return A maximum aggregation
     */
    static Aggregation max(String field) {
        return events -> events.stream()
            .mapToDouble(e -> {
                try {
                    return (double) e.get(field);
                } catch (Exception ex) {
                    return Double.NEGATIVE_INFINITY;
                }
            })
            .max()
            .orElse(Double.NaN);
    }
    
    /**
     * Creates a distinct count aggregation that counts the number of distinct values of a field.
     * 
     * @param field The field to count distinct values of
     * @return A distinct count aggregation
     */
    static Aggregation distinctCount(String field) {
        return events -> events.stream()
            .map(e -> e.get(field))
            .distinct()
            .count();
    }
    
    /**
     * Creates a histogram aggregation that counts the frequency of values in a field.
     * 
     * @param field The field to create a histogram of
     * @return A histogram aggregation
     */
    static Aggregation histogram(String field) {
        return events -> events.stream()
            .map(e -> e.get(field))
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
    
    /**
     * Creates a custom aggregation using the provided function.
     * 
     * @param function The aggregation function
     * @return A custom aggregation
     */
    static Aggregation of(Function<List<Event>, Object> function) {
        return function::apply;
    }
}
