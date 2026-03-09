import { query } from "../db";

export interface UsageReport {
  child_id: string;
  child_name: string;
  total_screen_time: number;
  session_count: number;
  top_apps: Array<{ app_name: string; duration: number }>;
  by_category: Record<string, number>;
}

export interface BlockReport {
  child_id: string;
  child_name: string;
  total_blocks: number;
  top_blocked: Array<{ target: string; count: number; category: string }>;
  by_category: Record<string, number>;
}

export interface DailySummary {
  date: string;
  total_screen_time: number;
  total_blocks: number;
  children: Array<{
    child_id: string;
    child_name: string;
    screen_time: number;
    blocks: number;
  }>;
}

/**
 * Get usage report for a date range.
 *
 * <p><b>Purpose</b><br>
 * Generates screen time report showing total duration, session count, top apps,
 * and usage by category for each child in the specified time period.
 *
 * <p><b>Metrics Calculated</b><br>
 * - total_screen_time: Sum of all usage session durations (in seconds)
 * - session_count: Count of distinct usage sessions
 * - top_apps: Top 10 apps by duration (app_name, duration)
 * - by_category: Duration grouped by category (social_media, games, productivity, etc.)
 *
 * <p><b>Process</b><br>
 * - Query children with LEFT JOIN to devices and usage_sessions
 * - For each child, fetch top 10 apps by duration
 * - For each child, fetch category breakdown
 * - Sort children by total_screen_time DESC (highest usage first)
 *
 * <p><b>Performance</b><br>
 * Uses Promise.all() to fetch top_apps and by_category concurrently for each child.
 * May be slow for large date ranges with many children.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get weekly usage report for all children
 * const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
 * const reports = await getUsageReport(userId, weekAgo, new Date());
 * for (const report of reports) {
 *   console.log(`${report.child_name}: ${(report.total_screen_time / 3600).toFixed(1)} hours`);
 * }
 * }</pre>
 *
 * @param userId User whose children to report on
 * @param startDate Start of time period
 * @param endDate End of time period
 * @param childId Optional filter for specific child
 * @return Array of usage reports per child
 * @throws Error if database query fails
 * @see getBlockReport
 * @see getDailySummary
 * @doc.type function
 * @doc.purpose Generate screen time usage report for date range
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getUsageReport(
  userId: string,
  startDate: Date,
  endDate: Date,
  childId?: string
): Promise<UsageReport[]> {
  let query_text = `
    SELECT 
      c.id as child_id,
      c.name as child_name,
      COALESCE(SUM(us.duration_seconds), 0) as total_screen_time,
      COUNT(DISTINCT us.id) as session_count
    FROM children c
    LEFT JOIN devices d ON c.id = d.child_id
    LEFT JOIN usage_sessions us ON d.id = us.device_id 
      AND us.start_time >= $2 
      AND us.start_time <= $3
    WHERE c.user_id = $1
  `;

  const params: unknown[] = [userId, startDate, endDate];

  if (childId) {
    query_text += " AND c.id = $4";
    params.push(childId);
  }

  query_text += " GROUP BY c.id, c.name ORDER BY total_screen_time DESC";

  const results = await query<{
    child_id: string;
    child_name: string;
    total_screen_time: string;
    session_count: string;
  }>(query_text, params);

  // Get top apps and categories for each child
  const reports: UsageReport[] = [];
  for (const row of results) {
    const [topApps, byCategory] = await Promise.all([
      query<{ item_name: string; duration: string }>(
        `SELECT us.item_name, SUM(us.duration_seconds) as duration
         FROM usage_sessions us
         JOIN devices d ON us.device_id = d.id
         WHERE d.child_id = $1 AND us.start_time >= $2 AND us.start_time <= $3
         GROUP BY us.item_name
         ORDER BY duration DESC
         LIMIT 10`,
        [row.child_id, startDate, endDate]
      ),
      query<{ category: string; duration: string }>(
        `SELECT us.category, SUM(us.duration_seconds) as duration
         FROM usage_sessions us
         JOIN devices d ON us.device_id = d.id
         WHERE d.child_id = $1 AND us.start_time >= $2 AND us.start_time <= $3
         GROUP BY us.category
         ORDER BY duration DESC`,
        [row.child_id, startDate, endDate]
      ),
    ]);

    const by_category: Record<string, number> = {};
    byCategory.forEach((cat) => {
      by_category[cat.category || "uncategorized"] = parseInt(cat.duration);
    });

    reports.push({
      child_id: row.child_id,
      child_name: row.child_name,
      total_screen_time: parseInt(row.total_screen_time),
      session_count: parseInt(row.session_count),
      top_apps: topApps.map((app) => ({
        app_name: app.item_name,
        duration: parseInt(app.duration),
      })),
      by_category,
    });
  }

  return reports;
}

/**
 * Get block events report for a date range.
 *
 * <p><b>Purpose</b><br>
 * Generates blocking report showing total blocks, top blocked items,
 * and blocks by category for each child in the specified time period.
 *
 * <p><b>Metrics Calculated</b><br>
 * - total_blocks: Count of all block events (websites, apps, etc.)
 * - top_blocked: Top 10 blocked items by count (target, count, category)
 * - by_category: Block count grouped by category (social_media, gaming, adult_content, etc.)
 *
 * <p><b>Process</b><br>
 * - Query children with LEFT JOIN to devices and block_events
 * - For each child, fetch top 10 blocked items by count
 * - For each child, fetch category breakdown
 * - Sort children by total_blocks DESC (most blocks first)
 *
 * <p><b>Performance</b><br>
 * Uses Promise.all() to fetch top_blocked and by_category concurrently for each child.
 * May be slow for large date ranges with many children.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get monthly block report for all children
 * const monthAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
 * const reports = await getBlockReport(userId, monthAgo, new Date());
 * for (const report of reports) {
 *   console.log(`${report.child_name}: ${report.total_blocks} blocks`);
 *   console.log(`Top blocked: ${report.top_blocked[0]?.target}`);
 * }
 * }</pre>
 *
 * @param userId User whose children to report on
 * @param startDate Start of time period
 * @param endDate End of time period
 * @param childId Optional filter for specific child
 * @return Array of block reports per child
 * @throws Error if database query fails
 * @see getUsageReport
 * @see getDailySummary
 * @doc.type function
 * @doc.purpose Generate block events report for date range
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getBlockReport(
  userId: string,
  startDate: Date,
  endDate: Date,
  childId?: string
): Promise<BlockReport[]> {
  let query_text = `
    SELECT 
      c.id as child_id,
      c.name as child_name,
      COUNT(be.id) as total_blocks
    FROM children c
    LEFT JOIN devices d ON c.id = d.child_id
    LEFT JOIN block_events be ON d.id = be.device_id 
      AND be.timestamp >= $2 
      AND be.timestamp <= $3
    WHERE c.user_id = $1
  `;

  const params: unknown[] = [userId, startDate, endDate];

  if (childId) {
    query_text += " AND c.id = $4";
    params.push(childId);
  }

  query_text += " GROUP BY c.id, c.name ORDER BY total_blocks DESC";

  const results = await query<{
    child_id: string;
    child_name: string;
    total_blocks: string;
  }>(query_text, params);

  // Get top blocked targets and categories for each child
  const reports: BlockReport[] = [];
  for (const row of results) {
    const [topBlocked, byCategory] = await Promise.all([
      query<{ blocked_item: string; category: string; count: string }>(
        `SELECT be.blocked_item, be.category, COUNT(*) as count
         FROM block_events be
         JOIN devices d ON be.device_id = d.id
         WHERE d.child_id = $1 AND be.timestamp >= $2 AND be.timestamp <= $3
         GROUP BY be.blocked_item, be.category
         ORDER BY count DESC
         LIMIT 10`,
        [row.child_id, startDate, endDate]
      ),
      query<{ category: string; count: string }>(
        `SELECT be.category, COUNT(*) as count
         FROM block_events be
         JOIN devices d ON be.device_id = d.id
         WHERE d.child_id = $1 AND be.timestamp >= $2 AND be.timestamp <= $3
         GROUP BY be.category
         ORDER BY count DESC`,
        [row.child_id, startDate, endDate]
      ),
    ]);

    const by_category: Record<string, number> = {};
    byCategory.forEach((cat) => {
      by_category[cat.category || "uncategorized"] = parseInt(cat.count);
    });

    reports.push({
      child_id: row.child_id,
      child_name: row.child_name,
      total_blocks: parseInt(row.total_blocks),
      top_blocked: topBlocked.map((item) => ({
        target: item.blocked_item,
        count: parseInt(item.count),
        category: item.category || "uncategorized",
      })),
      by_category,
    });
  }

  return reports;
}

/**
 * Get daily summary for the last N days.
 *
 * <p><b>Purpose</b><br>
 * Generates daily aggregated metrics for dashboard trend charts.
 * Shows screen time and blocks per day for all children.
 *
 * <p><b>Metrics Per Day</b><br>
 * - date: Date in ISO format (YYYY-MM-DD)
 * - total_screen_time: Sum of all children's screen time (in seconds)
 * - total_blocks: Sum of all children's block events
 * - children: Array of per-child metrics (child_id, child_name, screen_time, blocks)
 *
 * <p><b>Process</b><br>
 * - For each of the last N days:
 *   - Calculate start of day (00:00:00) and end of day (23:59:59)
 *   - Query all children with LEFT JOIN to usage_sessions and block_events
 *   - Aggregate totals across all children
 * - Return results in chronological order (oldest first)
 *
 * <p><b>Timezone Handling</b><br>
 * Uses JavaScript Date manipulation. Consider timezone if showing to users in different zones.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get last 7 days for dashboard chart
 * const summaries = await getDailySummary(userId, 7);
 * const labels = summaries.map(s => s.date);
 * const screenTimeData = summaries.map(s => s.total_screen_time / 3600); // hours
 * const blockData = summaries.map(s => s.total_blocks);
 * }</pre>
 *
 * @param userId User whose children to report on
 * @param days Number of days to include (default: 7)
 * @return Array of daily summaries in chronological order
 * @throws Error if database query fails
 * @see getUsageReport
 * @see getBlockReport
 * @doc.type function
 * @doc.purpose Generate daily aggregated metrics for trend charts
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getDailySummary(
  userId: string,
  days: number = 7
): Promise<DailySummary[]> {
  const summaries: DailySummary[] = [];
  const today = new Date();
  today.setHours(23, 59, 59, 999);

  for (let i = 0; i < days; i++) {
    const date = new Date(today);
    date.setDate(date.getDate() - i);
    const startOfDay = new Date(date);
    startOfDay.setHours(0, 0, 0, 0);
    const endOfDay = new Date(date);
    endOfDay.setHours(23, 59, 59, 999);

    // Get screen time and blocks for all children
    const childrenData = await query<{
      child_id: string;
      child_name: string;
      screen_time: string;
      blocks: string;
    }>(
      `SELECT 
        c.id as child_id,
        c.name as child_name,
        COALESCE(SUM(us.duration_seconds), 0) as screen_time,
        COALESCE(COUNT(DISTINCT be.id), 0) as blocks
      FROM children c
      LEFT JOIN devices d ON c.id = d.child_id
      LEFT JOIN usage_sessions us ON d.id = us.device_id 
        AND us.start_time >= $2 AND us.start_time <= $3
      LEFT JOIN block_events be ON d.id = be.device_id 
        AND be.timestamp >= $2 AND be.timestamp <= $3
      WHERE c.user_id = $1
      GROUP BY c.id, c.name
      ORDER BY c.name`,
      [userId, startOfDay, endOfDay]
    );

    const children = childrenData.map((child) => ({
      child_id: child.child_id,
      child_name: child.child_name,
      screen_time: parseInt(child.screen_time),
      blocks: parseInt(child.blocks),
    }));

    const total_screen_time = children.reduce(
      (sum, c) => sum + c.screen_time,
      0
    );
    const total_blocks = children.reduce((sum, c) => sum + c.blocks, 0);

    summaries.push({
      date: startOfDay.toISOString().split("T")[0],
      total_screen_time,
      total_blocks,
      children,
    });
  }

  return summaries.reverse(); // Return chronologically
}

/**
 * Export report data as CSV.
 *
 * <p><b>Purpose</b><br>
 * Converts usage or block report data to CSV format for download.
 * Useful for importing into Excel, Google Sheets, or other analysis tools.
 *
 * <p><b>CSV Format - Usage</b><br>
 * Headers: Child Name, Total Screen Time (min), Sessions, Top Apps
 * - Screen time converted to minutes (rounded)
 * - Top 3 apps joined with semicolon separator
 *
 * <p><b>CSV Format - Blocks</b><br>
 * Headers: Child Name, Total Blocks, Top Blocked Sites
 * - Top 3 blocked items joined with semicolon separator
 *
 * <p><b>Implementation</b><br>
 * Simple CSV generation without escaping.
 * May break if child names or app names contain commas or newlines.
 * Consider using proper CSV library (e.g., papaparse) for production.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const reports = await getUsageReport(userId, startDate, endDate);
 * const csv = exportToCSV(reports, 'usage');
 * res.setHeader('Content-Type', 'text/csv');
 * res.setHeader('Content-Disposition', 'attachment; filename=usage-report.csv');
 * res.send(csv);
 * }</pre>
 *
 * @param data Usage or block report data
 * @param type Report type: 'usage' or 'blocks'
 * @return CSV string
 * @see getUsageReport
 * @see getBlockReport
 * @see exportToPDF
 * @doc.type function
 * @doc.purpose Convert report data to CSV format
 * @doc.layer product
 * @doc.pattern Service
 */
export function exportToCSV(data: unknown[], type: "usage" | "blocks"): string {
  if (data.length === 0) return "";

  if (type === "usage") {
    const headers = [
      "Child Name",
      "Total Screen Time (min)",
      "Sessions",
      "Top Apps",
    ];
    const rows = (data as UsageReport[]).map((row) => [
      row.child_name,
      Math.round(row.total_screen_time / 60),
      row.session_count,
      row.top_apps
        .slice(0, 3)
        .map((app) => app.app_name)
        .join("; "),
    ]);

    return [headers, ...rows].map((row) => row.join(",")).join("\n");
  } else {
    const headers = ["Child Name", "Total Blocks", "Top Blocked Sites"];
    const rows = (data as BlockReport[]).map((row) => [
      row.child_name,
      row.total_blocks,
      row.top_blocked
        .slice(0, 3)
        .map((item) => item.target)
        .join("; "),
    ]);

    return [headers, ...rows].map((row) => row.join(",")).join("\n");
  }
}

/**
 * Export report data as a lightweight PDF-like buffer.
 *
 * <p><b>Purpose</b><br>
 * Placeholder implementation that serializes report data to JSON until
 * dedicated PDF library (e.g., pdfkit, puppeteer) is integrated.
 *
 * <p><b>Current Implementation</b><br>
 * - Serializes data to pretty-printed JSON
 * - Adds metadata: generatedAt timestamp, type, recordCount
 * - Returns UTF-8 Buffer for response
 *
 * <p><b>Future Enhancement</b><br>
 * Replace with actual PDF generation:
 * - Use pdfkit or puppeteer to create formatted PDF
 * - Include charts, tables, and branding
 * - Support multi-page reports with headers/footers
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const reports = await getUsageReport(userId, startDate, endDate);
 * const pdfBuffer = exportToPDF(reports, 'usage');
 * res.setHeader('Content-Type', 'application/pdf'); // TODO: Change to application/pdf
 * res.setHeader('Content-Disposition', 'attachment; filename=usage-report.pdf');
 * res.send(pdfBuffer);
 * }</pre>
 *
 * @param data Usage or block report data
 * @param type Report type: 'usage' or 'blocks'
 * @return Buffer containing JSON serialized data (placeholder)
 * @see getUsageReport
 * @see getBlockReport
 * @see exportToCSV
 * @doc.type function
 * @doc.purpose Export report as PDF (placeholder JSON implementation)
 * @doc.layer product
 * @doc.pattern Service
 */
export function exportToPDF(
  data: UsageReport[] | BlockReport[],
  type: "usage" | "blocks"
): Buffer {
  const timestamp = new Date().toISOString();
  const payload = {
    generatedAt: timestamp,
    type,
    recordCount: data.length,
    content: data,
  };

  return Buffer.from(JSON.stringify(payload, null, 2), "utf-8");
}
