/**
 * @fileoverview Output Formatters for Machine-Readable Reports
 * @module @ghatana/accessibility-audit/formatters
 * 
 * Supports multiple output formats for CI/CD integration and automation:
 * - JSON (structured data)
 * - SARIF (Static Analysis Results Interchange Format)
 * - XML (legacy system integration)
 * - CSV (spreadsheet analysis)
 * - HTML (visual reports)
 * - Markdown (documentation)
 */

import type {
  AccessibilityReport,
  Finding,
  SARIFOutput,
  AccessibilityScore,
} from '../types';

/**
 * Base output formatter interface.
 *
 * Implementations must produce a serialized representation of an
 * AccessibilityReport and expose the intended file extension and mime type.
 *
 * Contract:
 * - format(report): returns a string representation suitable for writing to disk or sending over the network
 * - getFileExtension(): the suggested file extension (without dot)
 * - getMimeType(): the IANA mime type for the output
 */
export interface OutputFormatterContract {
  /**
   * Serialize the report into the formatter's output format.
   * @param report Accessibility report to serialize
   * @returns serialized string
   */
  format(report: AccessibilityReport): string;

  /**
   * Get the recommended file extension (for example: 'json', 'html').
   */
  getFileExtension(): string;

  /**
   * Get the MIME type suitable for HTTP responses or Content-Type headers.
   */
  getMimeType(): string;
}

/**
 * JSON formatter - Clean, structured output.
 *
 * Produces a pretty-printed JSON representation of the full
 * AccessibilityReport. This is the recommended format for CI
 * systems and programmatic consumption.
 *
 * Example:
 * const formatter = new JSONFormatter();
 * const payload = formatter.format(report);
 * // write payload to report.json
 */
export class JSONFormatter implements OutputFormatterContract {
  format(report: AccessibilityReport): string {
    return JSON.stringify(report, null, 2);
  }

  getFileExtension(): string {
    return 'json';
  }

  getMimeType(): string {
    return 'application/json';
  }
}

/**
 * SARIF formatter - Static Analysis Results Interchange Format.
 *
 * Converts findings into SARIF 2.1.0 runs and rules so tools like
 * GitHub Code Scanning can ingest the results.
 *
 * Note: SARIF entries intentionally keep fields minimal (message, level, location)
 * to maximize compatibility with consumers.
 */
export class SARIFFormatter implements OutputFormatterContract {
  format(report: AccessibilityReport): string {
    const sarif: SARIFOutput = {
      version: '2.1.0',
      $schema:
        'https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json',
      runs: [
        {
          tool: {
            driver: {
              name: '@ghatana/accessibility-audit',
              version: report.metadata.toolVersion,
              informationUri: 'https://github.com/yappc/app-creator',
              rules: this.extractRules(report.findings),
            },
          },
          results: this.convertFindings(report.findings),
        },
      ],
    };

    return JSON.stringify(sarif, null, 2);
  }

  /**
   * Extract a de-duped list of SARIF rule definitions from findings.
   * @internal
   */
  private extractRules(findings: Finding[]): SARIFOutput['runs'][0]['tool']['driver']['rules'] {
    const uniqueRules = new Map<string, Finding>();

    findings.forEach((finding) => {
      if (!uniqueRules.has(finding.id)) {
        uniqueRules.set(finding.id, finding);
      }
    });

    return Array.from(uniqueRules.values()).map((finding) => ({
      id: finding.id,
      shortDescription: { text: finding.help },
      fullDescription: { text: finding.description },
      helpUri: finding.helpUrl,
    }));
  }

  /**
   * Convert findings into SARIF results array.
   * @internal
   */
  private convertFindings(findings: Finding[]): SARIFOutput['runs'][0]['results'] {
    return findings.map((finding) => {
      // Map severity to SARIF level
      let level: 'error' | 'warning' | 'note';
      if (finding.severity === 'critical' || finding.severity === 'serious') {
        level = 'error';
      } else if (finding.severity === 'moderate') {
        level = 'warning';
      } else {
        level = 'note';
      }

      return {
        ruleId: finding.id,
        level,
        message: { text: finding.help },
        locations: [
          {
            physicalLocation: {
              artifactLocation: {
                uri: finding.location.file || finding.location.url,
              },
              region: finding.location.line
                ? {
                    startLine: finding.location.line,
                    startColumn: finding.location.column || 1,
                  }
                : undefined,
            },
          },
        ],
      };
    });
  }

  /**
   * File extension used for SARIF exports (without dot).
   */
  getFileExtension(): string {
    return 'sarif';
  }

  /**
   * MIME type for SARIF JSON.
   */
  getMimeType(): string {
    return 'application/sarif+json';
  }
}

/**
 * CSV formatter - Spreadsheet-friendly format.
 *
 * Produces a comma-separated representation of each finding. Useful when
 * teams want to import results into Excel or Google Sheets for triage.
 */
export class CSVFormatter implements OutputFormatterContract {
  format(report: AccessibilityReport): string {
    const lines: string[] = [];

    // Header
    lines.push(
      'Severity,Type,Rule ID,WCAG,Element,Description,File,Line,URL,Help URL'
    );

    // Data rows
    report.findings.forEach((finding) => {
      const row = [
        finding.severity,
        finding.type,
        finding.id,
        `${finding.wcag.level} ${finding.wcag.criterion}`,
        this.escapeCsv(finding.location.selector),
        this.escapeCsv(finding.description),
        finding.location.file || '',
        finding.location.line?.toString() || '',
        finding.location.url,
        finding.helpUrl,
      ];
      lines.push(row.join(','));
    });

    return lines.join('\n');
  }

  /** Escape CSV cell values according to RFC4180-like rules. */
  private escapeCsv(value: string): string {
    if (value.includes(',') || value.includes('"') || value.includes('\n')) {
      return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
  }

  getFileExtension(): string {
    return 'csv';
  }

  getMimeType(): string {
    return 'text/csv';
  }
}

/**
 * XML formatter - Legacy system compatibility.
 *
 * Provides a compact XML representation intended for older ingestion
 * pipelines that expect XML payloads.
 */
export class XMLFormatter implements OutputFormatterContract {
  format(report: AccessibilityReport): string {
    let xml = '<?xml version="1.0" encoding="UTF-8"?>\n';
    xml += '<accessibility-report>\n';

    // Metadata
    xml += '  <metadata>\n';
    xml += `    <id>${this.escape(report.metadata.id)}</id>\n`;
    xml += `    <timestamp>${this.escape(report.metadata.timestamp)}</timestamp>\n`;
    xml += `    <tool-version>${this.escape(report.metadata.toolVersion)}</tool-version>\n`;
    xml += '  </metadata>\n';

    // Score
    xml += this.formatScore(report.score);

    // Summary
    xml += '  <summary>\n';
    xml += `    <total-findings>${report.summary.totalFindings}</total-findings>\n`;
    xml += `    <duration>${report.summary.duration}</duration>\n`;
    xml += `    <elements-analyzed>${report.summary.elementsAnalyzed}</elements-analyzed>\n`;
    xml += '  </summary>\n';

    // Findings
    xml += '  <findings>\n';
    report.findings.forEach((finding) => {
      xml += this.formatFinding(finding);
    });
    xml += '  </findings>\n';

    xml += '</accessibility-report>';
    return xml;
  }

  /** Serialize the numeric/grade score block to XML. */
  private formatScore(score: AccessibilityScore): string {
    let xml = '  <score>\n';
    xml += `    <overall>${score.overall}</overall>\n`;
    xml += `    <grade>${score.grade}</grade>\n`;
    xml += `    <compliance-level>${this.escape(score.complianceLevel)}</compliance-level>\n`;
    xml += '  </score>\n';
    return xml;
  }

  /** Serialize a single finding to XML. */
  private formatFinding(finding: Finding): string {
    let xml = '    <finding>\n';
    xml += `      <id>${this.escape(finding.id)}</id>\n`;
    xml += `      <severity>${finding.severity}</severity>\n`;
    xml += `      <type>${finding.type}</type>\n`;
    xml += `      <description>${this.escape(finding.description)}</description>\n`;
    xml += `      <wcag-level>${finding.wcag.level}</wcag-level>\n`;
    xml += `      <wcag-criterion>${this.escape(finding.wcag.criterion)}</wcag-criterion>\n`;
    xml += `      <selector>${this.escape(finding.location.selector)}</selector>\n`;
    xml += `      <help-url>${this.escape(finding.helpUrl)}</help-url>\n`;
    xml += '    </finding>\n';
    return xml;
  }

  /** Escape XML-special characters. */
  private escape(value: string | undefined | null): string {
    if (value === undefined || value === null) {
      return '';
    }
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  }

  /** XML file extension (without dot). */
  getFileExtension(): string {
    return 'xml';
  }

  /** XML MIME type. */
  getMimeType(): string {
    return 'application/xml';
  }
}

/**
 * HTML formatter - Visual, interactive report.
 *
 * Renders a self-contained HTML page including inline CSS/JS so the output
 * can be opened directly in a browser or saved as an artifact in CI.
 */
export class HTMLFormatter implements OutputFormatterContract {
  format(report: AccessibilityReport): string {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Accessibility Audit Report - ${this.escape(report.target.title)}</title>
  <style>
    ${this.getStyles()}
  </style>
</head>
<body>
  <div class="container">
    <header>
      <h1>Accessibility Audit Report</h1>
      <div class="meta">
        <p><strong>Target:</strong> ${this.escape(report.target.url)}</p>
        <p><strong>Date:</strong> ${new Date(report.metadata.timestamp).toLocaleString()}</p>
        <p><strong>Tool Version:</strong> ${this.escape(report.metadata.toolVersion)}</p>
      </div>
    </header>

    ${this.formatScoreSection(report.score)}
    ${this.formatSummarySection(report.summary)}
    ${this.formatFindingsSection(report.findings)}
    ${this.formatRecommendationsSection(report.recommendations)}
  </div>

  <script>
    ${this.getScripts()}
  </script>
</body>
</html>`;
  }

  /** Render the top-level score section for the HTML report. */
  private formatScoreSection(score: AccessibilityScore): string {
    const gradeClass = score.grade.replace('+', 'plus').replace('-', 'minus').toLowerCase();
    
    return `
    <section class="score-section">
      <h2>Overall Score</h2>
      <div class="score-card">
        <div class="score-circle grade-${gradeClass}">
          <div class="score-value">${score.overall}</div>
          <div class="score-grade">${score.grade}</div>
        </div>
        <div class="score-details">
          <p class="compliance-level">${this.escape(score.complianceLevel)}</p>
          ${score.trend ? `<p class="trend ${score.trend.direction}">${score.trend.direction.toUpperCase()}: ${score.trend.changePercentage > 0 ? '+' : ''}${score.trend.changePercentage}%</p>` : ''}
        </div>
      </div>

      <h3>Dimension Breakdown</h3>
      <div class="dimensions">
        ${this.formatDimensions(score.dimensions)}
      </div>
    </section>`;
  }

  /** Render the eight accessibility dimension breakdown blocks. */
  private formatDimensions(dimensions: AccessibilityScore['dimensions']): string {
    const entries: Array<[string, any]> = [
      ['WCAG Compliance', dimensions.wcagCompliance],
      ['Semantic Structure', dimensions.semanticStructure],
      ['Keyboard Accessibility', dimensions.keyboardAccessibility],
      ['Visual Accessibility', dimensions.visualAccessibility],
      ['Form Accessibility', dimensions.formAccessibility],
      ['Media Accessibility', dimensions.mediaAccessibility],
      ['ARIA Implementation', dimensions.ariaImplementation],
      ['Focus Management', dimensions.focusManagement],
    ];

    return entries
      .map(
        ([name, dim]) => `
        <div class="dimension">
          <div class="dimension-header">
            <span class="dimension-name">${name}</span>
            <span class="dimension-score">${dim.score}/100 (${dim.grade})</span>
          </div>
          <div class="dimension-bar">
            <div class="dimension-fill" style="width: ${dim.score}%"></div>
          </div>
          <div class="dimension-issues">
            <span class="critical">${dim.issues.critical} critical</span>
            <span class="serious">${dim.issues.serious} serious</span>
            <span class="moderate">${dim.issues.moderate} moderate</span>
            <span class="minor">${dim.issues.minor} minor</span>
          </div>
        </div>
      `
      )
      .join('');
  }

  /** Render the summary metrics section for HTML. */
  private formatSummarySection(summary: AccessibilityReport['summary']): string {
    return `
    <section class="summary-section">
      <h2>Summary</h2>
      <div class="summary-grid">
        <div class="summary-item">
          <div class="summary-value">${summary.totalFindings}</div>
          <div class="summary-label">Total Findings</div>
        </div>
        <div class="summary-item critical">
          <div class="summary-value">${summary.bySeverity.critical}</div>
          <div class="summary-label">Critical</div>
        </div>
        <div class="summary-item serious">
          <div class="summary-value">${summary.bySeverity.serious}</div>
          <div class="summary-label">Serious</div>
        </div>
        <div class="summary-item moderate">
          <div class="summary-value">${summary.bySeverity.moderate}</div>
          <div class="summary-label">Moderate</div>
        </div>
      </div>
    </section>`;
  }

  /** Render the findings grouped by severity for HTML. */
  private formatFindingsSection(findings: Finding[]): string {
    const bySeverity = {
      critical: findings.filter((f) => f.severity === 'critical'),
      serious: findings.filter((f) => f.severity === 'serious'),
      moderate: findings.filter((f) => f.severity === 'moderate'),
      minor: findings.filter((f) => f.severity === 'minor'),
    };

    let html = '<section class="findings-section"><h2>Findings</h2>';

    (['critical', 'serious', 'moderate', 'minor'] as const).forEach((severity) => {
      const items = bySeverity[severity];
      if (items.length === 0) return;

      html += `<div class="findings-group ${severity}">`;
      html += `<h3>${severity.toUpperCase()} (${items.length})</h3>`;
      items.forEach((finding) => {
        html += this.formatFinding(finding);
      });
      html += '</div>';
    });

    html += '</section>';
    return html;
  }

  /** Render an individual finding (expandable <details>) for HTML. */
  private formatFinding(finding: Finding): string {
    return `
    <details class="finding">
      <summary>
        <span class="finding-id">${this.escape(finding.id)}</span>
        <span class="finding-title">${this.escape(finding.help)}</span>
        <span class="wcag-tag">${finding.wcag.level} ${finding.wcag.criterion}</span>
      </summary>
      <div class="finding-details">
        <p><strong>Description:</strong> ${this.escape(finding.description)}</p>
        <p><strong>Element:</strong> <code>${this.escape(finding.location.selector)}</code></p>
        ${finding.location.file ? `<p><strong>File:</strong> ${this.escape(finding.location.file)}:${finding.location.line || 0}</p>` : ''}
        <p><strong>Impact:</strong> Affects users who are ${finding.affectedUsers.join(', ')}</p>
        
        <div class="remediation">
          <h4>How to Fix</h4>
          <p>${this.escape(finding.remediation.description)}</p>
          <details>
            <summary>View code example</summary>
            <pre><code>${this.escape(finding.remediation.codeExample)}</code></pre>
          </details>
          <p><a href="${finding.helpUrl}" target="_blank" rel="noopener">Learn more →</a></p>
        </div>
      </div>
    </details>`;
  }

  /** Render recommendations (immediate, short-term, long-term) for HTML. */
  private formatRecommendationsSection(recommendations: AccessibilityReport['recommendations']): string {
    return `
    <section class="recommendations-section">
      <h2>Recommendations</h2>
      
      ${recommendations.immediate.length > 0 ? `
      <div class="rec-group immediate">
        <h3>🚨 Immediate Actions</h3>
        <ul>
          ${recommendations.immediate.map((r) => `<li>${this.escape(r)}</li>`).join('')}
        </ul>
      </div>` : ''}
      
      ${recommendations.shortTerm.length > 0 ? `
      <div class="rec-group short-term">
        <h3>📅 Short-term (1-2 sprints)</h3>
        <ul>
          ${recommendations.shortTerm.map((r) => `<li>${this.escape(r)}</li>`).join('')}
        </ul>
      </div>` : ''}
      
      ${recommendations.longTerm.length > 0 ? `
      <div class="rec-group long-term">
        <h3>🎯 Long-term improvements</h3>
        <ul>
          ${recommendations.longTerm.map((r) => `<li>${this.escape(r)}</li>`).join('')}
        </ul>
      </div>` : ''}
    </section>`;
  }

  /** Basic HTML-escaping for inserted text nodes. */
  private escape(value: string | undefined | null): string {
    if (value === undefined || value === null) {
      return '';
    }
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /** Inline CSS used by the HTML report. Kept small and self-contained. */
  private getStyles(): string {
    return `
      * { margin: 0; padding: 0; box-sizing: border-box; }
      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; background: #f5f5f5; }
      .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
      header { background: white; padding: 30px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
      h1 { color: #2c3e50; margin-bottom: 10px; }
      h2 { color: #34495e; margin: 30px 0 15px; }
      section { background: white; padding: 30px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
      .score-card { display: flex; align-items: center; gap: 30px; margin: 20px 0; }
      .score-circle { width: 150px; height: 150px; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; font-weight: bold; }
      .score-value { font-size: 48px; }
      .score-grade { font-size: 24px; margin-top: 5px; }
      .grade-aplus, .grade-a { background: #27ae60; color: white; }
      .grade-aminus, .grade-bplus { background: #2ecc71; color: white; }
      .grade-b, .grade-bminus { background: #f39c12; color: white; }
      .grade-cplus, .grade-c { background: #e67e22; color: white; }
      .grade-cminus, .grade-d { background: #e74c3c; color: white; }
      .grade-f { background: #c0392b; color: white; }
      .dimensions { display: grid; gap: 15px; }
      .dimension { padding: 15px; background: #f8f9fa; border-radius: 4px; }
      .dimension-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
      .dimension-bar { height: 8px; background: #e0e0e0; border-radius: 4px; overflow: hidden; }
      .dimension-fill { height: 100%; background: linear-gradient(90deg, #3498db, #2ecc71); }
      .dimension-issues { display: flex; gap: 15px; margin-top: 8px; font-size: 14px; }
      .critical { color: #c0392b; font-weight: 600; }
      .serious { color: #e67e22; font-weight: 600; }
      .moderate { color: #f39c12; }
      .minor { color: #95a5a6; }
      .summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 20px; margin: 20px 0; }
      .summary-item { text-align: center; padding: 20px; background: #f8f9fa; border-radius: 4px; }
      .summary-value { font-size: 36px; font-weight: bold; color: #2c3e50; }
      .summary-label { font-size: 14px; color: #7f8c8d; margin-top: 5px; }
      .finding { margin: 15px 0; border: 1px solid #e0e0e0; border-radius: 4px; padding: 15px; }
      .finding summary { cursor: pointer; font-weight: 600; display: flex; gap: 10px; align-items: center; }
      .finding-details { margin-top: 15px; padding-top: 15px; border-top: 1px solid #e0e0e0; }
      code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: 'Courier New', monospace; }
      pre { background: #2c3e50; color: #ecf0f1; padding: 15px; border-radius: 4px; overflow-x: auto; }
      .recommendations-section ul { list-style-position: inside; }
      .recommendations-section li { margin: 8px 0; }
      .trend.improving { color: #27ae60; }
      .trend.degrading { color: #e74c3c; }
      .trend.stable { color: #95a5a6; }
    `;
  }

  /** Tiny JS helpers for the generated HTML report (scroll on open). */
  private getScripts(): string {
    return `
      // Add filtering/sorting functionality
      document.querySelectorAll('details').forEach(detail => {
        detail.addEventListener('toggle', function() {
          if (this.open) {
            this.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
          }
        });
      });
    `;
  }

  /** HTML file extension (without dot). */
  getFileExtension(): string {
    return 'html';
  }

  /** MIME type for saved HTML reports. */
  getMimeType(): string {
    return 'text/html';
  }
}

/**
 * Markdown formatter - Documentation-friendly.
 *
 * Useful for quick human-readable reports that can be added to PRs or
 * project wikis. Produces a GitHub-flavored Markdown string.
 */
export class MarkdownFormatter implements OutputFormatterContract {
  format(report: AccessibilityReport): string {
    let md = `# Accessibility Audit Report\n\n`;
    md += `**Target:** ${report.target.url}\n`;
    md += `**Date:** ${new Date(report.metadata.timestamp).toLocaleString()}\n`;
    md += `**Tool Version:** ${report.metadata.toolVersion}\n\n`;

    md += `## Overall Score: ${report.score.overall}/100 (${report.score.grade})\n\n`;
    md += `**Compliance Level:** ${report.score.complianceLevel}\n\n`;

    md += `### Dimension Scores\n\n`;
    md += `| Dimension | Score | Grade | Critical | Serious | Moderate | Minor |\n`;
    md += `|-----------|-------|-------|----------|---------|----------|-------|\n`;

    const dims = report.score.dimensions;
    const entries: Array<[string, any]> = [
      ['WCAG Compliance', dims.wcagCompliance],
      ['Semantic Structure', dims.semanticStructure],
      ['Keyboard Accessibility', dims.keyboardAccessibility],
      ['Visual Accessibility', dims.visualAccessibility],
      ['Form Accessibility', dims.formAccessibility],
      ['Media Accessibility', dims.mediaAccessibility],
      ['ARIA Implementation', dims.ariaImplementation],
      ['Focus Management', dims.focusManagement],
    ];

    entries.forEach(([name, dim]) => {
      md += `| ${name} | ${dim.score}/100 | ${dim.grade} | ${dim.issues.critical} | ${dim.issues.serious} | ${dim.issues.moderate} | ${dim.issues.minor} |\n`;
    });

    md += `\n## Summary\n\n`;
    md += `- **Total Findings:** ${report.summary.totalFindings}\n`;
    md += `- **Critical:** ${report.summary.bySeverity.critical}\n`;
    md += `- **Serious:** ${report.summary.bySeverity.serious}\n`;
    md += `- **Moderate:** ${report.summary.bySeverity.moderate}\n`;
    md += `- **Minor:** ${report.summary.bySeverity.minor}\n\n`;

    if (report.findings.length > 0) {
      md += `## Findings\n\n`;
      report.findings.forEach((finding, i) => {
        md += `### ${i + 1}. [${finding.severity.toUpperCase()}] ${finding.help}\n\n`;
        md += `- **ID:** ${finding.id}\n`;
        md += `- **WCAG:** ${finding.wcag.level} ${finding.wcag.criterion}\n`;
        md += `- **Element:** \`${finding.location.selector}\`\n`;
        md += `- **Help:** [Learn more](${finding.helpUrl})\n\n`;
      });
    }

    return md;
  }

  /** Suggested extension for Markdown reports. */
  getFileExtension(): string {
    return 'md';
  }

  /** MIME type for Markdown text. */
  getMimeType(): string {
    return 'text/markdown';
  }
}

/**
 * Factory for creating and listing supported output formatters.
 *
 * Use OutputFormatterFactory.create(format) to retrieve a formatter by name.
 * The factory keeps singleton instances of each formatter for re-use.
 */
export class OutputFormatterFactory {
  private static instance: OutputFormatterFactory;
  private static formatters: Map<string, OutputFormatterContract> = new Map([
    ['json', new JSONFormatter()],
    ['sarif', new SARIFFormatter()],
    ['csv', new CSVFormatter()],
    ['xml', new XMLFormatter()],
    ['html', new HTMLFormatter()],
    ['markdown', new MarkdownFormatter()],
  ]);

  /**
   * Get singleton instance (for test compatibility).
   *
   * The factory itself is lightweight; this helper exists so tests
   * that rely on object identity can obtain the same instance.
   */
  static getInstance(): OutputFormatterFactory {
    if (!this.instance) {
      this.instance = new OutputFormatterFactory();
    }
    return this.instance;
  }

  /**
   * Create or retrieve a formatter for the specified format.
   *
   * @param format - case-insensitive format name (json, sarif, csv, xml, html, markdown)
   * @returns IOutputFormatter instance
  * @returns OutputFormatterContract instance
   * @throws Error when format is unknown
   * @example
   * const fmt = OutputFormatterFactory.create('json');
   * const payload = fmt.format(report);
   */
  static create(format: string): OutputFormatterContract {
    const formatter = this.formatters.get(format.toLowerCase());
    if (!formatter) {
      throw new Error(`Unknown output format: ${format}. Supported formats: ${this.getSupportedFormats().join(', ')}`);
    }
    return formatter;
  }

  /** Alias for create(format) kept for backwards compatibility. */
  static getFormatter(format: string): OutputFormatterContract {
    return this.create(format);
  }

  /** Instance-level wrapper for create(format). */
  getFormatter(format: string): OutputFormatterContract {
    return OutputFormatterFactory.create(format);
  }

  /**
   * Get list of supported formats (lower-case names).
   */
  static getSupportedFormats(): string[] {
    return Array.from(this.formatters.keys());
  }
}

/**
 * Export singleton factory (deprecated - use static methods)
 * @deprecated Use OutputFormatterFactory.create() instead
 */
export const formatterFactory = new class {
  /**
   *
   */
  getFormatter(format: string): OutputFormatterContract {
    return OutputFormatterFactory.create(format);
  }

  /**
   *
   */
  getSupportedFormats(): string[] {
    return OutputFormatterFactory.getSupportedFormats();
  }
}();
