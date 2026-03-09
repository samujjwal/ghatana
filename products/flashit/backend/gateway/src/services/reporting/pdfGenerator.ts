/**
 * PDF Generator Service for Flashit
 * Generates PDF reports from summaries with charts and visualizations
 *
 * @doc.type service
 * @doc.purpose Generate PDF exports of summaries and reports
 * @doc.layer product
 * @doc.pattern ReportingService
 */

import PDFDocument from 'pdfkit';
import { PassThrough } from 'stream';
import type { GeneratedSummary, EmotionAnalysis, TopicCluster, Highlight } from './summaryGenerator.js';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface PDFOptions {
  includeCharts?: boolean;
  includeWordCloud?: boolean;
  includeHighlights?: boolean;
  includeRecommendations?: boolean;
  paperSize?: 'A4' | 'Letter';
  colorScheme?: 'light' | 'dark';
  headerImage?: string;
  footerText?: string;
}

export interface PDFGenerationResult {
  buffer: Buffer;
  filename: string;
  mimeType: 'application/pdf';
  size: number;
}

interface ChartData {
  labels: string[];
  values: number[];
  colors?: string[];
}

// ============================================================================
// Color Schemes
// ============================================================================

const COLORS = {
  light: {
    primary: '#4F46E5', // Indigo
    secondary: '#6366F1',
    text: '#1F2937',
    textSecondary: '#6B7280',
    background: '#FFFFFF',
    border: '#E5E7EB',
    success: '#10B981',
    warning: '#F59E0B',
    error: '#EF4444',
    chart: ['#4F46E5', '#06B6D4', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899'],
  },
  dark: {
    primary: '#818CF8',
    secondary: '#A5B4FC',
    text: '#F9FAFB',
    textSecondary: '#9CA3AF',
    background: '#1F2937',
    border: '#374151',
    success: '#34D399',
    warning: '#FBBF24',
    error: '#F87171',
    chart: ['#818CF8', '#22D3EE', '#34D399', '#FBBF24', '#F87171', '#A78BFA', '#F472B6'],
  },
};

// ============================================================================
// PDF Generator Service
// ============================================================================

/**
 * PDFGeneratorService generates PDF reports from summaries
 */
export class PDFGeneratorService {
  private doc: PDFKit.PDFDocument;
  private colors: (typeof COLORS)['light'];
  private options: PDFOptions;
  private pageWidth: number;
  private pageHeight: number;
  private margin: number;
  private contentWidth: number;

  constructor(options: PDFOptions = {}) {
    this.options = {
      includeCharts: true,
      includeWordCloud: true,
      includeHighlights: true,
      includeRecommendations: true,
      paperSize: 'A4',
      colorScheme: 'light',
      ...options,
    };

    this.colors = COLORS[this.options.colorScheme || 'light'];
    this.margin = 50;

    // Set page dimensions
    if (this.options.paperSize === 'Letter') {
      this.pageWidth = 612;
      this.pageHeight = 792;
    } else {
      // A4
      this.pageWidth = 595.28;
      this.pageHeight = 841.89;
    }

    this.contentWidth = this.pageWidth - this.margin * 2;

    this.doc = new PDFDocument({
      size: this.options.paperSize,
      margins: { top: this.margin, bottom: this.margin, left: this.margin, right: this.margin },
      bufferPages: true,
    });
  }

  /**
   * Generate PDF from a summary
   */
  async generateFromSummary(summary: GeneratedSummary): Promise<PDFGenerationResult> {
    return new Promise((resolve, reject) => {
      const chunks: Buffer[] = [];
      const stream = new PassThrough();

      stream.on('data', (chunk) => chunks.push(chunk));
      stream.on('end', () => {
        const buffer = Buffer.concat(chunks);
        resolve({
          buffer,
          filename: this.generateFilename(summary),
          mimeType: 'application/pdf',
          size: buffer.length,
        });
      });
      stream.on('error', reject);

      this.doc.pipe(stream);

      try {
        // Generate PDF content
        this.renderHeader(summary);
        this.renderOverview(summary);

        if (this.options.includeCharts && summary.sphereBreakdown) {
          this.renderSphereBreakdown(summary.sphereBreakdown);
        }

        if (this.options.includeCharts && summary.emotionAnalysis) {
          this.renderEmotionAnalysis(summary.emotionAnalysis);
        }

        if (summary.topicClusters && summary.topicClusters.length > 0) {
          this.renderTopicClusters(summary.topicClusters);
        }

        if (this.options.includeHighlights && summary.highlights) {
          this.renderHighlights(summary.highlights);
        }

        if (this.options.includeRecommendations && summary.recommendations) {
          this.renderRecommendations(summary.recommendations);
        }

        if (summary.keyMoments && summary.keyMoments.length > 0) {
          this.renderKeyMoments(summary.keyMoments);
        }

        if (this.options.includeWordCloud && summary.wordCloud) {
          this.renderWordCloud(summary.wordCloud);
        }

        this.renderFooter(summary);

        this.doc.end();
      } catch (error) {
        reject(error);
      }
    });
  }

  /**
   * Render header section
   */
  private renderHeader(summary: GeneratedSummary): void {
    // Title
    this.doc
      .font('Helvetica-Bold')
      .fontSize(28)
      .fillColor(this.colors.primary)
      .text(summary.title, { align: 'center' });

    // Subtitle
    this.doc
      .moveDown(0.5)
      .font('Helvetica')
      .fontSize(12)
      .fillColor(this.colors.textSecondary)
      .text(
        `Generated on ${summary.generatedAt.toLocaleDateString('en-US', {
          weekday: 'long',
          year: 'numeric',
          month: 'long',
          day: 'numeric',
        })}`,
        { align: 'center' }
      );

    // Stats bar
    this.doc.moveDown(1.5);

    const statsY = this.doc.y;
    const statsBoxWidth = this.contentWidth / 3;

    // Moments count
    this.renderStatBox(
      this.margin,
      statsY,
      statsBoxWidth,
      summary.momentCount.toString(),
      'Moments'
    );

    // Spheres count
    this.renderStatBox(
      this.margin + statsBoxWidth,
      statsY,
      statsBoxWidth,
      Object.keys(summary.sphereBreakdown || {}).length.toString(),
      'Spheres'
    );

    // Period
    this.renderStatBox(
      this.margin + statsBoxWidth * 2,
      statsY,
      statsBoxWidth,
      this.formatPeriod(summary.period),
      'Period'
    );

    this.doc.y = statsY + 60;
    this.doc.moveDown(1);
  }

  /**
   * Render stat box
   */
  private renderStatBox(
    x: number,
    y: number,
    width: number,
    value: string,
    label: string
  ): void {
    this.doc
      .font('Helvetica-Bold')
      .fontSize(24)
      .fillColor(this.colors.primary)
      .text(value, x, y, { width, align: 'center' });

    this.doc
      .font('Helvetica')
      .fontSize(10)
      .fillColor(this.colors.textSecondary)
      .text(label, x, y + 28, { width, align: 'center' });
  }

  /**
   * Render overview section
   */
  private renderOverview(summary: GeneratedSummary): void {
    this.checkPageBreak(150);

    this.renderSectionHeader('Overview');

    this.doc
      .font('Helvetica')
      .fontSize(11)
      .fillColor(this.colors.text)
      .text(summary.overview, {
        align: 'justify',
        lineGap: 4,
      });

    this.doc.moveDown(1.5);
  }

  /**
   * Render sphere breakdown chart
   */
  private renderSphereBreakdown(breakdown: Record<string, number>): void {
    this.checkPageBreak(200);

    this.renderSectionHeader('Moments by Sphere');

    const entries = Object.entries(breakdown)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 8);

    const maxValue = Math.max(...entries.map(([, v]) => v));
    const barHeight = 18;
    const barGap = 6;
    const labelWidth = 100;
    const barMaxWidth = this.contentWidth - labelWidth - 40;

    let y = this.doc.y;

    entries.forEach(([sphere, count], i) => {
      const barWidth = (count / maxValue) * barMaxWidth;
      const color = this.colors.chart[i % this.colors.chart.length];

      // Label
      this.doc
        .font('Helvetica')
        .fontSize(10)
        .fillColor(this.colors.text)
        .text(sphere, this.margin, y + 3, { width: labelWidth, ellipsis: true });

      // Bar
      this.doc
        .rect(this.margin + labelWidth, y, barWidth, barHeight)
        .fill(color);

      // Value
      this.doc
        .font('Helvetica')
        .fontSize(9)
        .fillColor(this.colors.textSecondary)
        .text(count.toString(), this.margin + labelWidth + barWidth + 8, y + 4);

      y += barHeight + barGap;
    });

    this.doc.y = y;
    this.doc.moveDown(1.5);
  }

  /**
   * Render emotion analysis
   */
  private renderEmotionAnalysis(analysis: EmotionAnalysis): void {
    this.checkPageBreak(180);

    this.renderSectionHeader('Emotional Insights');

    // Mood score
    const moodY = this.doc.y;
    this.doc
      .font('Helvetica-Bold')
      .fontSize(48)
      .fillColor(this.colors.primary)
      .text(analysis.moodScore.toString(), this.margin, moodY, { continued: true })
      .font('Helvetica')
      .fontSize(14)
      .fillColor(this.colors.textSecondary)
      .text('/10 Mood Score', { baseline: 'bottom' });

    this.doc
      .font('Helvetica')
      .fontSize(11)
      .fillColor(this.colors.text)
      .text(`Dominant emotion: ${analysis.dominantEmotion}`, this.margin, moodY + 60);

    this.doc
      .fillColor(
        analysis.emotionTrend === 'improving'
          ? this.colors.success
          : analysis.emotionTrend === 'declining'
            ? this.colors.error
            : this.colors.textSecondary
      )
      .text(`Trend: ${analysis.emotionTrend.charAt(0).toUpperCase() + analysis.emotionTrend.slice(1)}`);

    // Insights
    if (analysis.insights && analysis.insights.length > 0) {
      this.doc.moveDown(0.5);
      analysis.insights.forEach((insight) => {
        this.doc
          .font('Helvetica')
          .fontSize(10)
          .fillColor(this.colors.textSecondary)
          .text(`• ${insight}`, { indent: 10 });
      });
    }

    this.doc.moveDown(1.5);
  }

  /**
   * Render topic clusters
   */
  private renderTopicClusters(clusters: TopicCluster[]): void {
    this.checkPageBreak(150);

    this.renderSectionHeader('Key Topics');

    clusters.forEach((cluster, i) => {
      this.checkPageBreak(60);

      this.doc
        .font('Helvetica-Bold')
        .fontSize(12)
        .fillColor(this.colors.chart[i % this.colors.chart.length])
        .text(`${cluster.topic}`, { continued: true })
        .font('Helvetica')
        .fontSize(10)
        .fillColor(this.colors.textSecondary)
        .text(` (${cluster.momentCount} moments)`);

      this.doc
        .font('Helvetica')
        .fontSize(10)
        .fillColor(this.colors.text)
        .text(cluster.summary);

      this.doc
        .fontSize(9)
        .fillColor(this.colors.textSecondary)
        .text(`Keywords: ${cluster.keywords.join(', ')}`);

      this.doc.moveDown(0.5);
    });

    this.doc.moveDown(1);
  }

  /**
   * Render highlights
   */
  private renderHighlights(highlights: Highlight[]): void {
    this.checkPageBreak(150);

    this.renderSectionHeader('Highlights');

    highlights.forEach((highlight, i) => {
      this.checkPageBreak(50);

      const categoryColors: Record<Highlight['category'], string> = {
        achievement: this.colors.success,
        milestone: this.colors.primary,
        insight: this.colors.secondary,
        memorable: this.colors.warning,
        growth: this.colors.chart[5],
      };

      this.doc
        .font('Helvetica-Bold')
        .fontSize(10)
        .fillColor(categoryColors[highlight.category])
        .text(`${highlight.category.toUpperCase()}`);

      this.doc
        .font('Helvetica')
        .fontSize(10)
        .fillColor(this.colors.text)
        .text(`"${highlight.content}${highlight.content.length >= 200 ? '...' : ''}"`, {
          indent: 10,
        });

      this.doc.moveDown(0.5);
    });

    this.doc.moveDown(1);
  }

  /**
   * Render recommendations
   */
  private renderRecommendations(recommendations: GeneratedSummary['recommendations']): void {
    if (!recommendations || recommendations.length === 0) return;

    this.checkPageBreak(150);

    this.renderSectionHeader('Recommendations');

    recommendations.forEach((rec) => {
      this.checkPageBreak(50);

      const priorityColors = {
        high: this.colors.error,
        medium: this.colors.warning,
        low: this.colors.success,
      };

      this.doc
        .font('Helvetica-Bold')
        .fontSize(11)
        .fillColor(this.colors.text)
        .text(rec.title, { continued: true })
        .font('Helvetica')
        .fontSize(8)
        .fillColor(priorityColors[rec.priority])
        .text(` [${rec.priority.toUpperCase()}]`);

      this.doc
        .font('Helvetica')
        .fontSize(10)
        .fillColor(this.colors.textSecondary)
        .text(rec.description, { indent: 10 });

      this.doc.moveDown(0.5);
    });

    this.doc.moveDown(1);
  }

  /**
   * Render key moments
   */
  private renderKeyMoments(moments: GeneratedSummary['keyMoments']): void {
    if (moments.length === 0) return;

    this.checkPageBreak(150);

    this.renderSectionHeader('Key Moments');

    moments.slice(0, 5).forEach((moment, i) => {
      this.checkPageBreak(60);

      this.doc
        .font('Helvetica')
        .fontSize(9)
        .fillColor(this.colors.textSecondary)
        .text(
          moment.capturedAt.toLocaleDateString('en-US', {
            weekday: 'short',
            month: 'short',
            day: 'numeric',
          }) + ` • ${moment.sphereName}`
        );

      this.doc
        .font('Helvetica')
        .fontSize(10)
        .fillColor(this.colors.text)
        .text(
          moment.content.substring(0, 200) + (moment.content.length > 200 ? '...' : ''),
          { indent: 10 }
        );

      if (moment.emotions.length > 0) {
        this.doc
          .fontSize(9)
          .fillColor(this.colors.primary)
          .text(moment.emotions.join(' • '), { indent: 10 });
      }

      this.doc.moveDown(0.5);
    });

    this.doc.moveDown(1);
  }

  /**
   * Render word cloud (simplified as list)
   */
  private renderWordCloud(wordCloud: Record<string, number>): void {
    this.checkPageBreak(100);

    this.renderSectionHeader('Most Used Words');

    const words = Object.entries(wordCloud)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 20);

    const maxCount = words[0]?.[1] || 1;

    // Render as a flowing text with varying sizes
    let line = '';
    words.forEach(([word, count], i) => {
      line += `${word} (${count})  `;
      if ((i + 1) % 5 === 0 || i === words.length - 1) {
        this.doc
          .font('Helvetica')
          .fontSize(10)
          .fillColor(this.colors.textSecondary)
          .text(line.trim());
        line = '';
      }
    });

    this.doc.moveDown(1);
  }

  /**
   * Render footer
   */
  private renderFooter(summary: GeneratedSummary): void {
    // Add page numbers to all pages
    const pages = this.doc.bufferedPageRange();
    for (let i = 0; i < pages.count; i++) {
      this.doc.switchToPage(i);

      this.doc
        .font('Helvetica')
        .fontSize(8)
        .fillColor(this.colors.textSecondary)
        .text(
          `${this.options.footerText || 'Generated by Flashit'} | Page ${i + 1} of ${pages.count}`,
          this.margin,
          this.pageHeight - 30,
          { align: 'center', width: this.contentWidth }
        );
    }
  }

  /**
   * Render section header
   */
  private renderSectionHeader(title: string): void {
    this.doc
      .font('Helvetica-Bold')
      .fontSize(16)
      .fillColor(this.colors.text)
      .text(title);

    // Underline
    this.doc
      .moveTo(this.margin, this.doc.y + 2)
      .lineTo(this.margin + this.contentWidth, this.doc.y + 2)
      .strokeColor(this.colors.border)
      .stroke();

    this.doc.moveDown(0.8);
  }

  /**
   * Check if page break is needed
   */
  private checkPageBreak(requiredSpace: number): void {
    if (this.doc.y + requiredSpace > this.pageHeight - this.margin * 2) {
      this.doc.addPage();
    }
  }

  /**
   * Format period for display
   */
  private formatPeriod(period: string): string {
    const periodMap: Record<string, string> = {
      daily: 'Daily',
      weekly: 'Weekly',
      monthly: 'Monthly',
      yearly: 'Yearly',
      custom: 'Custom',
    };
    return periodMap[period] || period;
  }

  /**
   * Generate filename
   */
  private generateFilename(summary: GeneratedSummary): string {
    const date = summary.startDate.toISOString().split('T')[0];
    return `flashit-${summary.period}-summary-${date}.pdf`;
  }

  /**
   * Static factory method for generating PDF
   */
  static async generate(
    summary: GeneratedSummary,
    options?: PDFOptions
  ): Promise<PDFGenerationResult> {
    const generator = new PDFGeneratorService(options);
    return generator.generateFromSummary(summary);
  }
}

export default PDFGeneratorService;
