/**
 * Compliance Utilities Index
 *
 * Central export for all compliance management features:
 * - Policy management
 * - Audit trails
 * - Framework controls
 * - Assessment and remediation
 */

// Policy Management
export {
  CompliancePolicyManager,
  CompliancePolicy,
  PolicyStatus,
  PolicyAcknowledgment,
  policyManager,
} from './CompliancePolicy';

// Audit Trail
export {
  AuditTrail,
  AuditEvent,
  AuditFilter,
  globalAuditTrail,
  auditLog,
} from './AuditTrail';

// Frameworks
export {
  ComplianceFramework,
  ControlDefinition,
  SOC2_CONTROLS,
  ISO_27001_CONTROLS,
  HIPAA_CONTROLS,
  getFrameworkControls,
  getControlRequirements,
  calculateFrameworkCompliance,
  calculateWeightedCompliance,
} from './ComplianceFrameworks';

// Assessment and Remediation
export {
  ComplianceAssessmentManager,
  Assessment,
  AssessmentResult,
  ComplianceGap,
  RemediationPlan,
  RemediationStep,
  assessmentManager,
} from './ComplianceAssessment';

/**
 * Composite compliance manager
 *
 * Combines all compliance utilities for centralized management
 */
export class ComplianceManager {
  readonly policies = require('./CompliancePolicy').policyManager;
  readonly audits = require('./AuditTrail').globalAuditTrail;
  readonly assessments = require('./ComplianceAssessment').assessmentManager;

  /**
   * Generate complete compliance report
   *
   * GIVEN: Framework and period
   * WHEN: generateReport called
   * THEN: Comprehensive compliance status report generated
   */
  generateComplianceReport(framework: string, startDate: Date, endDate: Date) {
    const policies = this.policies.getPoliciesByFramework(framework);
    const auditReport = this.audits.generateReport(startDate, endDate, framework);
    const complianceScore = this.assessments.calculateComplianceScore(framework);
    const openPlans = this.assessments.getOpenRemediationPlans();
    const gapCount = openPlans.length;

    return {
      framework,
      period: { startDate, endDate },
      policies: {
        total: policies.length,
        active: policies.filter((p: any) => p.status === 'ACTIVE').length,
      },
      audit: auditReport,
      compliance: {
        overallScore: complianceScore,
        gapsIdentified: gapCount,
        remediationPlans: openPlans.length,
      },
      generatedAt: new Date(),
    };
  }

  /**
   * Get compliance dashboard data
   *
   * GIVEN: Framework names
   * WHEN: getDashboard called
   * THEN: Dashboard metrics returned
   */
  getDashboard(frameworks: string[]) {
    const dashboards = frameworks.map((framework) => {
      const score = this.assessments.calculateComplianceScore(framework);
      const policies = this.policies.getPoliciesByFramework(framework);
      const pendingPolicies = policies.filter((p: any) => p.status === 'DRAFT').length;

      return {
        framework,
        score,
        policies: {
          total: policies.length,
          draft: pendingPolicies,
        },
        assessmentsDue: this.assessments.getAssessmentsDue(30).filter(
          (a: any) => a.framework === framework
        ).length,
      };
    });

    return {
      dashboards,
      overallScore: Math.round(
        dashboards.reduce((sum, d) => sum + d.score, 0) / dashboards.length
      ),
      updatedAt: new Date(),
    };
  }

  /**
   * Get compliance action items
   *
   * GIVEN: All compliance data
   * WHEN: getActionItems called
   * THEN: Prioritized action items returned
   */
  getActionItems() {
    const items = [];

    // Policies needing acknowledgment
    const policiesNeedingAck = this.policies
      .getAllPolicies()
      .filter((p: any) => p.status === 'ACTIVE' && p.acknowledgments.length < 10); // < 10 acks as example

    items.push({
      type: 'POLICY_ACKNOWLEDGMENT',
      count: policiesNeedingAck.length,
      priority: 'HIGH',
      description: `${policiesNeedingAck.length} policies need user acknowledgment`,
    });

    // Assessments due
    const dueSoon = this.assessments.getAssessmentsDue(30);
    if (dueSoon.length > 0) {
      items.push({
        type: 'ASSESSMENT_DUE',
        count: dueSoon.length,
        priority: 'MEDIUM',
        description: `${dueSoon.length} assessments due within 30 days`,
      });
    }

    // Open remediation plans
    const openPlans = this.assessments.getOpenRemediationPlans();
    if (openPlans.length > 0) {
      items.push({
        type: 'REMEDIATION_PLAN',
        count: openPlans.length,
        priority: 'HIGH',
        description: `${openPlans.length} open remediation plans`,
      });
    }

    // Policies due for review
    const policiesDueForReview = this.policies.getPoliciesDueForReview(365);
    if (policiesDueForReview.length > 0) {
      items.push({
        type: 'POLICY_REVIEW',
        count: policiesDueForReview.length,
        priority: 'MEDIUM',
        description: `${policiesDueForReview.length} policies due for review`,
      });
    }

    return items.sort((a, b) => {
      const priorityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
      return priorityOrder[a.priority as keyof typeof priorityOrder] -
        priorityOrder[b.priority as keyof typeof priorityOrder];
    });
  }
}

/**
 * Global compliance manager instance
 */
export const complianceManager = new ComplianceManager();
