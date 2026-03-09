package com.ghatana.validation.ai.detectors;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.validation.ai.AIPatternDetectionService.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects temporal patterns in event streams.
 * Consolidated from event-core temporal analysis capabilities.
 
 *
 * @doc.type class
 * @doc.purpose Temporal pattern detector
 * @doc.layer core
 * @doc.pattern Component
*/
public class TemporalPatternDetector implements PatternDetector {
    
    @Override
    public List<DetectedPattern> detect(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Detect periodic patterns
        patterns.addAll(detectPeriodicPatterns(events, config));
        
        // Detect daily patterns
        patterns.addAll(detectDailyPatterns(events, config));
        
        // Detect weekly patterns
        patterns.addAll(detectWeeklyPatterns(events, config));
        
        // Detect burst patterns
        patterns.addAll(detectBurstPatterns(events, config));
        
        return patterns;
    }
    
    @Override
    public PatternType getPatternType() {
        return PatternType.TEMPORAL;
    }
    
    @Override
    public Map<String, String> getConfigurationRequirements() {
        Map<String, String> requirements = new HashMap<>();
        requirements.put("minPeriodMs", "Minimum period length in milliseconds (default: 60000)");
        requirements.put("maxPeriodMs", "Maximum period length in milliseconds (default: 86400000)");
        requirements.put("periodTolerance", "Tolerance for period variation as fraction (default: 0.1)");
        requirements.put("minCycles", "Minimum number of cycles to confirm pattern (default: 3)");
        requirements.put("burstThreshold", "Burst detection threshold as multiple of average rate (default: 3.0)");
        requirements.put("burstMinEvents", "Minimum events required for burst detection (default: 5)");
        return requirements;
    }
    
    private List<DetectedPattern> detectPeriodicPatterns(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        long minPeriodMs = getConfigValue(config, "minPeriodMs", 60000L);
        long maxPeriodMs = getConfigValue(config, "maxPeriodMs", 86400000L);
        double periodTolerance = getConfigValue(config, "periodTolerance", 0.1);
        int minCycles = getConfigValue(config, "minCycles", 3);
        
        if (events.size() < minCycles * 2) {
            return patterns;
        }
        
        // Sort events by timestamp
        List<Event> sortedEvents = events.stream()
            .sorted(Comparator.comparing(Event::getTimestamp))
            .collect(Collectors.toList());
        
        // Group events by type for separate analysis
        Map<String, List<Event>> eventsByType = sortedEvents.stream()
            .collect(Collectors.groupingBy(Event::getType));
        
        for (Map.Entry<String, List<Event>> entry : eventsByType.entrySet()) {
            String eventType = entry.getKey();
            List<Event> typeEvents = entry.getValue();
            
            if (typeEvents.size() < minCycles * 2) continue;
            
            // Calculate intervals between consecutive events
            List<Long> intervals = new ArrayList<>();
            for (int i = 1; i < typeEvents.size(); i++) {
                long interval = typeEvents.get(i).getTime().getOccurrenceTime().start().toInstant().toEpochMilli() - 
                               typeEvents.get(i-1).getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
                intervals.add(interval);
            }
            
            // Find repeating intervals (potential periods)
            Map<Long, Integer> intervalCounts = new HashMap<>();
            for (Long interval : intervals) {
                if (interval >= minPeriodMs && interval <= maxPeriodMs) {
                    // Group similar intervals together
                    Long bucket = findIntervalBucket(interval, intervalCounts.keySet(), periodTolerance);
                    if (bucket != null) {
                        intervalCounts.put(bucket, intervalCounts.get(bucket) + 1);
                    } else {
                        intervalCounts.put(interval, 1);
                    }
                }
            }
            
            // Check for patterns with sufficient cycles
            for (Map.Entry<Long, Integer> intervalEntry : intervalCounts.entrySet()) {
                Long period = intervalEntry.getKey();
                Integer count = intervalEntry.getValue();
                
                if (count >= minCycles) {
                    double confidence = Math.min(1.0, (double) count / intervals.size());
                    
                    DetectedPattern pattern = new DetectedPattern(
                        UUID.randomUUID().toString(),
                        "Periodic Event Pattern",
                        String.format("Event type '%s' shows periodic behavior with period ~%s", 
                            eventType, formatDuration(period)),
                        confidence,
                        PatternType.TEMPORAL,
                        Map.of(
                            "eventType", eventType,
                            "periodMs", period,
                            "periodFormatted", formatDuration(period),
                            "cycles", count,
                            "totalIntervals", intervals.size(),
                            "regularity", confidence
                        ),
                        typeEvents,
                        System.currentTimeMillis()
                    );
                    
                    if (pattern.confidence() >= config.confidenceThreshold()) {
                        patterns.add(pattern);
                    }
                }
            }
        }
        
        return patterns;
    }
    
    private List<DetectedPattern> detectDailyPatterns(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Group events by hour of day
        Map<Integer, List<Event>> eventsByHour = events.stream()
            .collect(Collectors.groupingBy(e -> 
                LocalDateTime.ofInstant(e.getTime().getOccurrenceTime().start().toInstant(), ZoneOffset.UTC).getHour()));
        
        // Find peak hours
        int totalEvents = events.size();
        double averageEventsPerHour = totalEvents / 24.0;
        
        for (Map.Entry<Integer, List<Event>> entry : eventsByHour.entrySet()) {
            Integer hour = entry.getKey();
            List<Event> hourEvents = entry.getValue();
            
            double hourlyRate = hourEvents.size();
            double deviation = (hourlyRate - averageEventsPerHour) / averageEventsPerHour;
            
            if (Math.abs(deviation) >= 0.5 && hourEvents.size() >= 10) { // 50% deviation threshold
                DetectedPattern pattern = new DetectedPattern(
                    UUID.randomUUID().toString(),
                    deviation > 0 ? "Daily Peak Hour" : "Daily Low Hour",
                    String.format("Hour %d:00 shows %s activity (%.1f%% %s than average)",
                        hour, deviation > 0 ? "high" : "low", 
                        Math.abs(deviation) * 100, deviation > 0 ? "above" : "below"),
                    Math.min(1.0, Math.abs(deviation)),
                    PatternType.TEMPORAL,
                    Map.of(
                        "hour", hour,
                        "eventCount", hourEvents.size(),
                        "averagePerHour", averageEventsPerHour,
                        "deviation", deviation,
                        "isPeak", deviation > 0
                    ),
                    hourEvents,
                    System.currentTimeMillis()
                );
                
                if (pattern.confidence() >= config.confidenceThreshold()) {
                    patterns.add(pattern);
                }
            }
        }
        
        return patterns;
    }
    
    private List<DetectedPattern> detectWeeklyPatterns(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Group events by day of week
        Map<Integer, List<Event>> eventsByDayOfWeek = events.stream()
            .collect(Collectors.groupingBy(e -> 
                e.getTime().getOccurrenceTime().start().toInstant().atZone(ZoneOffset.UTC).getDayOfWeek().getValue()));
        
        // Find significant weekly patterns
        int totalEvents = events.size();
        double averageEventsPerDay = totalEvents / 7.0;
        
        for (Map.Entry<Integer, List<Event>> entry : eventsByDayOfWeek.entrySet()) {
            Integer dayOfWeek = entry.getKey();
            List<Event> dayEvents = entry.getValue();
            
            double dailyRate = dayEvents.size();
            double deviation = (dailyRate - averageEventsPerDay) / averageEventsPerDay;
            
            if (Math.abs(deviation) >= 0.3 && dayEvents.size() >= 10) { // 30% deviation threshold
                String dayName = getDayName(dayOfWeek);
                
                DetectedPattern pattern = new DetectedPattern(
                    UUID.randomUUID().toString(),
                    deviation > 0 ? "Weekly Peak Day" : "Weekly Low Day",
                    String.format("%s shows %s activity (%.1f%% %s than average)",
                        dayName, deviation > 0 ? "high" : "low",
                        Math.abs(deviation) * 100, deviation > 0 ? "above" : "below"),
                    Math.min(1.0, Math.abs(deviation)),
                    PatternType.TEMPORAL,
                    Map.of(
                        "dayOfWeek", dayOfWeek,
                        "dayName", dayName,
                        "eventCount", dayEvents.size(),
                        "averagePerDay", averageEventsPerDay,
                        "deviation", deviation,
                        "isPeak", deviation > 0
                    ),
                    dayEvents,
                    System.currentTimeMillis()
                );
                
                if (pattern.confidence() >= config.confidenceThreshold()) {
                    patterns.add(pattern);
                }
            }
        }
        
        return patterns;
    }
    
    private List<DetectedPattern> detectBurstPatterns(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        double burstThreshold = getConfigValue(config, "burstThreshold", 3.0);
        int burstMinEvents = getConfigValue(config, "burstMinEvents", 5);
        long windowSize = 60000L; // 1 minute windows for burst detection
        
        // Sort events by timestamp
        List<Event> sortedEvents = events.stream()
            .sorted(Comparator.comparing(Event::getTimestamp))
            .collect(Collectors.toList());
        
        if (sortedEvents.isEmpty()) return patterns;
        
        // Calculate average event rate
        long timeSpan = sortedEvents.get(sortedEvents.size() - 1).getTimestamp().toEpochMilli() - 
                       sortedEvents.get(0).getTimestamp().toEpochMilli();
        double averageRate = (double) events.size() / (timeSpan / 1000.0); // events per second
        
        // Analyze time windows for bursts
        long startTime = sortedEvents.get(0).getTimestamp().toEpochMilli();
        long endTime = sortedEvents.get(sortedEvents.size() - 1).getTimestamp().toEpochMilli();
        
        for (long windowStart = startTime; windowStart < endTime; windowStart += windowSize) {
            long windowEnd = windowStart + windowSize;

            long finalWindowStart = windowStart;
            List<Event> windowEvents = sortedEvents.stream()
                .filter(e -> {
                    long eventTime = e.getTimestamp().toEpochMilli();
                    return eventTime >= finalWindowStart && eventTime < windowEnd;
                })
                .collect(Collectors.toList());
            
            if (windowEvents.size() >= burstMinEvents) {
                double windowRate = windowEvents.size() / (windowSize / 1000.0); // events per second
                
                if (windowRate >= averageRate * burstThreshold) {
                    double intensity = windowRate / averageRate;
                    
                    DetectedPattern pattern = new DetectedPattern(
                        UUID.randomUUID().toString(),
                        "Event Burst",
                        String.format("Burst of %d events detected (%.1fx normal rate) at %s",
                            windowEvents.size(), intensity, 
                            Instant.ofEpochMilli(windowStart).toString()),
                        Math.min(1.0, intensity / 10.0), // Normalize confidence
                        PatternType.TEMPORAL,
                        Map.of(
                            "windowStart", windowStart,
                            "windowEnd", windowEnd,
                            "eventCount", windowEvents.size(),
                            "averageRate", averageRate,
                            "burstRate", windowRate,
                            "intensity", intensity,
                            "duration", windowSize
                        ),
                        windowEvents,
                        System.currentTimeMillis()
                    );
                    
                    if (pattern.confidence() >= config.confidenceThreshold()) {
                        patterns.add(pattern);
                    }
                }
            }
        }
        
        return patterns;
    }
    
    private Long findIntervalBucket(Long interval, Set<Long> existingBuckets, double tolerance) {
        for (Long bucket : existingBuckets) {
            double difference = Math.abs(interval - bucket) / (double) bucket;
            if (difference <= tolerance) {
                return bucket;
            }
        }
        return null;
    }
    
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }
    
    private String getDayName(int dayOfWeek) {
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        return days[dayOfWeek - 1];
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getConfigValue(PatternAnalysisConfig config, String key, T defaultValue) {
        return (T) config.algorithmParameters().getOrDefault(key, defaultValue);
    }
}