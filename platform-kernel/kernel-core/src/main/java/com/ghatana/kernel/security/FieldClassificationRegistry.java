/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Canonical field classification registry for PHI data sensitivity.
 *
 * <p>Provides a centralized, canonical source of truth for field sensitivity
 * classifications across all products using the Kernel. This replaces ad-hoc
 * substring matching with explicit, maintainable field definitions.</p>
 *
 * <p>Field classifications:</p>
 * <ul>
 *   <li>RESTRICTED: Highest sensitivity - requires explicit policy, no caching/export</li>
 *   <li>SENSITIVE: High sensitivity - requires consent, limited access</li>
 *   <li>CONFIDENTIAL: Medium sensitivity - role-based access</li>
 *   <li>STANDARD: Normal sensitivity - standard access controls</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Canonical field classification registry for PHI sensitivity
 * @doc.layer core
 * @doc.pattern Registry
 * @since 1.0.0
 */
public final class FieldClassificationRegistry {

    /**
     * Field sensitivity classification levels.
     */
    public enum FieldSensitivity {
        /** Highest sensitivity - requires explicit policy, no caching/export */
        RESTRICTED,
        /** High sensitivity - requires consent, limited access */
        SENSITIVE,
        /** Medium sensitivity - role-based access */
        CONFIDENTIAL,
        /** Normal sensitivity - standard access controls */
        STANDARD
    }

    /**
     * PHI field categories for classification.
     */
    public enum FieldCategory {
        /** Mental health and psychiatric information */
        MENTAL_HEALTH,
        /** Substance use and abuse history */
        SUBSTANCE_USE,
        /** Genetic information and testing */
        GENETIC,
        /** Reproductive health information */
        REPRODUCTIVE_HEALTH,
        /** HIV/AIDS and STD status */
        INFECTIOUS_DISEASE,
        /** Sexual health information */
        SEXUAL_HEALTH,
        /** Standard demographic information */
        DEMOGRAPHIC,
        /** Clinical observations and measurements */
        CLINICAL,
        /** Medication and treatment information */
        TREATMENT,
        /** Other PHI not in specific categories */
        OTHER
    }

    private static final Map<String, FieldClassification> REGISTRY = new LinkedHashMap<>();

    static {
        // RESTRICTED fields - highest sensitivity
        registerRestrictedFields();
        
        // SENSITIVE fields - high sensitivity
        registerSensitiveFields();
        
        // CONFIDENTIAL fields - medium sensitivity
        registerConfidentialFields();
        
        // STANDARD fields - normal sensitivity
        registerStandardFields();
    }

    private static void registerRestrictedFields() {
        // Mental health
        register("mentalHealthStatus", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("psychiatricHistory", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("psychiatricDiagnosis", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("mentalHealthDiagnosis", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("psychiatricMedication", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("mentalHealthTreatment", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("suicideRisk", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("selfHarmHistory", FieldCategory.MENTAL_HEALTH, FieldSensitivity.RESTRICTED);
        
        // Substance use
        register("substanceUseHistory", FieldCategory.SUBSTANCE_USE, FieldSensitivity.RESTRICTED);
        register("drugAbuseHistory", FieldCategory.SUBSTANCE_USE, FieldSensitivity.RESTRICTED);
        register("alcoholAbuseHistory", FieldCategory.SUBSTANCE_USE, FieldSensitivity.RESTRICTED);
        register("addictionTreatment", FieldCategory.SUBSTANCE_USE, FieldSensitivity.RESTRICTED);
        register("rehabilitationHistory", FieldCategory.SUBSTANCE_USE, FieldSensitivity.RESTRICTED);
        
        // Genetic information
        register("geneticInformation", FieldCategory.GENETIC, FieldSensitivity.RESTRICTED);
        register("geneticTestResults", FieldCategory.GENETIC, FieldSensitivity.RESTRICTED);
        register("geneticScreening", FieldCategory.GENETIC, FieldSensitivity.RESTRICTED);
        register("familyGeneticHistory", FieldCategory.GENETIC, FieldSensitivity.RESTRICTED);
        register("geneticPredisposition", FieldCategory.GENETIC, FieldSensitivity.RESTRICTED);
        
        // Reproductive health
        register("reproductiveHealth", FieldCategory.REPRODUCTIVE_HEALTH, FieldSensitivity.RESTRICTED);
        register("pregnancyHistory", FieldCategory.REPRODUCTIVE_HEALTH, FieldSensitivity.RESTRICTED);
        register("abortionHistory", FieldCategory.REPRODUCTIVE_HEALTH, FieldSensitivity.RESTRICTED);
        register("fertilityTreatment", FieldCategory.REPRODUCTIVE_HEALTH, FieldSensitivity.RESTRICTED);
        register("contraceptionHistory", FieldCategory.REPRODUCTIVE_HEALTH, FieldSensitivity.RESTRICTED);
        
        // Infectious disease (HIV/STD)
        register("hivStatus", FieldCategory.INFECTIOUS_DISEASE, FieldSensitivity.RESTRICTED);
        register("aidsStatus", FieldCategory.INFECTIOUS_DISEASE, FieldSensitivity.RESTRICTED);
        register("stdStatus", FieldCategory.INFECTIOUS_DISEASE, FieldSensitivity.RESTRICTED);
        register("sexuallyTransmittedDisease", FieldCategory.INFECTIOUS_DISEASE, FieldSensitivity.RESTRICTED);
        register("hepatitisStatus", FieldCategory.INFECTIOUS_DISEASE, FieldSensitivity.RESTRICTED);
        
        // Sexual health
        register("sexualHistory", FieldCategory.SEXUAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("sexualOrientation", FieldCategory.SEXUAL_HEALTH, FieldSensitivity.RESTRICTED);
        register("genderIdentity", FieldCategory.SEXUAL_HEALTH, FieldSensitivity.RESTRICTED);
    }

    private static void registerSensitiveFields() {
        // Clinical sensitive fields
        register("diagnosis", FieldCategory.CLINICAL, FieldSensitivity.SENSITIVE);
        register("prognosis", FieldCategory.CLINICAL, FieldSensitivity.SENSITIVE);
        register("clinicalNotes", FieldCategory.CLINICAL, FieldSensitivity.SENSITIVE);
        register("physicianNotes", FieldCategory.CLINICAL, FieldSensitivity.SENSITIVE);
        register("treatmentPlan", FieldCategory.TREATMENT, FieldSensitivity.SENSITIVE);
        
        // Social determinants
        register("socialHistory", FieldCategory.OTHER, FieldSensitivity.SENSITIVE);
        register("livingSituation", FieldCategory.OTHER, FieldSensitivity.SENSITIVE);
        register("employmentStatus", FieldCategory.OTHER, FieldSensitivity.SENSITIVE);
    }

    private static void registerConfidentialFields() {
        // Clinical confidential fields
        register("medicationList", FieldCategory.TREATMENT, FieldSensitivity.CONFIDENTIAL);
        register("allergies", FieldCategory.CLINICAL, FieldSensitivity.CONFIDENTIAL);
        register("labResults", FieldCategory.CLINICAL, FieldSensitivity.CONFIDENTIAL);
        register("vitalSigns", FieldCategory.CLINICAL, FieldSensitivity.CONFIDENTIAL);
        register("immunizationRecord", FieldCategory.TREATMENT, FieldSensitivity.CONFIDENTIAL);
        
        // Demographic confidential fields
        register("contactInformation", FieldCategory.DEMOGRAPHIC, FieldSensitivity.CONFIDENTIAL);
        register("emergencyContact", FieldCategory.DEMOGRAPHIC, FieldSensitivity.CONFIDENTIAL);
        register("insuranceInformation", FieldCategory.DEMOGRAPHIC, FieldSensitivity.CONFIDENTIAL);
    }

    private static void registerStandardFields() {
        // Standard demographic fields
        register("name", FieldCategory.DEMOGRAPHIC, FieldSensitivity.STANDARD);
        register("dateOfBirth", FieldCategory.DEMOGRAPHIC, FieldSensitivity.STANDARD);
        register("age", FieldCategory.DEMOGRAPHIC, FieldSensitivity.STANDARD);
        register("gender", FieldCategory.DEMOGRAPHIC, FieldSensitivity.STANDARD);
        register("address", FieldCategory.DEMOGRAPHIC, FieldSensitivity.STANDARD);
        register("phoneNumber", FieldCategory.DEMOGRAPHIC, FieldSensitivity.STANDARD);
        register("email", FieldCategory.DEMOGRAPHIC, FieldSensitivity.STANDARD);
        
        // Standard clinical fields
        register("bloodType", FieldCategory.CLINICAL, FieldSensitivity.STANDARD);
        register("height", FieldCategory.CLINICAL, FieldSensitivity.STANDARD);
        register("weight", FieldCategory.CLINICAL, FieldSensitivity.STANDARD);
        register("bmi", FieldCategory.CLINICAL, FieldSensitivity.STANDARD);
    }

    private static void register(String fieldName, FieldCategory category, FieldSensitivity sensitivity) {
        FieldClassification classification = new FieldClassification(fieldName, category, sensitivity);
        REGISTRY.put(fieldName.toLowerCase(), classification);
    }

    /**
     * Gets the classification for a field name.
     *
     * @param fieldName the field name to classify
     * @return the field classification, or null if not found
     */
    public static FieldClassification classify(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }
        return REGISTRY.get(fieldName.toLowerCase());
    }

    /**
     * Checks if a field is restricted (highest sensitivity).
     *
     * @param fieldName the field name to check
     * @return true if the field is restricted
     */
    public static boolean isRestricted(String fieldName) {
        FieldClassification classification = classify(fieldName);
        return classification != null && classification.sensitivity() == FieldSensitivity.RESTRICTED;
    }

    /**
     * Checks if a field is sensitive or restricted.
     *
     * @param fieldName the field name to check
     * @return true if the field is sensitive or restricted
     */
    public static boolean isSensitive(String fieldName) {
        FieldClassification classification = classify(fieldName);
        if (classification == null) {
            return false;
        }
        return classification.sensitivity() == FieldSensitivity.RESTRICTED 
            || classification.sensitivity() == FieldSensitivity.SENSITIVE;
    }

    /**
     * Gets all registered field names for a category.
     *
     * @param category the field category
     * @return immutable set of field names in the category
     */
    public static Set<String> getFieldsByCategory(FieldCategory category) {
        return REGISTRY.entrySet().stream()
            .filter(entry -> entry.getValue().category() == category)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Gets all registered field names for a sensitivity level.
     *
     * @param sensitivity the field sensitivity level
     * @return immutable set of field names with the sensitivity
     */
    public static Set<String> getFieldsBySensitivity(FieldSensitivity sensitivity) {
        return REGISTRY.entrySet().stream()
            .filter(entry -> entry.getValue().sensitivity() == sensitivity)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Gets all registered field classifications.
     *
     * @return immutable map of field names to classifications
     */
    public static Map<String, FieldClassification> getAllClassifications() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * Field classification record.
     *
     * @param fieldName the field name
     * @param category the field category
     * @param sensitivity the field sensitivity level
     */
    public record FieldClassification(
        String fieldName,
        FieldCategory category,
        FieldSensitivity sensitivity
    ) {
        public FieldClassification {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName must not be blank");
            }
        }
    }

    private FieldClassificationRegistry() {
        // Utility class - prevent instantiation
    }
}
