package com.ghatana.validation.ai.detectors;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.validation.ai.AIPatternDetectionService.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects sequential patterns in event streams.
 * Consolidated from event-core sequence detection capabilities.
 
 *
 * @doc.type class
 * @doc.purpose Sequence pattern detector
 * @doc.layer core
 * @doc.pattern Component
*/
public class SequencePatternDetector implements PatternDetector {
    
    @Override
    public List<DetectedPattern> detect(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Sort events by timestamp
        List<Event> sortedEvents = events.stream()
            .sorted(Comparator.comparing(e -> e.getTime().getOccurrenceTime().start().toInstant()))
            .collect(Collectors.toList());
        
        // Detect repeating sequences
        patterns.addAll(detectRepeatingSequences(sortedEvents, config));
        
        // Detect event type sequences
        patterns.addAll(detectEventTypeSequences(sortedEvents, config));
        
        return patterns;
    }
    
    @Override
    public PatternType getPatternType() {
        return PatternType.SEQUENCE;
    }
    
    @Override
    public Map<String, String> getConfigurationRequirements() {
        Map<String, String> requirements = new HashMap<>();
        requirements.put("minSequenceLength", "Minimum length of sequence to detect (default: 3)");
        requirements.put("maxGapMs", "Maximum time gap between sequence events in milliseconds (default: 60000)");
        requirements.put("minOccurrences", "Minimum number of sequence occurrences (default: 2)");
        return requirements;
    }
    
    private List<DetectedPattern> detectRepeatingSequences(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        int minLength = getConfigValue(config, "minSequenceLength", 3);
        long maxGapMs = getConfigValue(config, "maxGapMs", 60000L);
        int minOccurrences = getConfigValue(config, "minOccurrences", 2);
        
        // Sliding window approach to find repeating sequences
        for (int seqLength = minLength; seqLength <= Math.min(10, events.size()); seqLength++) {
            Map<String, List<List<Event>>> sequenceOccurrences = new HashMap<>();
            
            for (int i = 0; i <= events.size() - seqLength; i++) {
                List<Event> sequence = events.subList(i, i + seqLength);
                
                // Check if sequence events are within max gap
                if (isValidSequence(sequence, maxGapMs)) {
                    String sequenceSignature = createSequenceSignature(sequence);
                    sequenceOccurrences.computeIfAbsent(sequenceSignature, k -> new ArrayList<>()).add(sequence);
                }
            }
            
            // Convert frequent sequences to patterns
            for (Map.Entry<String, List<List<Event>>> entry : sequenceOccurrences.entrySet()) {
                if (entry.getValue().size() >= minOccurrences) {
                    DetectedPattern pattern = createSequencePattern(entry.getKey(), entry.getValue(), seqLength);
                    if (pattern.confidence() >= config.confidenceThreshold()) {
                        patterns.add(pattern);
                    }
                }
            }
        }
        
        return patterns;
    }
    
    private List<DetectedPattern> detectEventTypeSequences(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Create sequences based on event types
        List<String> eventTypeSequence = events.stream()
            .map(Event::getType)
            .collect(Collectors.toList());
        
        // Find common subsequences
        Map<String, Integer> subsequenceCount = new HashMap<>();
        
        for (int length = 2; length <= Math.min(5, eventTypeSequence.size()); length++) {
            for (int i = 0; i <= eventTypeSequence.size() - length; i++) {
                String subsequence = String.join("->", eventTypeSequence.subList(i, i + length));
                subsequenceCount.put(subsequence, subsequenceCount.getOrDefault(subsequence, 0) + 1);
            }
        }
        
        // Convert frequent subsequences to patterns
        int minOccurrences = getConfigValue(config, "minOccurrences", 2);
        for (Map.Entry<String, Integer> entry : subsequenceCount.entrySet()) {
            if (entry.getValue() >= minOccurrences) {
                double confidence = (double) entry.getValue() / events.size();
                if (confidence >= config.confidenceThreshold()) {
                    DetectedPattern pattern = new DetectedPattern(
                        UUID.randomUUID().toString(),
                        "Event Type Sequence",
                        "Repeating sequence of event types: " + entry.getKey(),
                        confidence,
                        PatternType.SEQUENCE,
                        Map.of("sequence", entry.getKey(), "occurrences", entry.getValue()),
                        findEventsForTypeSequence(events, entry.getKey()),
                        System.currentTimeMillis()
                    );
                    patterns.add(pattern);
                }
            }
        }
        
        return patterns;
    }
    
    private boolean isValidSequence(List<Event> sequence, long maxGapMs) {
        for (int i = 1; i < sequence.size(); i++) {
            long gap = sequence.get(i).getTime().getOccurrenceTime().start().toInstant().toEpochMilli() - 
                      sequence.get(i-1).getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
            if (gap > maxGapMs) {
                return false;
            }
        }
        return true;
    }
    
    private String createSequenceSignature(List<Event> sequence) {
        return sequence.stream()
            .map(e -> e.getType() + ":" + extractKeyAttributes(e))
            .collect(Collectors.joining("->"));
    }
    
    private String extractKeyAttributes(Event event) {
        // Extract key attributes for sequence matching
        // This is a simplified implementation that uses payload keys
        // Note: In a real implementation, you might want to include specific known payload fields
        List<String> payloadFields = List.of("type", "severity", "message"); // Example fields
        return payloadFields.stream()
            .filter(field -> event.getPayload(field) != null)
            .limit(3) // Top 3 attributes
            .collect(Collectors.joining(","));
    }
    
    private DetectedPattern createSequencePattern(String signature, List<List<Event>> occurrences, int length) {
        double confidence = Math.min(1.0, (double) occurrences.size() / (10.0 - length)); // Adjust confidence based on frequency and length
        
        List<Event> allMatchingEvents = occurrences.stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
        
        return new DetectedPattern(
            UUID.randomUUID().toString(),
            "Repeating Sequence",
            "Sequence pattern: " + signature,
            confidence,
            PatternType.SEQUENCE,
            Map.of(
                "signature", signature,
                "length", length,
                "occurrences", occurrences.size(),
                "averageGap", calculateAverageGap(occurrences)
            ),
            allMatchingEvents,
            System.currentTimeMillis()
        );
    }
    
    private List<Event> findEventsForTypeSequence(List<Event> events, String typeSequence) {
        // Find events that match the type sequence
        String[] types = typeSequence.split("->");
        List<Event> matchingEvents = new ArrayList<>();
        
        for (int i = 0; i <= events.size() - types.length; i++) {
            boolean matches = true;
            for (int j = 0; j < types.length; j++) {
                if (!events.get(i + j).getType().equals(types[j])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                matchingEvents.addAll(events.subList(i, i + types.length));
            }
        }
        
        return matchingEvents;
    }
    
    private double calculateAverageGap(List<List<Event>> occurrences) {
        double totalGap = 0;
        int gapCount = 0;
        
        for (List<Event> sequence : occurrences) {
            for (int i = 1; i < sequence.size(); i++) {
                totalGap += sequence.get(i).getTimestamp().toEpochMilli() - 
                           sequence.get(i-1).getTimestamp().toEpochMilli();
                gapCount++;
            }
        }
        
        return gapCount > 0 ? totalGap / gapCount : 0;
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getConfigValue(PatternAnalysisConfig config, String key, T defaultValue) {
        return (T) config.algorithmParameters().getOrDefault(key, defaultValue);
    }
}