/**
 * Compliance Report Service
 *
 * <p><b>Purpose</b><br>
 * Manages compliance report generation, formatting, and distribution. Produces
 * comprehensive reports for stakeholders with customizable frameworks, time periods,
 * and detail levels.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const service = new ComplianceReportService(reportRepo, controlRepo);
 * const report = await service.generateReport('assessment-123', {
 *   format: 'pdf',
 *   framework: 'SOC2'
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Compliance report generation and distribution
 * @doc.layer product
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';

/**
 * Interface for report configuration
 */
export interface ReportConfig {
  format: 'pdf' | 'html' | 'json' | 'csv';
  framework?: string;
  includeExecutiveSummary?: boolean;
  includeDetailedFindings?: boolean;
  includeRemediationPlan?: boolean;
  includeAuditTrail?: boolean;
  includeMetrics?: boolean;
  period?: {
    startDate: Date;
    endDate: Date;
  };
}

/**
 * Interface for report metrics
 */
export interface ReportMetrics {
  totalControls: number;
  controlsAssessed: number;
  controlsImplemented: number;
  controlsPartiallyImplemented: number;
  controlsNotImplemented: number;
  overallComplianceScore: number;
  findingsCount: number;
  criticalFindingsCount: number;
  openRemediationItems: number;
  remediationCompletionRate: number;
}

/**
 * Interface for report section
 */
export interface ReportSection {
  title: string;
  content: string;
  subsections?: ReportSection[];
}

/**
 * Interface for compliance report
 */
export interface ComplianceReport {
  id: string;
  assessmentId: string;
  generatedAt: Date;
  framework: string;
  metrics: ReportMetrics;
  executiveSummary?: ReportSection;
  findings?: ReportSection;
  remediationPlan?: ReportSection;
  auditTrail?: ReportSection;
  recommendations?: ReportSection;
  format: string;
  filename: string;
}

/**
 * ComplianceReportService handles report generation and distribution
 */
export class ComplianceReportService {
  /**
   * Creates a new ComplianceReportService instance.
   *
   * @param prisma - Prisma client for database access
   */
  constructor(private prisma: PrismaClient) { }

  /**
   * Generates a compliance report for an assessment.
   *
   * <p><b>Purpose</b><br>
   * Creates a comprehensive compliance report in the specified format with
   * customizable sections and detail levels.
   *
   * @param assessmentId - The assessment ID
   * @param config - Report configuration
   * @returns Promise<ComplianceReport> - Generated report
   * @throws Error if assessment not found
   */
  async generateReport(
    assessmentId: string,
    config: ReportConfig
  ): Promise<ComplianceReport> {
    try {
      // Fetch assessment data
      const assessment = await this.prisma.complianceAssessment.findUnique({
        where: { id: assessmentId },
        include: {
          remediationPlans: {
            include: { steps: true },
            orderBy: { createdAt: 'desc' },
            take: 1,
          },
        },
      });

      if (!assessment) {
        throw new Error(`Assessment not found: ${assessmentId}`);
      }

      // Calculate metrics
      const metrics = this.calculateMetrics(assessment);

      // Build report sections
      const sections: { [key: string]: ReportSection | undefined } = {};

      if (config.includeExecutiveSummary) {
        sections.executiveSummary = this.generateExecutiveSummary(
          assessment,
          metrics
        );
      }

      if (config.includeDetailedFindings) {
        sections.findings = this.generateFindingsSection(assessment);
      }

      if (config.includeRemediationPlan) {
        sections.remediationPlan = this.generateRemediationSection(assessment);
      }

      if (config.includeAuditTrail) {
        sections.auditTrail = this.generateAuditTrailSection(assessment);
      }

      if (config.includeMetrics) {
        sections.recommendations = this.generateRecommendationsSection(
          assessment
        );
      }

      // Create report object
      const report: ComplianceReport = {
        id: `report-${Date.now()}`,
        assessmentId,
        generatedAt: new Date(),
        framework: config.framework || assessment.framework || 'custom',
        metrics,
        format: config.format,
        filename: this.generateFilename(assessmentId, config.format),
        ...sections,
      };

      // Save report metadata
      await this.prisma.complianceReport.create({
        data: {
          id: report.id,
          assessmentId,
          title: `Compliance Report - ${new Date().toISOString()}`,
          generatedBy: 'system',
          framework: report.framework,
          metrics: JSON.stringify(metrics),
          format: config.format,
          filename: report.filename,
        },
      });

      return report;
    } catch (error) {
      throw new Error(
        `Failed to generate report: ${error instanceof Error ? error.message : 'unknown error'
        }`
      );
    }
  }

  /**
   * Exports a report in the specified format.
   *
   * <p><b>Purpose</b><br>
   * Converts report data to requested format (PDF, HTML, JSON, CSV) for
   * distribution to stakeholders.
   *
   * @param report - The compliance report
   * @returns Promise<Buffer> - Formatted report data
   */
  async exportReport(report: ComplianceReport): Promise<Buffer> {
    switch (report.format) {
      case 'json':
        return Buffer.from(JSON.stringify(report, null, 2));
      case 'csv':
        return Buffer.from(this.formatAsCSV(report));
      case 'html':
        return Buffer.from(this.formatAsHTML(report));
      case 'pdf':
        // In production, would use library like pdfkit or puppeteer
        return Buffer.from(this.formatAsPDF(report));
      default:
        throw new Error(`Unsupported format: ${report.format}`);
    }
  }

  /**
   * Retrieves report history for an assessment.
   *
   * <p><b>Purpose</b><br>
   * Gets all historical reports generated for an assessment to track
   * compliance progress over time.
   *
   * @param assessmentId - The assessment ID
   * @param limit - Maximum number of reports to return
   * @returns Promise<ComplianceReport[]> - Historical reports
   */
  async getReportHistory(
    assessmentId: string,
    limit = 10
  ): Promise<ComplianceReport[]> {
    const reports = await this.prisma.complianceReport.findMany({
      where: { assessmentId },
      orderBy: { generatedAt: 'desc' },
      take: limit,
    });

    return reports.map((r) => ({
      id: r.id,
      assessmentId: r.assessmentId,
      generatedAt: r.generatedAt,
      framework: r.framework || 'custom',
      metrics: JSON.parse(r.metrics as string),
      format: r.format,
      filename: r.filename || '',
    }));
  }

  /**
   * Compares two reports to show compliance trends.
   *
   * <p><b>Purpose</b><br>
   * Analyzes differences between two reports to demonstrate compliance
   * improvement or degradation over time.
   *
   * @param reportId1 - First report ID
   * @param reportId2 - Second report ID
   * @returns Promise<object> - Comparison analysis
   */
  async compareReports(reportId1: string, reportId2: string): Promise<object> {
    const report1 = await this.prisma.complianceReport.findUnique({
      where: { id: reportId1 },
    });
    const report2 = await this.prisma.complianceReport.findUnique({
      where: { id: reportId2 },
    });

    if (!report1 || !report2) {
      throw new Error('One or both reports not found');
    }

    const metrics1 = JSON.parse(report1.metrics as string);
    const metrics2 = JSON.parse(report2.metrics as string);

    return {
      reportId1,
      reportId2,
      timestamp1: report1.generatedAt,
      timestamp2: report2.generatedAt,
      complianceScoreTrend: metrics2.overallComplianceScore - metrics1.overallComplianceScore,
      findingsTrend: metrics2.findingsCount - metrics1.findingsCount,
      remediationTrend: metrics2.remediationCompletionRate - metrics1.remediationCompletionRate,
      criticalFindingsTrend: metrics2.criticalFindingsCount - metrics1.criticalFindingsCount,
    };
  }

  /**
   * Schedules recurring compliance reports.
   *
   * <p><b>Purpose</b><br>
   * Sets up automated report generation on a schedule (daily, weekly, monthly)
   * with automatic delivery to stakeholders.
   *
   * @param assessmentId - The assessment ID
   * @param schedule - Schedule expression (cron format or predefined: 'daily', 'weekly', 'monthly')
   * @param recipients - Email addresses for report delivery
   * @returns Promise<string> - Schedule ID
   */
  async scheduleRecurringReport(
    assessmentId: string,
    schedule: string,
    recipients: string[]
  ): Promise<string> {
    const scheduleId = `schedule-${Date.now()}`;

    await this.prisma.reportSchedule.create({
      data: {
        id: scheduleId,
        assessmentId,
        name: `Report Schedule - ${new Date().toISOString()}`,
        frequency: schedule,
        recipients,
      },
    });

    return scheduleId;
  }

  /**
   * Calculates compliance metrics from assessment data
   *
   * @private
   */
  private calculateMetrics(assessment: unknown): ReportMetrics {
    const controls = assessment.controls || [];
    const findings = assessment.findings || [];
    const criticalFindings = findings.filter(
      (f: unknown) => f.severity === 'critical'
    );

    const remediationPlan = assessment.remediationPlan;
    const totalSteps = remediationPlan?.steps?.length || 0;
    const completedSteps = remediationPlan?.steps?.filter(
      (s: unknown) => s.status === 'completed'
    ).length || 0;

    return {
      totalControls: controls.length,
      controlsAssessed: controls.filter((c: unknown) => c.assessed).length,
      controlsImplemented: controls.filter(
        (c: unknown) => c.status === 'implemented'
      ).length,
      controlsPartiallyImplemented: controls.filter(
        (c: unknown) => c.status === 'partial'
      ).length,
      controlsNotImplemented: controls.filter(
        (c: unknown) => c.status === 'not_implemented'
      ).length,
      overallComplianceScore: assessment.riskScore || 0,
      findingsCount: findings.length,
      criticalFindingsCount: criticalFindings.length,
      openRemediationItems: totalSteps - completedSteps,
      remediationCompletionRate:
        totalSteps > 0 ? (completedSteps / totalSteps) * 100 : 0,
    };
  }

  /**
   * Generates executive summary section
   *
   * @private
   */
  private generateExecutiveSummary(
    assessment: unknown,
    metrics: ReportMetrics
  ): ReportSection {
    const complianceRate = metrics.overallComplianceScore;
    const status =
      complianceRate >= 80
        ? 'compliant'
        : complianceRate >= 60
          ? 'substantially compliant'
          : 'non-compliant';

    return {
      title: 'Executive Summary',
      content: `This compliance assessment was conducted on ${assessment.assessmentDate}. The organization is currently ${status} with an overall compliance score of ${complianceRate}%. Key findings include ${metrics.findingsCount} total findings, of which ${metrics.criticalFindingsCount} are critical. There are ${metrics.openRemediationItems} open remediation items with a completion rate of ${metrics.remediationCompletionRate.toFixed(1)}%.`,
      subsections: [
        {
          title: 'Compliance Score',
          content: `${complianceRate}%`,
        },
        {
          title: 'Status',
          content: status,
        },
        {
          title: 'Critical Findings',
          content: `${metrics.criticalFindingsCount}`,
        },
      ],
    };
  }

  /**
   * Generates findings section
   *
   * @private
   */
  private generateFindingsSection(assessment: unknown): ReportSection {
    const findings = assessment.findings || [];
    const bySeverity = new Map<string, number>();

    for (const finding of findings) {
      const severity = finding.severity || 'medium';
      bySeverity.set(severity, (bySeverity.get(severity) || 0) + 1);
    }

    return {
      title: 'Detailed Findings',
      content: `A total of ${findings.length} findings were identified during the assessment.`,
      subsections: Array.from(bySeverity.entries()).map(([severity, count]) => ({
        title: `${severity.charAt(0).toUpperCase() + severity.slice(1)} Findings`,
        content: `${count} finding(s)`,
      })),
    };
  }

  /**
   * Generates remediation section
   *
   * @private
   */
  private generateRemediationSection(assessment: unknown): ReportSection {
    const plan = assessment.remediationPlan;
    const steps = plan?.steps || [];

    return {
      title: 'Remediation Plan',
      content: `The remediation plan includes ${steps.length} action items with a total estimated effort of ${steps.reduce((sum: number, s: unknown) => sum + (s.estimatedEffort || 0), 0)} hours.`,
      subsections: [
        {
          title: 'Completion Target',
          content: plan?.completionTarget
            ? new Date(plan.completionTarget).toLocaleDateString()
            : 'Not set',
        },
        {
          title: 'Current Progress',
          content: `${steps.filter((s: unknown) => s.status === 'completed').length}/${steps.length} steps completed`,
        },
      ],
    };
  }

  /**
   * Generates audit trail section
   *
   * @private
   */
  private generateAuditTrailSection(assessment: unknown): ReportSection {
    const auditEvents = assessment.auditTrail || [];

    return {
      title: 'Audit Trail',
      content: `${auditEvents.length} audit events recorded during this assessment period.`,
      subsections: auditEvents.slice(0, 5).map((event: unknown) => ({
        title: event.action,
        content: `${new Date(event.timestamp).toLocaleDateString()} - ${event.actor}`,
      })),
    };
  }

  /**
   * Generates recommendations section
   *
   * @private
   */
  private generateRecommendationsSection(assessment: unknown): ReportSection {
    return {
      title: 'Recommendations',
      content: 'Based on the assessment findings, the following recommendations are proposed to improve compliance posture.',
      subsections: [
        {
          title: 'Priority Actions',
          content: 'Address all critical findings within 30 days',
        },
        {
          title: 'Process Improvements',
          content: 'Implement automated controls for recurring compliance checks',
        },
      ],
    };
  }

  /**
   * Formats report as CSV
   *
   * @private
   */
  private formatAsCSV(report: ComplianceReport): string {
    const lines: string[] = [
      'Compliance Report Export',
      `Assessment ID: ${report.assessmentId}`,
      `Generated: ${report.generatedAt.toISOString()}`,
      `Framework: ${report.framework}`,
      '',
      'Metrics',
      `Total Controls,${report.metrics.totalControls}`,
      `Controls Assessed,${report.metrics.controlsAssessed}`,
      `Overall Score,${report.metrics.overallComplianceScore}%`,
      `Total Findings,${report.metrics.findingsCount}`,
      `Critical Findings,${report.metrics.criticalFindingsCount}`,
      `Remediation Completion,${report.metrics.remediationCompletionRate.toFixed(1)}%`,
    ];
    return lines.join('\n');
  }

  /**
   * Formats report as HTML
   *
   * @private
   */
  private formatAsHTML(report: ComplianceReport): string {
    return `
      <!DOCTYPE html>
      <html>
        <head>
          <title>Compliance Report</title>
          <style>
            body { font-family: Arial, sans-serif; margin: 40px; }
            h1 { color: #333; }
            .metric { margin: 10px 0; padding: 10px; background: #f5f5f5; }
            .critical { color: red; }
          </style>
        </head>
        <body>
          <h1>Compliance Report</h1>
          <p>Assessment ID: ${report.assessmentId}</p>
          <p>Generated: ${report.generatedAt.toISOString()}</p>
          <h2>Metrics</h2>
          <div class="metric">
            <strong>Overall Compliance Score:</strong> ${report.metrics.overallComplianceScore}%
          </div>
          <div class="metric">
            <strong>Total Findings:</strong> <span class="critical">${report.metrics.findingsCount}</span>
          </div>
          <div class="metric">
            <strong>Remediation Completion:</strong> ${report.metrics.remediationCompletionRate.toFixed(1)}%
          </div>
        </body>
      </html>
    `;
  }

  /**
   * Formats report as PDF (placeholder)
   *
   * @private
   */
  private formatAsPDF(report: ComplianceReport): string {
    // In production, use pdfkit or puppeteer to generate real PDF
    return `PDF Report: ${report.id}`;
  }

  /**
   * Generates report filename
   *
   * @private
   */
  private generateFilename(assessmentId: string, format: string): string {
    const timestamp = new Date().toISOString().split('T')[0];
    return `compliance-report-${assessmentId}-${timestamp}.${format}`;
  }
}

