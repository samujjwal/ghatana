/**
 * @fileoverview Output Formatters Entry Point
 * @module @ghatana/accessibility-audit/formatters
 */

export {
  JSONFormatter,
  SARIFFormatter,
  CSVFormatter,
  XMLFormatter,
  HTMLFormatter,
  MarkdownFormatter,
  OutputFormatterFactory,
  formatterFactory,
} from './OutputFormatter';

export type { OutputFormatterContract as IOutputFormatter } from './OutputFormatter';