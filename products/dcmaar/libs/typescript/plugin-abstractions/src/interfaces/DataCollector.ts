/**
 * DataCollector Plugin Interface
 * Defines the contract for data collection plugins
 */

import { IPlugin } from '@ghatana/dcmaar-types';

export interface IDataCollector extends IPlugin {
  /**
   * Collect data from a specific source
   * @param source - Data source identifier
   * @returns Promise with collected data
   */
  collect(source: string): Promise<Record<string, unknown>>;

  /**
   * Validate if a data source is accessible
   * @param source - Data source identifier
   * @returns Promise with validation result
   */
  validate(source: string): Promise<boolean>;

  /**
   * Get list of available data sources
   * @returns Promise with list of source identifiers
   */
  getSources(): Promise<string[]>;
}
