package com.ghatana.stream.wiring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Day 31: Stream Wiring - Simplified concept demonstration.
 * Connects pattern match events to learning infrastructure.
 * 
 * This demonstrates the core concept without full domain modeling.
 */
public class StreamWiringDemo {
    private static final Logger logger = LoggerFactory.getLogger(StreamWiringDemo.class);
    
    private final Map<String, Instant> processedMatches = new ConcurrentHashMap<>();
    
    /**
     * Processes pattern matches and demonstrates the wiring concept.
     */
    public void processMatches(List<String> matchIds, List<String> patternIds) {
        logger.info("Day 31: Processing {} pattern matches for learning", matchIds.size());
        
        for (int i = 0; i < matchIds.size() && i < patternIds.size(); i++) {
            String matchId = matchIds.get(i);
            String patternId = patternIds.get(i);
            
            // Simulate pattern match processing and learning event creation
            processMatch(matchId, patternId);
            
            // Track processed matches for deduplication
            processedMatches.put(matchId, Instant.now());
        }
        
        logger.info("Successfully processed {} matches, total processed: {}", 
                   matchIds.size(), processedMatches.size());
    }
    
    /**
     * Simulates processing a single pattern match and creating learning data.
     */
    private void processMatch(String matchId, String patternId) {
        logger.debug("Processing match {} for pattern {}", matchId, patternId);
        
        // Simulate learning event data
        Map<String, Object> learningData = Map.of(
            "matchId", matchId,
            "patternId", patternId,
            "timestamp", Instant.now(),
            "confidence", 0.8,
            "processingTime", 100L,
            "outcome", "SUCCESS"
        );
        
        // This is where we would:
        // 1. Create LearningEvent from match data
        // 2. Store in PatternLearningStore
        // 3. Update PatternScoringEngine
        
        logger.trace("Created learning data for match {}: {}", matchId, learningData);
    }
    
    /**
     * Demonstrates batch processing with deduplication.
     */
    public void processBatch(String batchId, List<String> matchIds, List<String> patternIds) {
        logger.info("Day 31: Processing batch {} with {} matches", batchId, matchIds.size());
        
        // Deduplicate matches
        List<String> uniqueMatchIds = new ArrayList<>();
        List<String> uniquePatternIds = new ArrayList<>();
        
        for (int i = 0; i < matchIds.size(); i++) {
            String matchId = matchIds.get(i);
            if (!processedMatches.containsKey(matchId)) {
                uniqueMatchIds.add(matchId);
                uniquePatternIds.add(patternIds.get(i));
            }
        }
        
        logger.debug("Batch {}: {} unique matches after deduplication", 
                    batchId, uniqueMatchIds.size());
        
        // Process unique matches
        processMatches(uniqueMatchIds, uniquePatternIds);
        
        logger.info("Completed batch {} processing", batchId);
    }
    
    /**
     * Cleanup old processed matches to prevent memory leaks.
     */
    public void cleanup(Instant cutoffTime) {
        int beforeSize = processedMatches.size();
        processedMatches.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoffTime));
        int afterSize = processedMatches.size();
        
        if (beforeSize != afterSize) {
            logger.info("Cleaned up {} old processed match entries", beforeSize - afterSize);
        }
    }
    
    /**
     * Returns processing statistics.
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "processedMatches", processedMatches.size(),
            "component", "StreamWiringDemo",
            "day", 31,
            "purpose", "Connect pattern detection to scoring engine"
        );
    }
}