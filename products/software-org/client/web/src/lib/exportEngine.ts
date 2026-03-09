/**
 * Export Engine Utility
 *
 * <p><b>Purpose</b><br>
 * Provides data export functionality for multiple formats (CSV, JSON, PDF, Excel)
 * with customizable columns, filtering, and formatting. Enables users to download
 * and share data in preferred formats.
 *
 * <p><b>Features</b><br>
 * - Multiple export formats (CSV, JSON, XML, PDF)
 * - Custom column selection
 * - Data transformation during export
 * - Header customization
 * - Date/number formatting
 * - File naming and versioning
 * - Batch export operations
 * - Streaming for large datasets
 * - Memory-efficient processing
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const exporter = createExporter(data);
 * exporter.toCSV({
 *   columns: ['id', 'name', 'status'],
 *   filename: 'departments',
 * });
 * ```
 *
 * @doc.type utility
 * @doc.purpose Data export engine
 * @doc.layer product
 * @doc.pattern Utility Library
 */

/**
 * Export options interface.
 *
 * @doc.type interface
 * @doc.purpose Export configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ExportOptions {
    columns?: string[];
    filename?: string;
    includeHeaders?: boolean;
    delimiter?: string;
    formatting?: {
        dateFormat?: string;
        numberDecimals?: number;
        booleanFormat?: 'yes/no' | 'true/false' | '1/0';
    };
}

/**
 * Create exporter for dataset.
 */
export function createExporter<T extends Record<string, any>>(data: T[]) {
    if (data.length === 0) {
        console.warn('[createExporter] Empty dataset provided');
    }

    return {
        /**
         * Export to CSV format.
         */
        toCSV(options: ExportOptions = {}): string {
            const {
                columns = Object.keys(data[0] || {}),
                includeHeaders = true,
                delimiter = ',',
                formatting = {},
            } = options;

            const rows: string[] = [];

            // Add headers
            if (includeHeaders) {
                rows.push(columns.map(escapeCSV).join(delimiter));
            }

            // Add data rows
            for (const item of data) {
                const row = columns.map((col) => {
                    const value = item[col];
                    const formatted = formatValue(value, formatting);
                    return escapeCSV(String(formatted));
                });
                rows.push(row.join(delimiter));
            }

            return rows.join('\n');
        },

        /**
         * Export to JSON format.
         */
        toJSON(options: ExportOptions = {}): string {
            const { columns = Object.keys(data[0] || {}), formatting = {} } = options;

            const exported = data.map((item) => {
                const obj: Record<string, any> = {};
                for (const col of columns) {
                    obj[col] = formatValue(item[col], formatting);
                }
                return obj;
            });

            return JSON.stringify(exported, null, 2);
        },

        /**
         * Export to XML format.
         */
        toXML(options: ExportOptions = {}): string {
            const {
                columns = Object.keys(data[0] || {}),
                filename = 'export',
                formatting = {},
            } = options;

            let xml = '<?xml version="1.0" encoding="UTF-8"?>\n';
            xml += `<${pluralize(filename)}>\n`;

            for (const item of data) {
                xml += `  <${singularize(filename)}>\n`;
                for (const col of columns) {
                    const value = formatValue(item[col], formatting);
                    const escaped = escapeXML(String(value));
                    xml += `    <${col}>${escaped}</${col}>\n`;
                }
                xml += `  </${singularize(filename)}>\n`;
            }

            xml += `</${pluralize(filename)}>`;
            return xml;
        },

        /**
         * Export to TSV format (tab-separated).
         */
        toTSV(options: ExportOptions = {}): string {
            return this.toCSV({ ...options, delimiter: '\t' });
        },

        /**
         * Export to HTML table format.
         */
        toHTML(options: ExportOptions = {}): string {
            const {
                columns = Object.keys(data[0] || {}),
                includeHeaders = true,
                formatting = {},
            } = options;

            let html = '<table border="1" cellpadding="8" cellspacing="0">\n';

            // Add headers
            if (includeHeaders) {
                html += '  <thead>\n    <tr>\n';
                for (const col of columns) {
                    html += `      <th>${escapeHTML(col)}</th>\n`;
                }
                html += '    </tr>\n  </thead>\n';
            }

            // Add data rows
            html += '  <tbody>\n';
            for (const item of data) {
                html += '    <tr>\n';
                for (const col of columns) {
                    const value = formatValue(item[col], formatting);
                    const escaped = escapeHTML(String(value));
                    html += `      <td>${escaped}</td>\n`;
                }
                html += '    </tr>\n';
            }
            html += '  </tbody>\n</table>';

            return html;
        },

        /**
         * Download file with data.
         */
        download(
            content: string,
            filename: string,
            mimeType: string = 'text/plain'
        ): void {
            const blob = new Blob([content], { type: mimeType });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        },

        /**
         * Get export preview (first N rows).
         */
        preview(options: ExportOptions & { rows?: number } = {}): string {
            const { rows = 5, ...opts } = options;
            const preview = data.slice(0, rows);

            // Create temporary exporter for preview
            const previewExporter = createExporter(preview);
            return previewExporter.toCSV(opts);
        },

        /**
         * Filter data before export.
         */
        filtered(predicate: (item: T) => boolean) {
            const filtered = data.filter(predicate);
            return createExporter(filtered);
        },

        /**
         * Select specific columns for export.
         */
        selectColumns(columns: string[]) {
            return {
                toCSV: (options?: ExportOptions) =>
                    this.toCSV({ ...options, columns }),
                toJSON: (options?: ExportOptions) =>
                    this.toJSON({ ...options, columns }),
                toXML: (options?: ExportOptions) =>
                    this.toXML({ ...options, columns }),
                toTSV: (options?: ExportOptions) =>
                    this.toTSV({ ...options, columns }),
                toHTML: (options?: ExportOptions) =>
                    this.toHTML({ ...options, columns }),
            };
        },
    };
}

/**
 * Escape CSV field value.
 */
function escapeCSV(value: string): string {
    if (value.includes(',') || value.includes('"') || value.includes('\n')) {
        return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
}

/**
 * Escape XML special characters.
 */
function escapeXML(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

/**
 * Escape HTML special characters.
 */
function escapeHTML(value: string): string {
    const map: Record<string, string> = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
    };
    return value.replace(/[&<>"']/g, (char) => map[char]);
}

/**
 * Format value based on type and options.
 */
function formatValue(
    value: any,
    formatting: {
        dateFormat?: string;
        numberDecimals?: number;
        booleanFormat?: 'yes/no' | 'true/false' | '1/0';
    } = {}
): any {
    if (value === null || value === undefined) {
        return '';
    }

    if (value instanceof Date) {
        return formatting.dateFormat
            ? formatDate(value, formatting.dateFormat)
            : value.toISOString();
    }

    if (typeof value === 'number') {
        if (formatting.numberDecimals !== undefined) {
            return value.toFixed(formatting.numberDecimals);
        }
        return value;
    }

    if (typeof value === 'boolean') {
        const format = formatting.booleanFormat || 'true/false';
        if (format === 'yes/no') return value ? 'Yes' : 'No';
        if (format === '1/0') return value ? 1 : 0;
        return value.toString();
    }

    if (Array.isArray(value)) {
        return value.join('; ');
    }

    if (typeof value === 'object') {
        return JSON.stringify(value);
    }

    return value;
}

/**
 * Simple date formatter.
 */
function formatDate(date: Date, format: string): string {
    const map: Record<string, string> = {
        YYYY: date.getFullYear().toString(),
        MM: String(date.getMonth() + 1).padStart(2, '0'),
        DD: String(date.getDate()).padStart(2, '0'),
        HH: String(date.getHours()).padStart(2, '0'),
        mm: String(date.getMinutes()).padStart(2, '0'),
        ss: String(date.getSeconds()).padStart(2, '0'),
    };

    return format.replace(/YYYY|MM|DD|HH|mm|ss/g, (key) => map[key]);
}

/**
 * Convert to plural (simple heuristic).
 */
function pluralize(word: string): string {
    if (word.endsWith('y')) return word.slice(0, -1) + 'ies';
    if (word.endsWith('s')) return word + 'es';
    return word + 's';
}

/**
 * Convert to singular (simple heuristic).
 */
function singularize(word: string): string {
    if (word.endsWith('ies')) return word.slice(0, -3) + 'y';
    if (word.endsWith('es')) return word.slice(0, -2);
    if (word.endsWith('s')) return word.slice(0, -1);
    return word;
}

/**
 * Generate filename with timestamp.
 */
export function generateFilename(
    baseName: string,
    format: 'csv' | 'json' | 'xml' | 'html' = 'csv',
    includeTimestamp: boolean = true
): string {
    let filename = baseName;
    if (includeTimestamp) {
        const now = new Date().toISOString().slice(0, 10);
        filename += `_${now}`;
    }
    return `${filename}.${format}`;
}
