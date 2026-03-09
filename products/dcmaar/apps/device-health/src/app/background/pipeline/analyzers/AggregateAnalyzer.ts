/**
 * @fileoverview Aggregate Analyzer
 *
 * Aggregates events by various dimensions (count, sum, avg, etc.)
 *
 * @module pipeline/analyzers
 * @since 2.0.0
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import type { ProcessAnalyzer } from '../ProcessManager';
import type { ProcessExecutionContext, AnalysisOperation } from '../../contracts/process';

/**
 * Aggregate analyzer
 */
export class AggregateAnalyzer implements ProcessAnalyzer {
  /**
   * Analyzes data by aggregating
   */
  async analyze(
    data: Event[],
    operation: AnalysisOperation,
    context: ProcessExecutionContext
  ): Promise<any> {
    const { config } = operation;
    const {
      groupBy = 'type',
      metrics = ['count'],
      fields = [],
    } = config;

    try {
      const groups = this.groupEvents(data, groupBy);
      const aggregated: any = {};

      for (const [key, events] of Object.entries(groups)) {
        aggregated[key] = {};

        for (const metric of metrics) {
          switch (metric) {
            case 'count':
              aggregated[key].count = events.length;
              break;
            case 'sum':
              for (const field of fields) {
                const sum = this.sumField(events, field);
                aggregated[key][`${field}_sum`] = sum;
              }
              break;
            case 'avg':
              for (const field of fields) {
                const avg = this.avgField(events, field);
                aggregated[key][`${field}_avg`] = avg;
              }
              break;
            case 'min':
              for (const field of fields) {
                const min = this.minField(events, field);
                aggregated[key][`${field}_min`] = min;
              }
              break;
            case 'max':
              for (const field of fields) {
                const max = this.maxField(events, field);
                aggregated[key][`${field}_max`] = max;
              }
              break;
          }
        }
      }

      context.logger.debug('Aggregation completed', {
        groupCount: Object.keys(aggregated).length,
      });

      return aggregated;
    } catch (error) {
      context.logger.error('Aggregation failed', { error });
      throw error;
    }
  }

  /**
   * Groups events by field
   */
  private groupEvents(events: Event[], groupBy: string): Record<string, Event[]> {
    const groups: Record<string, Event[]> = {};

    for (const event of events) {
      const key = this.getFieldValue(event, groupBy) || 'unknown';
      if (!groups[key]) {
        groups[key] = [];
      }
      groups[key].push(event);
    }

    return groups;
  }

  /**
   * Sums a field across events
   */
  private sumField(events: Event[], field: string): number {
    return events.reduce((sum, event) => {
      const value = this.getFieldValue(event, field);
      return sum + (typeof value === 'number' ? value : 0);
    }, 0);
  }

  /**
   * Averages a field across events
   */
  private avgField(events: Event[], field: string): number {
    if (events.length === 0) return 0;
    return this.sumField(events, field) / events.length;
  }

  /**
   * Finds minimum value of a field
   */
  private minField(events: Event[], field: string): number {
    const values = events
      .map((e) => this.getFieldValue(e, field))
      .filter((v) => typeof v === 'number') as number[];
    return values.length > 0 ? Math.min(...values) : 0;
  }

  /**
   * Finds maximum value of a field
   */
  private maxField(events: Event[], field: string): number {
    const values = events
      .map((e) => this.getFieldValue(e, field))
      .filter((v) => typeof v === 'number') as number[];
    return values.length > 0 ? Math.max(...values) : 0;
  }

  /**
   * Gets nested field value
   */
  private getFieldValue(event: Event, field: string): any {
    const parts = field.split('.');
    let value: any = event;

    for (const part of parts) {
      value = value?.[part];
    }

    return value;
  }
}
