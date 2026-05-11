package com.ghatana.datacloud.spi;

import java.time.Instant;
import java.util.Map;

/**
 * P1-07: Evidence-first and policy-gated AI/automation outputs.
 * 
 * <p>This interface defines the evidence and policy metadata that must be
 * returned for every AI/automation response to ensure transparency and governance.
 *
 * @doc.type interface
 * @doc.purpose Evidence-first and policy-gated AI/automation outputs
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public interface EvidenceBasedAiOutput {
    
    /**
     * Gets the input scope used for the AI/automation decision.
     * @return description of the input scope
     */
    String getInputScope();
    
    /**
     * Gets the source evidence for the decision.
     * @return evidence identifiers and references
     */
    Map<String, Object> getSourceEvidence();
    
    /**
     * Gets the confidence level of the AI/automation decision.
     * @return confidence score (0.0 to 1.0)
     */
    double getConfidence();
    
    /**
     * Gets the risk level of the AI/automation decision.
     * @return risk level classification
     */
    RiskLevel getRiskLevel();
    
    /**
     * Gets the policy decision for this output.
     * @return whether the output was approved by policy
     */
    PolicyDecision getPolicyDecision();
    
    /**
     * Gets the freshness of the data used for the decision.
     * @return timestamp of the most recent data
     */
    Instant getDataFreshness();
    
    /**
     * Gets the lineage/provenance of the decision.
     * @return lineage information
     */
    Map<String, Object> getLineage();
    
    /**
     * Gets the human review requirement.
     * @return whether human review is required
     */
    boolean requiresHumanReview();
    
    /**
     * Gets the trace ID for the AI/automation execution.
     * @return trace identifier
     */
    String getTraceId();
    
    /**
     * Gets the audit ID for this decision.
     * @return audit identifier
     */
    String getAuditId();
    
    /**
     * Risk level enumeration.
     */
    enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Policy decision enumeration.
     */
    enum PolicyDecision {
        APPROVED,
        REJECTED,
        CONDITIONAL,
        ESCALATED
    }
    
    /**
     * Record implementation of EvidenceBasedAiOutput.
     */
    record EvidenceBasedAiOutputRecord(
        String inputScope,
        Map<String, Object> sourceEvidence,
        double confidence,
        RiskLevel riskLevel,
        PolicyDecision policyDecision,
        Instant dataFreshness,
        Map<String, Object> lineage,
        boolean requiresHumanReview,
        String traceId,
        String auditId
    ) implements EvidenceBasedAiOutput {
        
        @Override
        public String getInputScope() {
            return inputScope;
        }
        
        @Override
        public Map<String, Object> getSourceEvidence() {
            return sourceEvidence;
        }
        
        @Override
        public double getConfidence() {
            return confidence;
        }
        
        @Override
        public RiskLevel getRiskLevel() {
            return riskLevel;
        }
        
        @Override
        public PolicyDecision getPolicyDecision() {
            return policyDecision;
        }
        
        @Override
        public Instant getDataFreshness() {
            return dataFreshness;
        }
        
        @Override
        public Map<String, Object> getLineage() {
            return lineage;
        }
        
        @Override
        public boolean requiresHumanReview() {
            return requiresHumanReview;
        }
        
        @Override
        public String getTraceId() {
            return traceId;
        }
        
        @Override
        public String getAuditId() {
            return auditId;
        }
    }
}
