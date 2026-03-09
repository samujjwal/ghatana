package com.ghatana.core.event.query;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents an executable plan for a windowed query that groups events into windows
 * based on time or other criteria.
 */
public interface WindowedQueryPlan {
    
    /**
     * Executes the windowed query and returns a Promise that completes with a list of windowed results.
     * 
     * @return A Promise that completes with a list of windowed results, one for each window
     */
    Promise<List<WindowedResult>> execute();
    
    /**
     * Gets the window specification for this query plan.
     * 
     * @return The window specification
     */
    WindowSpec getWindowSpec();
    
    /**
     * Creates a new windowed query plan that applies an additional aggregation.
     * 
     * @param aggregation The aggregation to apply
     * @return A new windowed query plan with the additional aggregation
     */
    default WindowedQueryPlan withAggregation(Aggregation aggregation) {
        return new WindowedQueryPlan() {
            @Override
            public Promise<List<WindowedResult>> execute() {
                // Apply the aggregation to each window
                return WindowedQueryPlan.this.execute()
                    .map(windows -> windows.stream()
                        .map(window -> {
                            // Apply the aggregation to the window
                            Object result = aggregation.apply(window.getEvents());
                            // Create a new window with the aggregation result
                            return new WindowedResult(
                                window.getWindowStart(),
                                window.getWindowEnd(),
                                window.getEvents(),
                                window.getAggregations(),
                                window.getMetadata()
                            );
                        })
                        .collect(Collectors.toList()));
            }
            
            @Override
            public WindowSpec getWindowSpec() {
                return WindowedQueryPlan.this.getWindowSpec();
            }
        };
    }
    
    /**
     * Creates a new windowed query plan that groups the results by the specified key function.
     * 
     * @param keyFunction A function that extracts a key from an event
     * @return A new windowed query plan with grouping
     */
    default <K> WindowedQueryPlan groupBy(java.util.function.Function<Event, K> keyFunction) {
        return new WindowedQueryPlan() {
            @Override
            public Promise<List<WindowedResult>> execute() {
                // Group events in each window by the key function
                return WindowedQueryPlan.this.execute()
                    .map(windows -> windows.stream()
                        .flatMap(window -> {
                            // Group events in this window by the key
                            Map<K, List<Event>> groups = window.getEvents().stream()
                                .collect(Collectors.groupingBy(keyFunction));
                            
                            // Create a new window for each group
                            return groups.entrySet().stream()
                                .map(entry -> new WindowedResult(
                                    window.getWindowStart(),
                                    window.getWindowEnd(),
                                    entry.getValue(),
                                    window.getAggregations(),
                                    Map.of("groupKey", entry.getKey())
                                ));
                        })
                        .collect(Collectors.toList()));
            }
            
            @Override
            public WindowSpec getWindowSpec() {
                return WindowedQueryPlan.this.getWindowSpec();
            }
        };
    }
    
    /**
     * Executes the windowed query and returns the results as a stream of windowed results.
     * 
     * @return A Promise that completes with a stream of windowed results, one for each window
     */
    default Promise<java.util.stream.Stream<WindowedResult>> stream() {
        return execute()
            .map(List::stream);
    }
}
