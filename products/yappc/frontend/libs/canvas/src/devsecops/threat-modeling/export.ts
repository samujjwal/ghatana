/**
 * Threat Model Export
 * 
 * Export threat models to various formats (YAML, CSV, JSON, Markdown).
 */

import { calculateThreatScore } from './state';

import type { ThreatModelState, ThreatExportFormat } from './types';

/**
 * Exports threat model to specified format
 */
export function exportThreatModel(
  state: ThreatModelState,
  format: ThreatExportFormat
): string {
  switch (format) {
    case 'yaml':
      return exportToYAML(state);
    case 'csv':
      return exportToCSV(state);
    case 'json':
      return exportToJSON(state);
    case 'markdown':
      return exportToMarkdown(state);
    default:
      throw new Error(`Unsupported export format: ${format}`);
  }
}

/**
 *
 */
function exportToYAML(state: ThreatModelState): string {
  const yaml = [
    'threat_model:',
    '  config:',
    `    stride_enabled: ${state.config.enableSTRIDE}`,
    `    linddun_enabled: ${state.config.enableLINDDUN}`,
    `    min_severity: ${state.config.minSeverity}`,
    '',
    '  elements:',
    ...state.elements.map(e => `    - id: ${e.id}\n      type: ${e.type}\n      name: ${e.name}`),
    '',
    '  threats:',
    ...state.threats.map(t => [
      `    - id: ${t.id}`,
      `      title: ${t.title}`,
      `      category: ${t.category}`,
      `      severity: ${t.severity}`,
      `      status: ${t.status}`,
      `      score: ${calculateThreatScore(t)}`,
    ].join('\n')),
  ];
  
  return yaml.join('\n');
}

/**
 *
 */
function exportToCSV(state: ThreatModelState): string {
  const headers = ['ID', 'Title', 'Category', 'Severity', 'Status', 'Score', 'Affected Elements'];
  const rows = state.threats.map(t => [
    t.id,
    `"${t.title}"`,
    t.category,
    t.severity,
    t.status,
    calculateThreatScore(t).toString(),
    `"${t.affectedElements.join(', ')}"`,
  ]);
  
  return [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
}

/**
 *
 */
function exportToJSON(state: ThreatModelState): string {
  return JSON.stringify(state, null, 2);
}

/**
 *
 */
function exportToMarkdown(state: ThreatModelState): string {
  const lines = [
    '# Threat Model Report',
    '',
    '## Configuration',
    `- STRIDE: ${state.config.enableSTRIDE ? 'Enabled' : 'Disabled'}`,
    `- LINDDUN: ${state.config.enableLINDDUN ? 'Enabled' : 'Disabled'}`,
    `- Minimum Severity: ${state.config.minSeverity}`,
    '',
    '## Summary',
    `- Total Elements: ${state.elements.length}`,
    `- Total Flows: ${state.flows.length}`,
    `- Total Boundaries: ${state.boundaries.length}`,
    `- Total Threats: ${state.threats.length}`,
    '',
    '## Threats',
    '',
  ];
  
  // Group by severity
  const severities: Array<'critical' | 'high' | 'medium' | 'low' | 'info'> = 
    ['critical', 'high', 'medium', 'low', 'info'];
  
  for (const severity of severities) {
    const threats = state.threats.filter(t => t.severity === severity);
    if (threats.length > 0) {
      lines.push(`### ${severity.toUpperCase()} (${threats.length})`, '');
      
      for (const threat of threats) {
        lines.push(
          `#### ${threat.title}`,
          '',
          `- **ID:** ${threat.id}`,
          `- **Category:** ${threat.category}`,
          `- **Status:** ${threat.status}`,
          `- **Score:** ${calculateThreatScore(threat)}/10`,
          `- **Affected Elements:** ${threat.affectedElements.join(', ') || 'None'}`,
          '',
          `**Description:** ${threat.description}`,
          ''
        );
        
        if (threat.mitigations.length > 0) {
          lines.push('**Mitigations:**', '');
          for (const mitigation of threat.mitigations) {
            lines.push(
              `- **${mitigation.title}** (${mitigation.status})`,
              `  - Effort: ${mitigation.effort}`,
              `  - Impact: ${mitigation.impact}`,
              ''
            );
          }
        }
        
        lines.push('---', '');
      }
    }
  }
  
  return lines.join('\n');
}
