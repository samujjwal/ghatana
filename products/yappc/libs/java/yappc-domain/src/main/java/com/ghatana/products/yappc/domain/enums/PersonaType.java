package com.ghatana.products.yappc.domain.enums;

/**
 * Persona type enumeration for dashboard personalization.
 *
 * <p><b>Purpose</b><br>
 * Defines user personas to customize dashboard views, KPIs, and metrics based on role/responsibility.
 * Different personas see different data emphasizing their concerns.
 *
 * <p><b>Persona Mapping</b><br>
 * - EXECUTIVE: High-level business/risk metrics, cost trends, compliance status
 * - LEADERSHIP: Team performance, project health, strategic initiatives
 * - MANAGER: Project metrics, team productivity, resource allocation
 * - ENGINEER: Technical metrics, code quality, pipeline status, incident details
 * - SECURITY_CHAMPION: Security posture, vulnerability trends, threat intel
 * - COMPLIANCE_OFFICER: Compliance assessments, policy violations, audit trails
 * - AUDITOR: Historical data, audit logs, evidence collection
 *
 * <p><b>Usage</b><br>
 * Used in WorkspaceMember and Dashboard entities to control UI personalization.
 *
 * @see com.ghatana.products.yappc.domain.model.WorkspaceMember
 * @see com.ghatana.products.yappc.domain.model.Dashboard
 * @doc.type enum
 * @doc.purpose Dashboard persona-based UI customization
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum PersonaType {
    /**
     * Executive persona - strategic business metrics.
     * Focus: Risk exposure, compliance status, cost optimization, business impact.
     */
    EXECUTIVE,

    /**
     * Leadership persona - organizational performance.
     * Focus: Team velocity, project health, resource utilization, strategic KPIs.
     */
    LEADERSHIP,

    /**
     * Manager persona - operational metrics.
     * Focus: Project status, team productivity, blockers, tactical decisions.
     */
    MANAGER,

    /**
     * Engineer persona - technical metrics.
     * Focus: Code quality, pipeline status, deployment frequency, technical debt.
     */
    ENGINEER,

    /**
     * Security champion persona - security posture.
     * Focus: Vulnerability trends, threat intelligence, security incidents, remediation.
     */
    SECURITY_CHAMPION,

    /**
     * Compliance officer persona - regulatory compliance.
     * Focus: Compliance assessments, policy adherence, evidence collection, audit readiness.
     */
    COMPLIANCE_OFFICER,

    /**
     * Auditor persona - historical audit data.
     * Focus: Audit trails, evidence verification, control testing, historical reports.
     */
    AUDITOR
}
