/**
 * Unit tests for OutputFormatter classes
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  JSONFormatter,
  SARIFFormatter,
  CSVFormatter,
  XMLFormatter,
  HTMLFormatter,
  MarkdownFormatter,
  OutputFormatterFactory,
} from './OutputFormatter';
import { mockAccessibilityReport, mockFindings } from '../test/fixtures';

import * as FormatterIndex from './index';

// Also import from index to get coverage
import type { OutputFormat } from '../types';

describe('JSONFormatter', () => {
  let formatter: JSONFormatter;

  beforeEach(() => {
    formatter = new JSONFormatter();
  });

  it('should format report as JSON', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(() => JSON.parse(output)).not.toThrow();
    const parsed = JSON.parse(output);
    expect(parsed).toHaveProperty('metadata');
    expect(parsed).toHaveProperty('score');
    expect(parsed).toHaveProperty('findings');
  });

  it('should produce valid JSON with indentation', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('\n');
    expect(output).toContain('  ');
  });

  it('should include all report properties', () => {
    const output = formatter.format(mockAccessibilityReport);
    const parsed = JSON.parse(output);
    
    expect(parsed.metadata.timestamp).toBeDefined();
    expect(parsed.score.overall).toBe(mockAccessibilityReport.score.overall);
    expect(parsed.findings.length).toBe(mockAccessibilityReport.findings.length);
  });
});

describe('SARIFFormatter', () => {
  let formatter: SARIFFormatter;

  beforeEach(() => {
    formatter = new SARIFFormatter();
  });

  it('should format report as SARIF', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(() => JSON.parse(output)).not.toThrow();
    const parsed = JSON.parse(output);
    expect(parsed.version).toBe('2.1.0');
    expect(parsed.$schema).toContain('sarif');
  });

  it('should include runs array', () => {
    const output = formatter.format(mockAccessibilityReport);
    const parsed = JSON.parse(output);
    
    expect(parsed.runs).toBeInstanceOf(Array);
    expect(parsed.runs.length).toBe(1);
  });

  it('should map findings to SARIF results', () => {
    const output = formatter.format(mockAccessibilityReport);
    const parsed = JSON.parse(output);
    
    const results = parsed.runs[0].results;
    expect(results.length).toBe(mockAccessibilityReport.findings.length);
    expect(results[0]).toHaveProperty('ruleId');
    expect(results[0]).toHaveProperty('level');
    expect(results[0]).toHaveProperty('message');
  });

  it('should map severity correctly', () => {
    const output = formatter.format(mockAccessibilityReport);
    const parsed = JSON.parse(output);
    
    const results = parsed.runs[0].results;
    const criticalResult = results.find((r: any) => 
      mockFindings.find(f => f.id === r.ruleId)?.severity === 'critical'
    );
    
    expect(criticalResult.level).toBe('error');
  });

  it('should map minor severity to note level', () => {
    const reportWithMinor = {
      ...mockAccessibilityReport,
      findings: [
        { ...mockAccessibilityReport.findings[0], severity: 'minor' as const }
      ]
    };
    
    const output = formatter.format(reportWithMinor);
    const parsed = JSON.parse(output);
    
    expect(parsed.runs[0].results[0].level).toBe('note');
  });

  it('should include tool information', () => {
    const output = formatter.format(mockAccessibilityReport);
    const parsed = JSON.parse(output);
    
    expect(parsed.runs[0].tool.driver.name).toBe('@ghatana/accessibility-audit');
    expect(parsed.runs[0].tool.driver.version).toBeDefined();
  });
});

describe('CSVFormatter', () => {
  let formatter: CSVFormatter;

  beforeEach(() => {
    formatter = new CSVFormatter();
  });

  it('should format report as CSV', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain(',');
    expect(output).toContain('\n');
  });

  it('should include CSV headers', () => {
    const output = formatter.format(mockAccessibilityReport);
    const lines = output.split('\n');
    
    expect(lines[0]).toContain('Rule ID');
    expect(lines[0]).toContain('Description');
    expect(lines[0]).toContain('Severity');
    expect(lines[0]).toContain('WCAG');
  });

  it('should have correct number of rows', () => {
    const output = formatter.format(mockAccessibilityReport);
    const lines = output.split('\n').filter(line => line.trim());
    
    // Header + findings
    expect(lines.length).toBe(mockAccessibilityReport.findings.length + 1);
  });

  it('should escape commas in values', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    // If description contains comma, it should be quoted
    if (mockAccessibilityReport.findings.some(f => f.description.includes(','))) {
      expect(output).toContain('"');
    }
  });

  it('should handle special characters', () => {
    const reportWithQuotes = {
      ...mockAccessibilityReport,
      findings: [{
        ...mockFindings[0],
        description: 'Test "quoted" text',
      }],
    };
    
    const output = formatter.format(reportWithQuotes);
    expect(output).toContain('""'); // Escaped quotes
  });
});

describe('XMLFormatter', () => {
  let formatter: XMLFormatter;

  beforeEach(() => {
    formatter = new XMLFormatter();
  });

  it('should format report as XML', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('<?xml version="1.0" encoding="UTF-8"?>');
    expect(output).toContain('<accessibility-report>');
    expect(output).toContain('</accessibility-report>');
  });

  it('should include metadata section', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('<metadata>');
    expect(output).toContain('</metadata>');
    expect(output).toContain('<timestamp>');
  });

  it('should include score section', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('<score>');
    expect(output).toContain('</score>');
    expect(output).toContain('<overall>');
  });

  it('should include findings', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('<findings>');
    expect(output).toContain('<finding>');
    expect(output).toContain('</finding>');
  });

  it('should escape XML special characters', () => {
    const reportWithSpecialChars = {
      ...mockAccessibilityReport,
      findings: [{
        ...mockFindings[0],
        description: 'Test <tag> & "quotes"',
      }],
    };
    
    const output = formatter.format(reportWithSpecialChars);
    expect(output).toContain('&lt;');
    expect(output).toContain('&amp;');
    expect(output).toContain('&quot;');
  });
});

describe('HTMLFormatter', () => {
  let formatter: HTMLFormatter;

  beforeEach(() => {
    formatter = new HTMLFormatter();
  });

  it('should format report as HTML', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('<!DOCTYPE html>');
    expect(output).toContain('<html');
    expect(output).toContain('</html>');
  });

  it('should include embedded CSS', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('<style>');
    expect(output).toContain('</style>');
  });

  it('should include score card', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('Overall Score');
    expect(output).toContain(mockAccessibilityReport.score.overall.toString());
    expect(output).toContain(mockAccessibilityReport.score.grade);
  });

  it('should include findings section', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('Findings');
    mockAccessibilityReport.findings.forEach(finding => {
      expect(output).toContain(finding.id);
    });
  });

  it('should include severity badges', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('findings-group');
    expect(output).toContain('critical');
    expect(output).toContain('serious');
  });

  it('should include interactive JavaScript', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('<script>');
    expect(output).toContain('</script>');
  });

  it('should be responsive', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('viewport');
    expect(output).toContain('width=device-width');
  });
});

describe('MarkdownFormatter', () => {
  let formatter: MarkdownFormatter;

  beforeEach(() => {
    formatter = new MarkdownFormatter();
  });

  it('should format report as Markdown', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('#');
    expect(output).toContain('##');
  });

  it('should include title', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('# Accessibility Audit Report');
  });

  it('should include score section', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('## Overall Score');
    expect(output).toContain(mockAccessibilityReport.score.overall.toString());
  });

  it('should include findings as list', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('## Findings');
    expect(output).toContain('-'); // List marker
  });

  it('should use markdown formatting', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('**'); // Bold
    expect(output).toContain('`'); // Code
  });

  it('should include metadata table', () => {
    const output = formatter.format(mockAccessibilityReport);
    
    expect(output).toContain('|'); // Table separator
    expect(output).toContain('Target');
    expect(output).toContain('Dimension');
  });
});

describe('OutputFormatterFactory', () => {
  let factory: OutputFormatterFactory;

  beforeEach(() => {
    factory = OutputFormatterFactory.getInstance();
  });

  it('should return singleton instance', () => {
    const instance1 = OutputFormatterFactory.getInstance();
    const instance2 = OutputFormatterFactory.getInstance();
    expect(instance1).toBe(instance2);
  });

  it('should create JSON formatter', () => {
    const formatter = factory.getFormatter('json');
    expect(formatter).toBeInstanceOf(JSONFormatter);
  });

  it('should create SARIF formatter', () => {
    const formatter = factory.getFormatter('sarif');
    expect(formatter).toBeInstanceOf(SARIFFormatter);
  });

  it('should create CSV formatter', () => {
    const formatter = factory.getFormatter('csv');
    expect(formatter).toBeInstanceOf(CSVFormatter);
  });

  it('should create XML formatter', () => {
    const formatter = factory.getFormatter('xml');
    expect(formatter).toBeInstanceOf(XMLFormatter);
  });

  it('should create HTML formatter', () => {
    const formatter = factory.getFormatter('html');
    expect(formatter).toBeInstanceOf(HTMLFormatter);
  });

  it('should create Markdown formatter', () => {
    const formatter = factory.getFormatter('markdown');
    expect(formatter).toBeInstanceOf(MarkdownFormatter);
  });

  it('should throw error for unknown format', () => {
    expect(() => {
      factory.getFormatter('unknown' as OutputFormat);
    }).toThrow();
  });

  it('should list all supported formats', () => {
    const formats = OutputFormatterFactory.getSupportedFormats();
    
    expect(formats).toContain('json');
    expect(formats).toContain('sarif');
    expect(formats).toContain('csv');
    expect(formats).toContain('xml');
    expect(formats).toContain('html');
    expect(formats).toContain('markdown');
    expect(formats.length).toBe(6);
  });

  it('should cache formatters', () => {
    const formatter1 = factory.getFormatter('json');
    const formatter2 = factory.getFormatter('json');
    
    // Same instance should be returned
    expect(formatter1).toBe(formatter2);
  });

  it('should support static create method', () => {
    const formatter = OutputFormatterFactory.create('json');
    expect(formatter).toBeInstanceOf(JSONFormatter);
  });

  it('should support static getFormatter as alias for create', () => {
    const formatter = OutputFormatterFactory.getFormatter('json');
    expect(formatter).toBeInstanceOf(JSONFormatter);
    
    // Should be same as create
    const formatter2 = OutputFormatterFactory.create('json');
    expect(formatter).toBe(formatter2);
  });

  it('should support instance getFormatter method', () => {
    const instance = OutputFormatterFactory.getInstance();
    const formatter = instance.getFormatter('json');
    expect(formatter).toBeInstanceOf(JSONFormatter);
  });

  it('should throw for unknown format using static create', () => {
    expect(() => {
      OutputFormatterFactory.create('unknown' as OutputFormat);
    }).toThrow('Unknown output format');
  });

  it('should throw for unknown format using static getFormatter', () => {
    expect(() => {
      OutputFormatterFactory.getFormatter('unknown' as OutputFormat);
    }).toThrow('Unknown output format');
  });
});

describe('Deprecated formatterFactory', () => {
  it('should support deprecated formatterFactory export', async () => {
    const { formatterFactory } = await import('./OutputFormatter');
    
    const formatter = formatterFactory.getFormatter('json');
    expect(formatter).toBeInstanceOf(JSONFormatter);
  });

  it('should support getSupportedFormats on deprecated export', async () => {
    const { formatterFactory } = await import('./OutputFormatter');
    
    const formats = formatterFactory.getSupportedFormats();
    expect(formats).toContain('json');
    expect(formats.length).toBe(6);
  });
});

describe('Integration Tests', () => {
  it('should format same report in all formats without errors', () => {
    const factory = OutputFormatterFactory.getInstance();
    const formats: OutputFormat[] = ['json', 'sarif', 'csv', 'xml', 'html', 'markdown'];
    
    formats.forEach(format => {
      const formatter = factory.getFormatter(format);
      expect(() => {
        formatter.format(mockAccessibilityReport);
      }).not.toThrow();
    });
  });

  it('should produce different outputs for different formats', () => {
    const factory = OutputFormatterFactory.getInstance();
    const jsonOutput = factory.getFormatter('json').format(mockAccessibilityReport);
    const htmlOutput = factory.getFormatter('html').format(mockAccessibilityReport);
    const csvOutput = factory.getFormatter('csv').format(mockAccessibilityReport);
    
    expect(jsonOutput).not.toBe(htmlOutput);
    expect(jsonOutput).not.toBe(csvOutput);
    expect(htmlOutput).not.toBe(csvOutput);
  });

  it('should preserve data integrity across formats', () => {
    const factory = OutputFormatterFactory.getInstance();
    const jsonOutput = factory.getFormatter('json').format(mockAccessibilityReport);
    const parsed = JSON.parse(jsonOutput);
    
    // Verify key data is preserved
    expect(parsed.score.overall).toBe(mockAccessibilityReport.score.overall);
    expect(parsed.findings.length).toBe(mockAccessibilityReport.findings.length);
    expect(parsed.metadata.id).toBe(mockAccessibilityReport.metadata.id);
  });
});

describe('Security Tests', () => {
  const maliciousReport = {
    ...mockAccessibilityReport,
    findings: [
      {
        ...mockAccessibilityReport.findings[0],
        description: '<script>alert("XSS")</script>',
        help: '<img src=x onerror=alert("XSS")>',
      },
      {
        ...mockAccessibilityReport.findings[0],
        description: '"; DROP TABLE findings;--',
        help: '\'OR\'1\'=\'1',
      },
    ],
  };

  describe('XSS Prevention', () => {
    it('should escape HTML entities in HTML formatter', () => {
      const factory = OutputFormatterFactory.getInstance();
      const htmlOutput = factory.getFormatter('html').format(maliciousReport);
      
      // Malicious script should be escaped in content area
      expect(htmlOutput).not.toContain('alert("XSS")');
      expect(htmlOutput).toContain('&lt;script&gt;');
      expect(htmlOutput).toContain('&lt;img');
    });

    it('should escape XML entities in XML formatter', () => {
      const factory = OutputFormatterFactory.getInstance();
      const xmlOutput = factory.getFormatter('xml').format(maliciousReport);
      
      // XML special characters should be escaped
      expect(xmlOutput).not.toContain('<script>');
      expect(xmlOutput).toContain('&lt;script&gt;');
    });

    it('should escape quotes in CSV formatter to prevent CSV injection', () => {
      const csvInjectionReport = {
        ...mockAccessibilityReport,
        findings: [
          {
            ...mockAccessibilityReport.findings[0],
            description: '=1+1",=1+1\',@SUM(1+1),-1+1,+1+1',
            help: 'CSV injection attempt',
          },
        ],
      };

      const factory = OutputFormatterFactory.getInstance();
      const csvOutput = factory.getFormatter('csv').format(csvInjectionReport);
      
      // CSV formulas should be escaped or quoted
      expect(csvOutput).toContain('"');
      // Should not have unescaped commas in field values
      const lines = csvOutput.split('\n');
      expect(lines.length).toBeGreaterThan(0);
    });

    it('should handle markdown injection attempts', () => {
      const mdInjectionReport = {
        ...mockAccessibilityReport,
        findings: [
          {
            ...mockAccessibilityReport.findings[0],
            description: '[Click here](javascript:alert("XSS"))',
            help: '![](https://evil.com/tracking.png)',
          },
        ],
      };

      const factory = OutputFormatterFactory.getInstance();
      const mdOutput = factory.getFormatter('markdown').format(mdInjectionReport);
      
      // Should still generate valid markdown
      expect(mdOutput).toContain('##');
      expect(mdOutput.length).toBeGreaterThan(0);
    });
  });

  describe('Edge Cases & Error Handling', () => {
    it('should handle empty findings array', () => {
      const emptyReport = {
        ...mockAccessibilityReport,
        findings: [],
      };

      const factory = OutputFormatterFactory.getInstance();
      const formats: OutputFormat[] = ['json', 'html', 'csv', 'xml', 'markdown', 'sarif'];
      
      formats.forEach(format => {
        const formatter = factory.getFormatter(format);
        const output = formatter.format(emptyReport);
        expect(output).toBeTruthy();
        expect(output.length).toBeGreaterThan(0);
      });
    });

    it('should handle very long strings without truncation', () => {
      const longString = 'A'.repeat(10000);
      const longReport = {
        ...mockAccessibilityReport,
        findings: [
          {
            ...mockAccessibilityReport.findings[0],
            description: longString,
          },
        ],
      };

      const factory = OutputFormatterFactory.getInstance();
      const jsonOutput = factory.getFormatter('json').format(longReport);
      
      expect(jsonOutput).toContain(longString);
    });

    it('should handle special Unicode characters', () => {
      const unicodeReport = {
        ...mockAccessibilityReport,
        findings: [
          {
            ...mockAccessibilityReport.findings[0],
            description: '你好世界 🚀 émojis ñ áéíóú',
            help: 'Тест кирилицы',
          },
        ],
      };

      const factory = OutputFormatterFactory.getInstance();
      const formats: OutputFormat[] = ['json', 'html', 'csv', 'xml', 'markdown'];
      
      formats.forEach(format => {
        const formatter = factory.getFormatter(format);
        expect(() => {
          formatter.format(unicodeReport);
        }).not.toThrow();
      });
    });

    it('should handle null/undefined values gracefully', () => {
      const partialReport = {
        ...mockAccessibilityReport,
        findings: [
          {
            ...mockAccessibilityReport.findings[0],
            description: null as any,
            help: undefined as any,
          },
        ],
      };

      const factory = OutputFormatterFactory.getInstance();
      expect(() => {
        factory.getFormatter('json').format(partialReport);
      }).not.toThrow();
    });
  });

  describe('Formatter Metadata Methods', () => {
    it('should return correct file extensions for all formatters', () => {
      const factory = OutputFormatterFactory.getInstance();
      
      expect(factory.getFormatter('json').getFileExtension()).toBe('json');
      expect(factory.getFormatter('sarif').getFileExtension()).toBe('sarif');
      expect(factory.getFormatter('csv').getFileExtension()).toBe('csv');
      expect(factory.getFormatter('xml').getFileExtension()).toBe('xml');
      expect(factory.getFormatter('html').getFileExtension()).toBe('html');
      expect(factory.getFormatter('markdown').getFileExtension()).toBe('md');
    });

    it('should return correct MIME types for all formatters', () => {
      const factory = OutputFormatterFactory.getInstance();
      
      expect(factory.getFormatter('json').getMimeType()).toBe('application/json');
      expect(factory.getFormatter('sarif').getMimeType()).toBe('application/sarif+json');
      expect(factory.getFormatter('csv').getMimeType()).toBe('text/csv');
      expect(factory.getFormatter('xml').getMimeType()).toBe('application/xml');
      expect(factory.getFormatter('html').getMimeType()).toBe('text/html');
      expect(factory.getFormatter('markdown').getMimeType()).toBe('text/markdown');
    });

    it('should export all formatters from index', () => {
      // Test that index exports work by creating instances
      const jsonFormatter = new FormatterIndex.JSONFormatter();
      const sarifFormatter = new FormatterIndex.SARIFFormatter();
      const csvFormatter = new FormatterIndex.CSVFormatter();
      const xmlFormatter = new FormatterIndex.XMLFormatter();
      const htmlFormatter = new FormatterIndex.HTMLFormatter();
      const mdFormatter = new FormatterIndex.MarkdownFormatter();
      const factory = FormatterIndex.OutputFormatterFactory.getInstance();
      const deprecatedFactory = FormatterIndex.formatterFactory;
      
      expect(jsonFormatter).toBeInstanceOf(FormatterIndex.JSONFormatter);
      expect(sarifFormatter).toBeInstanceOf(FormatterIndex.SARIFFormatter);
      expect(csvFormatter).toBeInstanceOf(FormatterIndex.CSVFormatter);
      expect(xmlFormatter).toBeInstanceOf(FormatterIndex.XMLFormatter);
      expect(htmlFormatter).toBeInstanceOf(FormatterIndex.HTMLFormatter);
      expect(mdFormatter).toBeInstanceOf(FormatterIndex.MarkdownFormatter);
      expect(factory).toBeDefined();
      expect(deprecatedFactory).toBeDefined();
      expect(deprecatedFactory.getSupportedFormats().length).toBe(6);
    });
  });

  describe('Branch Coverage - Edge Cases', () => {
    describe('HTMLFormatter branches', () => {
      it('should render trend with positive changePercentage', () => {
        const reportWithPositiveTrend = {
          ...mockAccessibilityReport,
          score: {
            ...mockAccessibilityReport.score,
            trend: {
              direction: 'improving' as const,
              previousScore: 70,
              change: 10,
              changePercentage: 14.3,
              projectedScore: 90
            }
          }
        };
        
        const formatter = new HTMLFormatter();
        const html = formatter.format(reportWithPositiveTrend);
        
        expect(html).toContain('trend');
        expect(html).toContain('IMPROVING');
        expect(html).toContain('+14.3');
      });

      it('should render trend with negative changePercentage', () => {
        const reportWithNegativeTrend = {
          ...mockAccessibilityReport,
          score: {
            ...mockAccessibilityReport.score,
            trend: {
              direction: 'degrading' as const,
              previousScore: 90,
              change: -10,
              changePercentage: -11.1,
              projectedScore: 70
            }
          }
        };
        
        const formatter = new HTMLFormatter();
        const html = formatter.format(reportWithNegativeTrend);
        
        expect(html).toContain('trend');
        expect(html).toContain('DEGRADING');
        expect(html).toContain('-11.1');
      });

      it('should render findings with file location', () => {
        const reportWithFileLocation = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              location: {
                ...mockAccessibilityReport.findings[0].location,
                file: 'src/components/Form.tsx',
                line: 42
              }
            }
          ]
        };
        
        const formatter = new HTMLFormatter();
        const html = formatter.format(reportWithFileLocation);
        
        expect(html).toContain('Form.tsx');
        expect(html).toContain('42');
      });

      it('should render all recommendation categories when present', () => {
        const reportWithAllRecommendations = {
          ...mockAccessibilityReport,
          recommendations: {
            immediate: ['Fix critical issue now'],
            shortTerm: ['Address this soon'],
            longTerm: ['Consider future improvements']
          }
        };
        
        const formatter = new HTMLFormatter();
        const html = formatter.format(reportWithAllRecommendations);
        
        expect(html).toContain('Immediate');
        expect(html).toContain('Fix critical issue now');
        expect(html).toContain('Short-term');
        expect(html).toContain('Address this soon');
        expect(html).toContain('Long-term');
        expect(html).toContain('Consider future improvements');
      });

      it('should handle empty recommendation categories', () => {
        const reportWithPartialRecommendations = {
          ...mockAccessibilityReport,
          recommendations: {
            immediate: ['Only immediate'],
            shortTerm: [],
            longTerm: []
          }
        };
        
        const formatter = new HTMLFormatter();
        const html = formatter.format(reportWithPartialRecommendations);
        
        expect(html).toContain('Only immediate');
        // Empty categories should not render their sections
        const shortTermMatches = html.match(/Short-term/g);
        const longTermMatches = html.match(/Long-term/g);
        expect(shortTermMatches).toBeNull();
        expect(longTermMatches).toBeNull();
      });

      it('should handle grade with plus sign', () => {
        const reportWithPlusGrade = {
          ...mockAccessibilityReport,
          score: {
            ...mockAccessibilityReport.score,
            grade: 'A+' as const
          }
        };
        
        const formatter = new HTMLFormatter();
        const html = formatter.format(reportWithPlusGrade);
        
        expect(html).toContain('aplus'); // + replaced with 'plus'
      });

      it('should handle grade with minus sign', () => {
        const reportWithMinusGrade = {
          ...mockAccessibilityReport,
          score: {
            ...mockAccessibilityReport.score,
            grade: 'B-' as const
          }
        };
        
        const formatter = new HTMLFormatter();
        const html = formatter.format(reportWithMinusGrade);
        
        expect(html).toContain('bminus'); // - replaced with 'minus'
      });
    });

    describe('CSV/XML special character handling', () => {
      it('should escape CSV values with commas', () => {
        const reportWithCommas = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              description: 'This description contains, several, commas'
            }
          ]
        };
        
        const formatter = new CSVFormatter();
        const csv = formatter.format(reportWithCommas);
        
        expect(csv).toContain('"This description contains, several, commas"');
      });

      it('should escape CSV values with quotes', () => {
        const reportWithQuotes = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              description: 'This has "quoted" text'
            }
          ]
        };
        
        const formatter = new CSVFormatter();
        const csv = formatter.format(reportWithQuotes);
        
        expect(csv).toContain('This has ""quoted"" text'); // Quotes should be escaped as ""
      });

      it('should escape CSV values with newlines', () => {
        const reportWithNewlines = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              description: 'Line 1\nLine 2'
            }
          ]
        };
        
        const formatter = new CSVFormatter();
        const csv = formatter.format(reportWithNewlines);
        
        expect(csv).toContain('"'); // Should be quoted due to newline
      });

      it('should handle null/undefined values in XML', () => {
        const reportWithNullValues = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              location: {
                ...mockAccessibilityReport.findings[0].location,
                file: undefined,
                line: undefined
              }
            }
          ]
        };
        
        const formatter = new XMLFormatter();
        const xml = formatter.format(reportWithNullValues);
        
        // Should not crash and should handle undefined gracefully
        expect(xml).toContain('<?xml');
        expect(xml).toContain('<findings>');
      });
    });

    describe('MarkdownFormatter branches', () => {
      it('should show findings section when findings present', () => {
        const formatter = new MarkdownFormatter();
        const md = formatter.format(mockAccessibilityReport);
        
        expect(md).toContain('# Accessibility Audit Report');
        expect(md).toContain('## Findings');
        // Should not contain the "no issues" message when there are findings
        expect(md).not.toContain('**No accessibility issues found!**');
      });

      it('should list findings with severity tags', () => {
        const formatter = new MarkdownFormatter();
        const md = formatter.format(mockAccessibilityReport);
        
        expect(md).toContain('## Findings');
        expect(md).toContain('###');
        expect(md).toContain('[CRITICAL]');
      });
    });

    describe('SARIF formatter rule deduplication', () => {
      it('should deduplicate rules with same ID', () => {
        const reportWithDuplicateRules = {
          ...mockAccessibilityReport,
          findings: [
            { ...mockAccessibilityReport.findings[0], id: 'duplicate-rule' },
            { ...mockAccessibilityReport.findings[0], id: 'duplicate-rule' },
            { ...mockAccessibilityReport.findings[0], id: 'unique-rule' }
          ]
        };
        
        const formatter = new SARIFFormatter();
        const sarif = formatter.format(reportWithDuplicateRules);
        const parsed = JSON.parse(sarif);
        
        const rules = parsed.runs[0].tool.driver.rules;
        const ruleIds = rules.map((r: any) => r.id);
        
        // Should only have 2 unique rules
        expect(new Set(ruleIds).size).toBe(2);
        expect(ruleIds).toContain('duplicate-rule');
        expect(ruleIds).toContain('unique-rule');
      });

      it('should include region with line and column in SARIF when present', () => {
        const reportWithLineColumn = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              location: {
                ...mockAccessibilityReport.findings[0].location,
                file: 'test.ts',
                line: 42,
                column: 10
              }
            }
          ]
        };
        
        const formatter = new SARIFFormatter();
        const sarif = formatter.format(reportWithLineColumn);
        const parsed = JSON.parse(sarif);
        
        const result = parsed.runs[0].results[0];
        expect(result.locations[0].physicalLocation.region).toBeDefined();
        expect(result.locations[0].physicalLocation.region.startLine).toBe(42);
        expect(result.locations[0].physicalLocation.region.startColumn).toBe(10);
      });

      it('should omit region in SARIF when line is not present', () => {
        const reportWithoutLine = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              location: {
                ...mockAccessibilityReport.findings[0].location,
                line: undefined
              }
            }
          ]
        };
        
        const formatter = new SARIFFormatter();
        const sarif = formatter.format(reportWithoutLine);
        const parsed = JSON.parse(sarif);
        
        const result = parsed.runs[0].results[0];
        expect(result.locations[0].physicalLocation.region).toBeUndefined();
      });

      it('should default startColumn to 1 when column is not provided', () => {
        const reportWithoutColumn = {
          ...mockAccessibilityReport,
          findings: [
            {
              ...mockAccessibilityReport.findings[0],
              location: {
                ...mockAccessibilityReport.findings[0].location,
                file: 'test.ts',
                line: 42,
                column: undefined
              }
            }
          ]
        };
        
        const formatter = new SARIFFormatter();
        const sarif = formatter.format(reportWithoutColumn);
        const parsed = JSON.parse(sarif);
        
        const result = parsed.runs[0].results[0];
        expect(result.locations[0].physicalLocation.region.startColumn).toBe(1);
      });
    });
  });
});

