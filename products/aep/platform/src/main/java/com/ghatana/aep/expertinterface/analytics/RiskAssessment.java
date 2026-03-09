package com.ghatana.aep.expertinterface.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Risk assessment service for analyzing system and business risks.
 * 
 * @doc.type class
 * @doc.purpose Risk assessment and scoring
 * @doc.layer analytics
 */
public class RiskAssessment {
    private static final Logger log = LoggerFactory.getLogger(RiskAssessment.class);
    
    /**
     * Assesses risk based on provided data.
     * 
     * @param data risk assessment data
     * @return risk assessment result
     */
    public RiskAssessmentResult assessRisk(RiskAssessmentData data) {
        Objects.requireNonNull(data, "Risk assessment data is required");
        
        log.debug("Assessing risk for: {}", data.getContext());
        
        // Calculate individual risk scores
        double technicalRisk = calculateTechnicalRisk(data);
        double operationalRisk = calculateOperationalRisk(data);
        double businessRisk = calculateBusinessRisk(data);
        double securityRisk = calculateSecurityRisk(data);
        
        // Calculate overall risk score (weighted average)
        double overallRisk = (technicalRisk * 0.3) + 
                            (operationalRisk * 0.25) + 
                            (businessRisk * 0.25) + 
                            (securityRisk * 0.2);
        
        // Determine risk level
        RiskLevel riskLevel = determineRiskLevel(overallRisk);
        
        // Generate recommendations
        List<String> recommendations = generateRecommendations(
            technicalRisk, operationalRisk, businessRisk, securityRisk, riskLevel
        );
        
        RiskAssessmentResult result = new RiskAssessmentResult(
            overallRisk,
            riskLevel,
            technicalRisk,
            operationalRisk,
            businessRisk,
            securityRisk,
            recommendations,
            data.getContext()
        );
        
        log.info("Risk assessment completed: level={}, score={}", riskLevel, overallRisk);
        return result;
    }
    
    private double calculateTechnicalRisk(RiskAssessmentData data) {
        double risk = 0.0;
        
        // System complexity risk
        if (data.getSystemComplexity() > 0.7) risk += 0.3;
        else if (data.getSystemComplexity() > 0.5) risk += 0.2;
        else risk += 0.1;
        
        // Technical debt risk
        if (data.getTechnicalDebt() > 0.6) risk += 0.25;
        else if (data.getTechnicalDebt() > 0.4) risk += 0.15;
        else risk += 0.05;
        
        // Performance issues risk
        if (data.getPerformanceIssues() > 0.5) risk += 0.2;
        else if (data.getPerformanceIssues() > 0.3) risk += 0.1;
        
        // Scalability concerns
        if (data.getScalabilityConcerns() > 0.5) risk += 0.25;
        else if (data.getScalabilityConcerns() > 0.3) risk += 0.15;
        
        return Math.min(1.0, risk);
    }
    
    private double calculateOperationalRisk(RiskAssessmentData data) {
        double risk = 0.0;
        
        // Incident frequency
        if (data.getIncidentFrequency() > 0.6) risk += 0.35;
        else if (data.getIncidentFrequency() > 0.4) risk += 0.2;
        else risk += 0.1;
        
        // Mean time to recovery
        if (data.getMeanTimeToRecovery() > 0.7) risk += 0.3;
        else if (data.getMeanTimeToRecovery() > 0.5) risk += 0.2;
        else risk += 0.1;
        
        // Resource availability
        if (data.getResourceAvailability() < 0.3) risk += 0.35;
        else if (data.getResourceAvailability() < 0.5) risk += 0.2;
        
        return Math.min(1.0, risk);
    }
    
    private double calculateBusinessRisk(RiskAssessmentData data) {
        double risk = 0.0;
        
        // Revenue impact
        if (data.getRevenueImpact() > 0.7) risk += 0.4;
        else if (data.getRevenueImpact() > 0.5) risk += 0.25;
        else risk += 0.1;
        
        // Customer satisfaction
        if (data.getCustomerSatisfaction() < 0.4) risk += 0.3;
        else if (data.getCustomerSatisfaction() < 0.6) risk += 0.2;
        
        // Market competition
        if (data.getMarketCompetition() > 0.6) risk += 0.3;
        else if (data.getMarketCompetition() > 0.4) risk += 0.15;
        
        return Math.min(1.0, risk);
    }
    
    private double calculateSecurityRisk(RiskAssessmentData data) {
        double risk = 0.0;
        
        // Vulnerability count
        if (data.getVulnerabilityCount() > 0.7) risk += 0.4;
        else if (data.getVulnerabilityCount() > 0.5) risk += 0.25;
        else risk += 0.1;
        
        // Security incidents
        if (data.getSecurityIncidents() > 0.6) risk += 0.35;
        else if (data.getSecurityIncidents() > 0.4) risk += 0.2;
        
        // Compliance gaps
        if (data.getComplianceGaps() > 0.5) risk += 0.25;
        else if (data.getComplianceGaps() > 0.3) risk += 0.15;
        
        return Math.min(1.0, risk);
    }
    
    private RiskLevel determineRiskLevel(double overallRisk) {
        if (overallRisk >= 0.75) return RiskLevel.CRITICAL;
        if (overallRisk >= 0.5) return RiskLevel.HIGH;
        if (overallRisk >= 0.3) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
    
    private List<String> generateRecommendations(
            double technical, double operational, double business, double security, RiskLevel level) {
        List<String> recommendations = new ArrayList<>();
        
        if (level == RiskLevel.CRITICAL || level == RiskLevel.HIGH) {
            recommendations.add("Immediate action required to mitigate risks");
        }
        
        if (technical > 0.6) {
            recommendations.add("Address technical debt and system complexity");
            recommendations.add("Implement performance optimization measures");
        }
        
        if (operational > 0.6) {
            recommendations.add("Improve incident response procedures");
            recommendations.add("Increase resource allocation for operations");
        }
        
        if (business > 0.6) {
            recommendations.add("Focus on customer satisfaction improvements");
            recommendations.add("Develop competitive differentiation strategy");
        }
        
        if (security > 0.6) {
            recommendations.add("Conduct security audit and vulnerability assessment");
            recommendations.add("Implement enhanced security controls");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Continue monitoring risk indicators");
        }
        
        return recommendations;
    }
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public static class RiskAssessmentData {
        private final String context;
        private final double systemComplexity;
        private final double technicalDebt;
        private final double performanceIssues;
        private final double scalabilityConcerns;
        private final double incidentFrequency;
        private final double meanTimeToRecovery;
        private final double resourceAvailability;
        private final double revenueImpact;
        private final double customerSatisfaction;
        private final double marketCompetition;
        private final double vulnerabilityCount;
        private final double securityIncidents;
        private final double complianceGaps;
        
        public RiskAssessmentData(String context, double systemComplexity, double technicalDebt,
                                 double performanceIssues, double scalabilityConcerns,
                                 double incidentFrequency, double meanTimeToRecovery,
                                 double resourceAvailability, double revenueImpact,
                                 double customerSatisfaction, double marketCompetition,
                                 double vulnerabilityCount, double securityIncidents,
                                 double complianceGaps) {
            this.context = context;
            this.systemComplexity = systemComplexity;
            this.technicalDebt = technicalDebt;
            this.performanceIssues = performanceIssues;
            this.scalabilityConcerns = scalabilityConcerns;
            this.incidentFrequency = incidentFrequency;
            this.meanTimeToRecovery = meanTimeToRecovery;
            this.resourceAvailability = resourceAvailability;
            this.revenueImpact = revenueImpact;
            this.customerSatisfaction = customerSatisfaction;
            this.marketCompetition = marketCompetition;
            this.vulnerabilityCount = vulnerabilityCount;
            this.securityIncidents = securityIncidents;
            this.complianceGaps = complianceGaps;
        }
        
        public String getContext() { return context; }
        public double getSystemComplexity() { return systemComplexity; }
        public double getTechnicalDebt() { return technicalDebt; }
        public double getPerformanceIssues() { return performanceIssues; }
        public double getScalabilityConcerns() { return scalabilityConcerns; }
        public double getIncidentFrequency() { return incidentFrequency; }
        public double getMeanTimeToRecovery() { return meanTimeToRecovery; }
        public double getResourceAvailability() { return resourceAvailability; }
        public double getRevenueImpact() { return revenueImpact; }
        public double getCustomerSatisfaction() { return customerSatisfaction; }
        public double getMarketCompetition() { return marketCompetition; }
        public double getVulnerabilityCount() { return vulnerabilityCount; }
        public double getSecurityIncidents() { return securityIncidents; }
        public double getComplianceGaps() { return complianceGaps; }
    }
    
    public static class RiskAssessmentResult {
        private final double overallRisk;
        private final RiskLevel riskLevel;
        private final double technicalRisk;
        private final double operationalRisk;
        private final double businessRisk;
        private final double securityRisk;
        private final List<String> recommendations;
        private final String context;
        
        public RiskAssessmentResult(double overallRisk, RiskLevel riskLevel,
                                   double technicalRisk, double operationalRisk,
                                   double businessRisk, double securityRisk,
                                   List<String> recommendations, String context) {
            this.overallRisk = overallRisk;
            this.riskLevel = riskLevel;
            this.technicalRisk = technicalRisk;
            this.operationalRisk = operationalRisk;
            this.businessRisk = businessRisk;
            this.securityRisk = securityRisk;
            this.recommendations = recommendations;
            this.context = context;
        }
        
        public double getOverallRisk() { return overallRisk; }
        public RiskLevel getRiskLevel() { return riskLevel; }
        public double getTechnicalRisk() { return technicalRisk; }
        public double getOperationalRisk() { return operationalRisk; }
        public double getBusinessRisk() { return businessRisk; }
        public double getSecurityRisk() { return securityRisk; }
        public List<String> getRecommendations() { return recommendations; }
        public String getContext() { return context; }
    }
}
