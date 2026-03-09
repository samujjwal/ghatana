/**
 * Meaning Telemetry Service for Flashit Web API
 * Implements Day 42 metrics: Return-to-Meaning Rate, Annotation Lag, 
 * Cross-Time Referencing, and Language Evolution
 *
 * @doc.type service
 * @doc.purpose Meaning-focused analytics and telemetry
 * @doc.layer product
 * @doc.pattern TelemetryService
 */

import { prisma } from '../../lib/prisma.js';

// Types
export interface MeaningMetrics {
  returnToMeaningRate: ReturnToMeaningData;
  annotationLag: AnnotationLagData;
  crossTimeReferencing: CrossTimeData;
  languageEvolution: LanguageEvolutionData;
  computedAt: string;
}

export interface ReturnToMeaningData {
  rate: number;                           // Percentage of moments revisited
  avgRevisitsPerMoment: number;           // Average revisits per moment
  topRevisitedMoments: Array<{
    momentId: string;
    preview: string;
    revisitCount: number;
    lastRevisitedAt: string;
  }>;
  trend: 'increasing' | 'decreasing' | 'stable';
  periodDays: number;
}

export interface AnnotationLagData {
  avgLagMinutes: number;                  // Average time to first annotation
  medianLagMinutes: number;               // Median time to first annotation
  distribution: {
    immediate: number;                    // < 5 minutes
    sameDay: number;                      // < 24 hours
    withinWeek: number;                   // < 7 days
    later: number;                        // > 7 days
    never: number;                        // No annotation
  };
  trend: 'improving' | 'worsening' | 'stable';
}

export interface CrossTimeData {
  linksAcrossMonths: number;              // Links between moments > 30 days apart
  linksAcrossWeeks: number;               // Links between moments 7-30 days apart
  linksWithinWeek: number;                // Links between moments < 7 days apart
  avgTimespanDays: number;                // Average timespan of linked moments
  temporalArcs: Array<{
    arcId: string;
    startDate: string;
    endDate: string;
    momentCount: number;
    theme?: string;
  }>;
}

export interface LanguageEvolutionData {
  vocabularyGrowth: number;               // New unique words per period
  emotionDiversity: number;               // Variety of emotions expressed
  topicShift: Array<{
    from: string;
    to: string;
    shiftStrength: number;
  }>;
  expressionPatterns: Array<{
    pattern: string;
    frequency: number;
    trend: 'increasing' | 'decreasing' | 'stable';
  }>;
}

/**
 * Meaning Telemetry Service
 * Calculates meaning-focused metrics for dashboards and insights
 */
export class MeaningTelemetryService {
  /**
   * Get all meaning metrics for a user
   */
  static async getMeaningMetrics(
    userId: string,
    options: { periodDays?: number; sphereId?: string } = {}
  ): Promise<MeaningMetrics> {
    const { periodDays = 30, sphereId } = options;

    const [
      returnToMeaningRate,
      annotationLag,
      crossTimeReferencing,
      languageEvolution,
    ] = await Promise.all([
      this.calculateReturnToMeaningRate(userId, periodDays, sphereId),
      this.calculateAnnotationLag(userId, periodDays, sphereId),
      this.analyzeeCrossTimeReferencing(userId, periodDays, sphereId),
      this.analyzeLanguageEvolution(userId, periodDays, sphereId),
    ]);

    return {
      returnToMeaningRate,
      annotationLag,
      crossTimeReferencing,
      languageEvolution,
      computedAt: new Date().toISOString(),
    };
  }

  /**
   * Calculate Return-to-Meaning Rate
   * How often users revisit specific moments
   */
  static async calculateReturnToMeaningRate(
    userId: string,
    periodDays: number,
    sphereId?: string
  ): Promise<ReturnToMeaningData> {
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - periodDays);

    const previousStartDate = new Date(startDate);
    previousStartDate.setDate(previousStartDate.getDate() - periodDays);

    // Get moments and their view counts from audit events
    // Note: Using MOMENT_SEARCHED as proxy for moment views/revisits
    const sphereCondition = sphereId ? { sphereId } : {};
    
    // Count moment views in the period (using update events as proxy)
    const viewEvents = await prisma.auditEvent.findMany({
      where: {
        userId,
        eventType: 'MOMENT_UPDATED',
        createdAt: { gte: startDate },
        ...sphereCondition,
      },
      select: {
        momentId: true,
        createdAt: true,
      },
    });

    // Group views by moment
    const momentViews = new Map<string, { count: number; lastViewed: Date }>();
    for (const event of viewEvents) {
      if (!event.momentId) continue;
      const existing = momentViews.get(event.momentId);
      if (existing) {
        existing.count++;
        if (event.createdAt > existing.lastViewed) {
          existing.lastViewed = event.createdAt;
        }
      } else {
        momentViews.set(event.momentId, { count: 1, lastViewed: event.createdAt });
      }
    }

    // Get total moments in period
    const totalMoments = await prisma.moment.count({
      where: {
        userId,
        deletedAt: null,
        capturedAt: { gte: startDate },
        ...sphereCondition,
      },
    });

    // Calculate rate
    const revisitedMoments = Array.from(momentViews.entries()).filter(
      ([, data]) => data.count > 1
    );
    
    const rate = totalMoments > 0 
      ? (revisitedMoments.length / totalMoments) * 100 
      : 0;

    const totalRevisits = revisitedMoments.reduce((sum, [, data]) => sum + data.count, 0);
    const avgRevisitsPerMoment = revisitedMoments.length > 0 
      ? totalRevisits / revisitedMoments.length 
      : 0;

    // Get top revisited moments
    const topRevisitedIds = revisitedMoments
      .sort((a, b) => b[1].count - a[1].count)
      .slice(0, 5)
      .map(([id]) => id);

    let topRevisitedMoments: ReturnToMeaningData['topRevisitedMoments'] = [];
    if (topRevisitedIds.length > 0) {
      const moments = await prisma.moment.findMany({
        where: { id: { in: topRevisitedIds } },
        select: { id: true, contentText: true },
      });

      topRevisitedMoments = topRevisitedIds.map(id => {
        const moment = moments.find(m => m.id === id);
        const viewData = momentViews.get(id)!;
        return {
          momentId: id,
          preview: moment?.contentText?.substring(0, 100) || 'Unknown',
          revisitCount: viewData.count,
          lastRevisitedAt: viewData.lastViewed.toISOString(),
        };
      });
    }

    // Calculate trend by comparing to previous period (using update events)
    const previousViewEvents = await prisma.auditEvent.count({
      where: {
        userId,
        eventType: 'MOMENT_UPDATED',
        createdAt: { gte: previousStartDate, lt: startDate },
        ...sphereCondition,
      },
    });

    const currentViewCount = viewEvents.length;
    const trend = currentViewCount > previousViewEvents * 1.1 
      ? 'increasing' 
      : currentViewCount < previousViewEvents * 0.9 
        ? 'decreasing' 
        : 'stable';

    return {
      rate: Math.round(rate * 100) / 100,
      avgRevisitsPerMoment: Math.round(avgRevisitsPerMoment * 100) / 100,
      topRevisitedMoments,
      trend,
      periodDays,
    };
  }

  /**
   * Calculate Annotation Lag
   * Time between moment creation and first annotation/reaction
   */
  static async calculateAnnotationLag(
    userId: string,
    periodDays: number,
    sphereId?: string
  ): Promise<AnnotationLagData> {
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - periodDays);

    const sphereCondition = sphereId ? { sphereId } : {};

    // Get moments with their update times (using updatedAt vs capturedAt as proxy for annotation lag)
    const moments = await prisma.moment.findMany({
      where: {
        userId,
        deletedAt: null,
        capturedAt: { gte: startDate },
        ...sphereCondition,
      },
      select: {
        id: true,
        capturedAt: true,
        updatedAt: true,
      },
    });

    const lags: number[] = [];
    let neverAnnotated = 0;

    for (const moment of moments) {
      // Use updatedAt vs capturedAt difference as proxy for annotation lag
      // If they're very close (< 1 minute), consider it as never annotated
      if (moment.capturedAt && moment.updatedAt) {
        const lagMs = moment.updatedAt.getTime() - moment.capturedAt.getTime();
        const lagMinutes = lagMs / (1000 * 60);
        
        if (lagMinutes > 1) {
          lags.push(lagMinutes);
        } else {
          neverAnnotated++;
        }
      } else {
        neverAnnotated++;
      }
    }

    // Calculate statistics
    const sortedLags = lags.sort((a, b) => a - b);
    const avgLagMinutes = lags.length > 0 
      ? lags.reduce((a, b) => a + b, 0) / lags.length 
      : 0;
    const medianLagMinutes = lags.length > 0 
      ? sortedLags[Math.floor(sortedLags.length / 2)] 
      : 0;

    // Distribution
    const distribution = {
      immediate: lags.filter(l => l < 5).length,
      sameDay: lags.filter(l => l >= 5 && l < 24 * 60).length,
      withinWeek: lags.filter(l => l >= 24 * 60 && l < 7 * 24 * 60).length,
      later: lags.filter(l => l >= 7 * 24 * 60).length,
      never: neverAnnotated,
    };

    // Calculate trend (simplified)
    const trend = avgLagMinutes < 60 ? 'improving' : avgLagMinutes > 24 * 60 ? 'worsening' : 'stable';

    return {
      avgLagMinutes: Math.round(avgLagMinutes),
      medianLagMinutes: Math.round(medianLagMinutes),
      distribution,
      trend,
    };
  }

  /**
   * Analyze Cross-Time Referencing
   * Links between moments across different time periods
   */
  static async analyzeeCrossTimeReferencing(
    userId: string,
    periodDays: number,
    sphereId?: string
  ): Promise<CrossTimeData> {
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - periodDays);

    // Get user's accessible spheres
    const userSpheres = await prisma.sphereAccess.findMany({
      where: {
        userId,
        revokedAt: null,
        ...(sphereId ? { sphereId } : {}),
      },
      select: { sphereId: true },
    });
    const sphereIds = userSpheres.map(s => s.sphereId);

    if (sphereIds.length === 0) {
      return {
        linksAcrossMonths: 0,
        linksAcrossWeeks: 0,
        linksWithinWeek: 0,
        avgTimespanDays: 0,
        temporalArcs: [],
      };
    }

    // Get links with moment dates
    const links = await prisma.momentLink.findMany({
      where: {
        createdBy: userId,
        deletedAt: null,
        sourceMoment: { sphereId: { in: sphereIds } },
      },
      include: {
        sourceMoment: { select: { capturedAt: true } },
        targetMoment: { select: { capturedAt: true } },
      },
    });

    let linksAcrossMonths = 0;
    let linksAcrossWeeks = 0;
    let linksWithinWeek = 0;
    const timespans: number[] = [];

    for (const link of links) {
      if (!link.sourceMoment.capturedAt || !link.targetMoment.capturedAt) continue;

      const daysDiff = Math.abs(
        (link.sourceMoment.capturedAt.getTime() - link.targetMoment.capturedAt.getTime()) 
        / (1000 * 60 * 60 * 24)
      );

      timespans.push(daysDiff);

      if (daysDiff >= 30) {
        linksAcrossMonths++;
      } else if (daysDiff >= 7) {
        linksAcrossWeeks++;
      } else {
        linksWithinWeek++;
      }
    }

    const avgTimespanDays = timespans.length > 0 
      ? timespans.reduce((a, b) => a + b, 0) / timespans.length 
      : 0;

    // Identify temporal arcs (clusters of linked moments)
    // Simplified: Group links by link type and find spans
    const arcMap = new Map<string, { dates: Date[]; momentIds: Set<string> }>();
    
    for (const link of links) {
      const arcKey = link.linkType;
      const existing = arcMap.get(arcKey) || { dates: [], momentIds: new Set() };
      
      if (link.sourceMoment.capturedAt) {
        existing.dates.push(link.sourceMoment.capturedAt);
        existing.momentIds.add(link.sourceMomentId);
      }
      if (link.targetMoment.capturedAt) {
        existing.dates.push(link.targetMoment.capturedAt);
        existing.momentIds.add(link.targetMomentId);
      }
      
      arcMap.set(arcKey, existing);
    }

    const temporalArcs = Array.from(arcMap.entries())
      .filter(([, data]) => data.dates.length >= 3)
      .map(([type, data], idx) => {
        const sortedDates = data.dates.sort((a, b) => a.getTime() - b.getTime());
        return {
          arcId: `arc-${idx + 1}`,
          startDate: sortedDates[0].toISOString(),
          endDate: sortedDates[sortedDates.length - 1].toISOString(),
          momentCount: data.momentIds.size,
          theme: type,
        };
      })
      .slice(0, 5);

    return {
      linksAcrossMonths,
      linksAcrossWeeks,
      linksWithinWeek,
      avgTimespanDays: Math.round(avgTimespanDays * 10) / 10,
      temporalArcs,
    };
  }

  /**
   * Analyze Language Evolution
   * Changes in vocabulary and expression over time
   */
  static async analyzeLanguageEvolution(
    userId: string,
    periodDays: number,
    sphereId?: string
  ): Promise<LanguageEvolutionData> {
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - periodDays);

    const previousStartDate = new Date(startDate);
    previousStartDate.setDate(previousStartDate.getDate() - periodDays);

    const sphereCondition = sphereId ? { sphereId } : {};

    // Get moments from current and previous periods
    const [currentMoments, previousMoments] = await Promise.all([
      prisma.moment.findMany({
        where: {
          userId,
          deletedAt: null,
          capturedAt: { gte: startDate },
          ...sphereCondition,
        },
        select: {
          contentText: true,
          emotions: true,
          tags: true,
        },
      }),
      prisma.moment.findMany({
        where: {
          userId,
          deletedAt: null,
          capturedAt: { gte: previousStartDate, lt: startDate },
          ...sphereCondition,
        },
        select: {
          contentText: true,
          emotions: true,
          tags: true,
        },
      }),
    ]);

    // Analyze vocabulary
    const extractWords = (moments: typeof currentMoments) => {
      const words = new Set<string>();
      for (const m of moments) {
        if (m.contentText) {
          const cleanedWords = m.contentText
            .toLowerCase()
            .replace(/[^\w\s]/g, '')
            .split(/\s+/)
            .filter(w => w.length > 3);
          cleanedWords.forEach(w => words.add(w));
        }
      }
      return words;
    };

    const currentWords = extractWords(currentMoments);
    const previousWords = extractWords(previousMoments);
    
    const newWords = new Set([...currentWords].filter(w => !previousWords.has(w)));
    const vocabularyGrowth = newWords.size;

    // Analyze emotion diversity
    const currentEmotions = new Set<string>();
    for (const m of currentMoments) {
      m.emotions?.forEach(e => currentEmotions.add(e));
    }
    const emotionDiversity = currentEmotions.size;

    // Analyze topic shift (using tags as proxy)
    const countTags = (moments: typeof currentMoments) => {
      const counts = new Map<string, number>();
      for (const m of moments) {
        m.tags?.forEach(t => counts.set(t, (counts.get(t) || 0) + 1));
      }
      return counts;
    };

    const currentTagCounts = countTags(currentMoments);
    const previousTagCounts = countTags(previousMoments);

    // Find significant shifts
    const topicShift: LanguageEvolutionData['topicShift'] = [];
    
    // Topics that decreased
    for (const [tag, prevCount] of previousTagCounts.entries()) {
      const currCount = currentTagCounts.get(tag) || 0;
      if (prevCount > 2 && currCount < prevCount * 0.5) {
        // Find what might have replaced it
        for (const [newTag, newCount] of currentTagCounts.entries()) {
          if (!previousTagCounts.has(newTag) && newCount >= prevCount * 0.5) {
            topicShift.push({
              from: tag,
              to: newTag,
              shiftStrength: Math.min((prevCount - currCount) / prevCount, 1),
            });
            break;
          }
        }
      }
    }

    // Expression patterns (word frequency trends)
    const wordFrequency = (moments: typeof currentMoments) => {
      const freq = new Map<string, number>();
      for (const m of moments) {
        if (m.contentText) {
          const words = m.contentText
            .toLowerCase()
            .replace(/[^\w\s]/g, '')
            .split(/\s+/)
            .filter(w => w.length > 4);
          words.forEach(w => freq.set(w, (freq.get(w) || 0) + 1));
        }
      }
      return freq;
    };

    const currentFreq = wordFrequency(currentMoments);
    const previousFreq = wordFrequency(previousMoments);

    const expressionPatterns: LanguageEvolutionData['expressionPatterns'] = [];
    
    // Find words with significant frequency changes
    const allWords = new Set([...currentFreq.keys(), ...previousFreq.keys()]);
    for (const word of allWords) {
      const curr = currentFreq.get(word) || 0;
      const prev = previousFreq.get(word) || 0;
      
      if (curr >= 3 || prev >= 3) {
        if (curr > prev * 1.5) {
          expressionPatterns.push({ pattern: word, frequency: curr, trend: 'increasing' });
        } else if (curr < prev * 0.5) {
          expressionPatterns.push({ pattern: word, frequency: curr, trend: 'decreasing' });
        }
      }
    }

    // Sort by frequency and take top patterns
    expressionPatterns.sort((a, b) => b.frequency - a.frequency);
    expressionPatterns.splice(10);

    return {
      vocabularyGrowth,
      emotionDiversity,
      topicShift: topicShift.slice(0, 5),
      expressionPatterns,
    };
  }

  /**
   * Get dashboard summary for quick overview
   */
  static async getDashboardSummary(
    userId: string,
    sphereId?: string
  ): Promise<{
    meaningScore: number;
    highlights: string[];
    alerts: string[];
    recommendations: string[];
  }> {
    const metrics = await this.getMeaningMetrics(userId, { periodDays: 30, sphereId });

    // Calculate composite meaning score (0-100)
    const returnScore = Math.min(metrics.returnToMeaningRate.rate * 2, 30);
    const annotationScore = metrics.annotationLag.avgLagMinutes < 60 ? 25 : 
                           metrics.annotationLag.avgLagMinutes < 1440 ? 15 : 5;
    const crossTimeScore = Math.min((metrics.crossTimeReferencing.linksAcrossMonths * 5), 25);
    const evolutionScore = Math.min(metrics.languageEvolution.emotionDiversity * 2, 20);
    
    const meaningScore = Math.round(returnScore + annotationScore + crossTimeScore + evolutionScore);

    // Generate highlights
    const highlights: string[] = [];
    if (metrics.returnToMeaningRate.rate > 20) {
      highlights.push(`You revisit ${Math.round(metrics.returnToMeaningRate.rate)}% of your moments`);
    }
    if (metrics.crossTimeReferencing.linksAcrossMonths > 5) {
      highlights.push(`${metrics.crossTimeReferencing.linksAcrossMonths} connections span across months`);
    }
    if (metrics.languageEvolution.vocabularyGrowth > 50) {
      highlights.push(`Your vocabulary grew by ${metrics.languageEvolution.vocabularyGrowth} words`);
    }

    // Generate alerts
    const alerts: string[] = [];
    if (metrics.returnToMeaningRate.rate < 5) {
      alerts.push('Low revisit rate - try reviewing past moments');
    }
    if (metrics.annotationLag.distribution.never > metrics.annotationLag.distribution.immediate * 2) {
      alerts.push('Many moments lack annotations');
    }

    // Generate recommendations
    const recommendations: string[] = [];
    if (metrics.crossTimeReferencing.linksWithinWeek > metrics.crossTimeReferencing.linksAcrossMonths * 3) {
      recommendations.push('Try linking moments from different time periods');
    }
    if (metrics.languageEvolution.emotionDiversity < 3) {
      recommendations.push('Explore expressing a wider range of emotions');
    }

    return {
      meaningScore,
      highlights,
      alerts,
      recommendations,
    };
  }
}
