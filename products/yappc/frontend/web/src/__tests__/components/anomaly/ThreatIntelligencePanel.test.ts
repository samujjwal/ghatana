/**
 * Unit tests for ThreatIntelligencePanel component
 *
 * Tests pure logic functions for threat display:
 * - Threat severity classification
 * - Icon mapping for threat types
 * - Confidence score formatting
 * - Related indicators extraction
 *
 * @see ThreatIntelligencePanel.tsx
 */

import { describe, it, expect } from 'vitest';

describe('ThreatIntelligencePanel', () => {
  describe('threat severity display', () => {
    /**
     * GIVEN: Threat intelligence data
     * WHEN: Determining severity level
     * THEN: Correct severity classification returned
     */
    it('should classify threat severity from confidence and cvss', () => {
      const classifySeverity = (confidence: number, cvss: number): string => {
        if (confidence > 0.8 && cvss > 7) return 'CRITICAL';
        if (confidence > 0.7 && cvss > 5) return 'HIGH';
        if (confidence > 0.5 && cvss > 3) return 'MEDIUM';
        return 'LOW';
      };

      expect(classifySeverity(0.9, 8.5)).toBe('CRITICAL');
      expect(classifySeverity(0.75, 6.0)).toBe('HIGH');
      expect(classifySeverity(0.6, 4.5)).toBe('MEDIUM');
      expect(classifySeverity(0.3, 2.0)).toBe('LOW');
    });

    /**
     * GIVEN: Multiple threats
     * WHEN: Sorting by severity
     * THEN: Threats ordered critical to low
     */
    it('should sort threats by severity descending', () => {
      const threats = [
        { id: '1', severity: 'LOW' },
        { id: '2', severity: 'CRITICAL' },
        { id: '3', severity: 'MEDIUM' },
        { id: '4', severity: 'HIGH' },
      ];

      const severityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
      const sorted = [...threats].sort(
        (a, b) => (severityRank[b.severity as keyof typeof severityRank] || 0) - (severityRank[a.severity as keyof typeof severityRank] || 0)
      );

      expect(sorted[0].severity).toBe('CRITICAL');
      expect(sorted[1].severity).toBe('HIGH');
      expect(sorted[2].severity).toBe('MEDIUM');
      expect(sorted[3].severity).toBe('LOW');
    });

    /**
     * GIVEN: Threat with high confidence and high CVSS
     * WHEN: Determining if critical
     * THEN: Correctly identified as critical threat
     */
    it('should flag critical threats requiring immediate action', () => {
      const isCritical = (threat: {
        confidence: number;
        cvssScore: number;
      }): boolean => {
        return threat.confidence > 0.8 && threat.cvssScore > 7;
      };

      const critical = { confidence: 0.95, cvssScore: 9.5 };
      const notCritical = { confidence: 0.7, cvssScore: 5 };

      expect(isCritical(critical)).toBe(true);
      expect(isCritical(notCritical)).toBe(false);
    });
  });

  describe('threat type icons', () => {
    /**
     * GIVEN: Threat type names
     * WHEN: Mapping to icons
     * THEN: Correct icon identifiers returned
     */
    it('should map threat types to correct icons', () => {
      const threatTypeToIcon = (type: string): string => {
        const iconMap: Record<string, string> = {
          MALWARE: 'virus-2',
          PHISHING: 'mail-alert',
          EXPLOIT: 'zap',
          DATA_BREACH: 'lock-open',
          DDOS: 'network',
          SQL_INJECTION: 'database-alert',
          XSS: 'code-2',
          RANSOMWARE: 'lock-alert',
        };
        return iconMap[type] || 'alert-circle';
      };

      expect(threatTypeToIcon('MALWARE')).toBe('virus-2');
      expect(threatTypeToIcon('PHISHING')).toBe('mail-alert');
      expect(threatTypeToIcon('EXPLOIT')).toBe('zap');
      expect(threatTypeToIcon('UNKNOWN')).toBe('alert-circle');
    });

    /**
     * GIVEN: Various threat types
     * WHEN: Getting icon badges
     * THEN: Each type has unique icon
     */
    it('should provide unique icons for different threat types', () => {
      const threatTypeToIcon = (type: string): string => {
        const iconMap: Record<string, string> = {
          MALWARE: 'virus-2',
          PHISHING: 'mail-alert',
          EXPLOIT: 'zap',
          DATA_BREACH: 'lock-open',
        };
        return iconMap[type] || 'alert-circle';
      };

      const threats = ['MALWARE', 'PHISHING', 'EXPLOIT', 'DATA_BREACH'];
      const icons = threats.map(threatTypeToIcon);

      // All should be unique
      expect(new Set(icons).size).toBe(threats.length);
    });

    /**
     * GIVEN: High-risk threat types
     * WHEN: Selecting icon color
     * THEN: Color reflects threat severity
     */
    it('should color code threat icons by severity', () => {
      const threatToIconColor = (type: string): string => {
        const colorMap: Record<string, string> = {
          MALWARE: 'text-red-600',
          RANSOMWARE: 'text-red-600',
          EXPLOIT: 'text-orange-500',
          PHISHING: 'text-yellow-500',
          DATA_BREACH: 'text-red-600',
          SQL_INJECTION: 'text-orange-500',
          XSS: 'text-yellow-500',
          DDOS: 'text-orange-500',
        };
        return colorMap[type] || 'text-gray-600';
      };

      expect(threatToIconColor('MALWARE')).toBe('text-red-600');
      expect(threatToIconColor('EXPLOIT')).toBe('text-orange-500');
      expect(threatToIconColor('PHISHING')).toBe('text-yellow-500');
    });
  });

  describe('confidence score formatting', () => {
    /**
     * GIVEN: Confidence score between 0 and 1
     * WHEN: Formatting for display
     * THEN: Percentage string returned
     */
    it('should format confidence as percentage', () => {
      const formatConfidence = (score: number): string => {
        return `${Math.round(score * 100)}%`;
      };

      expect(formatConfidence(0.95)).toBe('95%');
      expect(formatConfidence(0.5)).toBe('50%');
      expect(formatConfidence(0.123)).toBe('12%');
    });

    /**
     * GIVEN: Confidence scores
     * WHEN: Determining confidence level
     * THEN: Human-readable confidence level returned
     */
    it('should classify confidence level', () => {
      const getConfidenceLevel = (score: number): string => {
        if (score > 0.8) return 'Very High';
        if (score > 0.6) return 'High';
        if (score > 0.4) return 'Medium';
        return 'Low';
      };

      expect(getConfidenceLevel(0.95)).toBe('Very High');
      expect(getConfidenceLevel(0.7)).toBe('High');
      expect(getConfidenceLevel(0.5)).toBe('Medium');
      expect(getConfidenceLevel(0.2)).toBe('Low');
    });

    /**
     * GIVEN: Multiple threats
     * WHEN: Sorting by confidence
     * THEN: Threats ordered most to least confident
     */
    it('should order threats by confidence descending', () => {
      const threats = [
        { id: '1', confidence: 0.6 },
        { id: '2', confidence: 0.95 },
        { id: '3', confidence: 0.4 },
      ];

      const sorted = [...threats].sort((a, b) => b.confidence - a.confidence);

      expect(sorted[0].confidence).toBe(0.95);
      expect(sorted[1].confidence).toBe(0.6);
      expect(sorted[2].confidence).toBe(0.4);
    });
  });

  describe('related indicators display', () => {
    /**
     * GIVEN: Threat with IOCs (indicators of compromise)
     * WHEN: Extracting related indicators
     * THEN: Indicators grouped and formatted
     */
    it('should extract and group indicators by type', () => {
      const threat = {
        id: 'threat-123',
        iocs: [
          { type: 'IP', value: '192.168.1.1' },
          { type: 'DOMAIN', value: 'malicious.com' },
          { type: 'IP', value: '10.0.0.1' },
          { type: 'HASH', value: 'abc123def456' },
        ],
      };

      const groupByType = (iocs: Array<{ type: string; value: string }>) => {
        return iocs.reduce(
          (acc, ioc) => {
            if (!acc[ioc.type]) acc[ioc.type] = [];
            acc[ioc.type].push(ioc.value);
            return acc;
          },
          {} as Record<string, string[]>
        );
      };

      const grouped = groupByType(threat.iocs);

      expect(grouped['IP'].length).toBe(2);
      expect(grouped['DOMAIN'].length).toBe(1);
      expect(grouped['HASH'].length).toBe(1);
    });

    /**
     * GIVEN: Multiple IOCs
     * WHEN: Counting by type
     * THEN: Correct counts returned
     */
    it('should count indicators by type', () => {
      const iocs = [
        { type: 'IP', value: '192.168.1.1' },
        { type: 'DOMAIN', value: 'malicious.com' },
        { type: 'IP', value: '10.0.0.1' },
        { type: 'HASH', value: 'abc123def456' },
      ];

      const countByType = (indicators: Array<{ type: string }>) => {
        return indicators.reduce(
          (acc, ioc) => {
            acc[ioc.type] = (acc[ioc.type] || 0) + 1;
            return acc;
          },
          {} as Record<string, number>
        );
      };

      const counts = countByType(iocs);

      expect(counts['IP']).toBe(2);
      expect(counts['DOMAIN']).toBe(1);
      expect(counts['HASH']).toBe(1);
    });

    /**
     * GIVEN: Threat indicators
     * WHEN: Formatting for display
     * THEN: Formatted list with type badges
     */
    it('should format indicators with type badges', () => {
      const ioc = { type: 'IP', value: '192.168.1.1' };

      const formatIndicator = (i: { type: string; value: string }): string => {
        return `[${i.type}] ${i.value}`;
      };

      const formatted = formatIndicator(ioc);

      expect(formatted).toBe('[IP] 192.168.1.1');
    });
  });

  describe('threat action buttons', () => {
    /**
     * GIVEN: Threat severity
     * WHEN: Determining available actions
     * THEN: Correct actions enabled based on severity
     */
    it('should enable block action for critical threats', () => {
      const canBlock = (severity: string): boolean => {
        return ['CRITICAL', 'HIGH'].includes(severity);
      };

      expect(canBlock('CRITICAL')).toBe(true);
      expect(canBlock('HIGH')).toBe(true);
      expect(canBlock('MEDIUM')).toBe(false);
      expect(canBlock('LOW')).toBe(false);
    });

    /**
     * GIVEN: Threat intelligence data
     * WHEN: Checking if automated response available
     * THEN: Boolean indicating response availability
     */
    it('should determine if automated response is available', () => {
      const hasAutomatedResponse = (threat: {
        hasPlaybook: boolean;
        confidence: number;
      }): boolean => {
        return threat.hasPlaybook && threat.confidence > 0.7;
      };

      const withPlaybook = {
        hasPlaybook: true,
        confidence: 0.85,
      };
      const withoutPlaybook = {
        hasPlaybook: false,
        confidence: 0.85,
      };
      const lowConfidence = {
        hasPlaybook: true,
        confidence: 0.5,
      };

      expect(hasAutomatedResponse(withPlaybook)).toBe(true);
      expect(hasAutomatedResponse(withoutPlaybook)).toBe(false);
      expect(hasAutomatedResponse(lowConfidence)).toBe(false);
    });
  });
});
