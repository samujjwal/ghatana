package com.ghatana.core.pattern.learning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Evolves patterns using genetic algorithms and machine learning.
 *
 * @doc.type class
 * @doc.purpose Pattern evolution for adaptive improvement
 * @doc.layer core
 * @doc.pattern Learning
 */
public class PatternEvolutionEngine {

    private static final Logger logger = LoggerFactory.getLogger(PatternEvolutionEngine.class);

    private final LearningEngineConfig.EvolutionConfig config;
    private final Random random;

    public PatternEvolutionEngine(LearningEngineConfig.EvolutionConfig config) {
        this.config = config;
        this.random = new Random();
    }

    /**
     * Evolve patterns using genetic algorithms.
     */
    public List<LearnedPattern> evolvePatterns(List<LearnedPattern> patterns) {
        if (patterns.size() < 2) {
            return patterns;
        }

        try {
            // Initialize population
            List<LearnedPattern> population = initializePopulation(patterns);
            
            // Evolve for specified generations
            for (int generation = 0; generation < config.getMaxGenerations(); generation++) {
                population = evolveGeneration(population);
                
                // Log progress
                if (generation % 10 == 0) {
                    double avgFitness = calculateAverageFitness(population);
                    logger.debug("Evolution generation {}: average fitness = {:.3f}", generation, avgFitness);
                }
            }
            
            // Return best evolved patterns
            return selectBestPatterns(population);
            
        } catch (Exception e) {
            logger.error("Error during pattern evolution: {}", e.getMessage());
            return patterns;
        }
    }

    private List<LearnedPattern> initializePopulation(List<LearnedPattern> patterns) {
        List<LearnedPattern> population = new ArrayList<>();
        
        // Add original patterns
        population.addAll(patterns);
        
        // Generate additional patterns through mutation
        while (population.size() < config.getPopulationSize()) {
            LearnedPattern parent = selectParent(patterns);
            LearnedPattern mutated = mutatePattern(parent);
            population.add(mutated);
        }
        
        return population;
    }

    private List<LearnedPattern> evolveGeneration(List<LearnedPattern> population) {
        List<LearnedPattern> newPopulation = new ArrayList<>();
        
        // Elitism: keep best patterns
        int eliteCount = (int) (population.size() * 0.1);
        newPopulation.addAll(selectBestPatterns(population).subList(0, Math.min(eliteCount, population.size())));
        
        // Generate offspring through crossover and mutation
        while (newPopulation.size() < config.getPopulationSize()) {
            if (random.nextDouble() < config.getCrossoverRate()) {
                // Crossover
                LearnedPattern parent1 = selectParent(population);
                LearnedPattern parent2 = selectParent(population);
                LearnedPattern offspring = crossoverPatterns(parent1, parent2);
                newPopulation.add(offspring);
            } else {
                // Mutation
                LearnedPattern parent = selectParent(population);
                LearnedPattern mutated = mutatePattern(parent);
                newPopulation.add(mutated);
            }
        }
        
        return newPopulation;
    }

    private LearnedPattern selectParent(List<LearnedPattern> population) {
        // Tournament selection
        int tournamentSize = 3;
        List<LearnedPattern> tournament = new ArrayList<>();
        
        for (int i = 0; i < tournamentSize; i++) {
            tournament.add(population.get(random.nextInt(population.size())));
        }
        
        return tournament.stream()
                .max(Comparator.comparingDouble(LearnedPattern::getFitnessScore))
                .orElse(tournament.get(0));
    }

    private LearnedPattern crossoverPatterns(LearnedPattern parent1, LearnedPattern parent2) {
        // Simple crossover: combine features from both parents
        Map<String, Object> combinedFeatures = new HashMap<>();
        combinedFeatures.putAll(parent1.getFeatures());
        combinedFeatures.putAll(parent2.getFeatures());
        
        // Create new signature
        String newSignature = "EVOLVED_" + parent1.getSignature() + "_x_" + parent2.getSignature();
        
        // Average confidence and support
        double newConfidence = (parent1.getConfidence() + parent2.getConfidence()) / 2.0;
        long newSupport = (parent1.getSupport() + parent2.getSupport()) / 2;
        
        return LearnedPattern.builder()
                .signature(newSignature)
                .patternType(parent1.getPatternType()) // Keep parent1's type
                .confidence(newConfidence)
                .support(newSupport)
                .features(combinedFeatures)
                .build();
    }

    private LearnedPattern mutatePattern(LearnedPattern pattern) {
        if (random.nextDouble() > config.getMutationRate()) {
            return pattern; // No mutation
        }
        
        Map<String, Object> mutatedFeatures = new HashMap<>(pattern.getFeatures());
        
        // Apply random mutations
        switch (random.nextInt(4)) {
            case 0:
                // Add random feature
                mutatedFeatures.put("mutated_feature_" + random.nextInt(1000), random.nextDouble());
                break;
            case 1:
                // Modify existing feature
                if (!mutatedFeatures.isEmpty()) {
                    String keyToModify = new ArrayList<>(mutatedFeatures.keySet()).get(random.nextInt(mutatedFeatures.size()));
                    mutatedFeatures.put(keyToModify, random.nextDouble());
                }
                break;
            case 2:
                // Remove feature
                if (!mutatedFeatures.isEmpty()) {
                    String keyToRemove = new ArrayList<>(mutatedFeatures.keySet()).get(random.nextInt(mutatedFeatures.size()));
                    mutatedFeatures.remove(keyToRemove);
                }
                break;
            case 3:
                // Change pattern type
                LearnedPatternType[] types = LearnedPatternType.values();
                LearnedPatternType newType = types[random.nextInt(types.length)];
                return LearnedPattern.builder()
                        .signature("MUTATED_" + pattern.getSignature())
                        .patternType(newType)
                        .confidence(pattern.getConfidence() * (0.8 + random.nextDouble() * 0.4)) // Vary confidence
                        .support(pattern.getSupport())
                        .features(mutatedFeatures)
                        .build();
        }
        
        return LearnedPattern.builder()
                .signature("MUTATED_" + pattern.getSignature())
                .patternType(pattern.getPatternType())
                .confidence(pattern.getConfidence() * (0.8 + random.nextDouble() * 0.4)) // Vary confidence
                .support(pattern.getSupport())
                .features(mutatedFeatures)
                .build();
    }

    private List<LearnedPattern> selectBestPatterns(List<LearnedPattern> population) {
        return population.stream()
                .sorted(Comparator.comparingDouble(LearnedPattern::getFitnessScore).reversed())
                .limit(Math.max(1, population.size() / 2))
                .collect(Collectors.toList());
    }

    private double calculateAverageFitness(List<LearnedPattern> population) {
        return population.stream()
                .mapToDouble(LearnedPattern::getFitnessScore)
                .average()
                .orElse(0.0);
    }

    /**
     * Additional evolution methods for specialized pattern types.
     */
    public List<LearnedPattern> evolveSequentialPatterns(List<LearnedPattern> sequentialPatterns) {
        return sequentialPatterns.stream()
                .map(this::evolveSequentialPattern)
                .collect(Collectors.toList());
    }

    public List<LearnedPattern> evolveFrequencyPatterns(List<LearnedPattern> frequencyPatterns) {
        return frequencyPatterns.stream()
                .map(this::evolveFrequencyPattern)
                .collect(Collectors.toList());
    }

    private LearnedPattern evolveSequentialPattern(LearnedPattern pattern) {
        // Specialized evolution for sequential patterns
        Map<String, Object> evolvedFeatures = new HashMap<>(pattern.getFeatures());
        
        // Evolve sequence parameters
        if (evolvedFeatures.containsKey("sequence_length")) {
            int currentLength = (Integer) evolvedFeatures.get("sequence_length");
            int newLength = Math.max(2, Math.min(10, currentLength + random.nextInt(3) - 1)); // +/- 1
            evolvedFeatures.put("sequence_length", newLength);
        }
        
        if (evolvedFeatures.containsKey("time_window")) {
            long currentTimeWindow = (Long) evolvedFeatures.get("time_window");
            long newTimeWindow = Math.max(60000, currentTimeWindow + (random.nextInt(5) - 2) * 30000); // +/- 2 minutes
            evolvedFeatures.put("time_window", newTimeWindow);
        }
        
        return LearnedPattern.builder()
                .signature("EVOLVED_SEQ_" + pattern.getSignature())
                .patternType(LearnedPatternType.SEQUENTIAL)
                .confidence(pattern.getConfidence() * (0.9 + random.nextDouble() * 0.2))
                .support(pattern.getSupport())
                .features(evolvedFeatures)
                .build();
    }

    private LearnedPattern evolveFrequencyPattern(LearnedPattern pattern) {
        // Specialized evolution for frequency patterns
        Map<String, Object> evolvedFeatures = new HashMap<>(pattern.getFeatures());
        
        // Evolve frequency parameters
        if (evolvedFeatures.containsKey("frequency_threshold")) {
            double currentThreshold = (Double) evolvedFeatures.get("frequency_threshold");
            double newThreshold = Math.max(0.1, Math.min(1.0, currentThreshold + (random.nextDouble() - 0.5) * 0.2));
            evolvedFeatures.put("frequency_threshold", newThreshold);
        }
        
        if (evolvedFeatures.containsKey("count_threshold")) {
            int currentThreshold = (Integer) evolvedFeatures.get("count_threshold");
            int newThreshold = Math.max(1, currentThreshold + random.nextInt(5) - 2);
            evolvedFeatures.put("count_threshold", newThreshold);
        }
        
        return LearnedPattern.builder()
                .signature("EVOLVED_FREQ_" + pattern.getSignature())
                .patternType(LearnedPatternType.FREQUENCY)
                .confidence(pattern.getConfidence() * (0.9 + random.nextDouble() * 0.2))
                .support(pattern.getSupport())
                .features(evolvedFeatures)
                .build();
    }
}
