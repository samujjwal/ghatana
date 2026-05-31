import { z } from "zod";

/**
 * Dashboard API Request/Response Schemas
 *
 * @doc.type schema
 * @doc.purpose Type-safe validation for dashboard API
 * @doc.layer ui
 * @doc.pattern Schema Validation
 */

/** Dashboard summary schema */
export const DashboardSummarySchema = z.object({
  tenantId: z.string().min(1),
  entityCount: z.number().int().nonnegative(),
  eventCount: z.number().int().nonnegative(),
  queryCount: z.number().int().nonnegative(),
  reportCount: z.number().int().nonnegative(),
  lastUpdated: z.string().datetime(),
  alerts: z.array(
    z.object({
      id: z.string(),
      severity: z.enum(["info", "warning", "error", "critical"]),
      title: z.string(),
      message: z.string(),
      timestamp: z.string().datetime(),
      acknowledged: z.boolean(),
    }),
  ),
});

/** Quick action schema */
export const QuickActionSchema = z.object({
  id: z.string(),
  label: z.string(),
  icon: z.string(),
  route: z.string(),
  badge: z.number().int().nonnegative().optional(),
  enabled: z.boolean(),
});

/** Activity item schema */
export const ActivityItemSchema = z.object({
  id: z.string(),
  type: z.enum([
    "entity_created",
    "entity_updated",
    "query_executed",
    "report_generated",
    "user_action",
  ]),
  title: z.string(),
  description: z.string(),
  userId: z.string(),
  userName: z.string(),
  timestamp: z.string().datetime(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

/** Widget config schema */
export const WidgetConfigSchema = z.object({
  widgetId: z.string(),
  type: z.enum([
    "entity-count",
    "event-timeline",
    "query-stats",
    "recent-activity",
    "system-health",
    "custom",
  ]),
  title: z.string(),
  position: z.object({
    x: z.number().int().nonnegative(),
    y: z.number().int().nonnegative(),
    w: z.number().int().positive(),
    h: z.number().int().positive(),
  }),
  settings: z.record(z.string(), z.unknown()),
  refreshInterval: z.number().int().positive().optional(),
});

/** Widget data schema */
export const WidgetDataSchema = z.object({
  widgetId: z.string(),
  type: z.enum([
    "entity-count",
    "event-timeline",
    "query-stats",
    "recent-activity",
    "system-health",
    "custom",
  ]),
  title: z.string(),
  data: z.unknown(),
  loading: z.boolean(),
  error: z.string().optional(),
  lastUpdated: z.string().datetime(),
});

/** Dashboard update schema */
export const DashboardUpdateSchema = z.object({
  type: z.enum(["alert", "widget_update", "activity"]),
  payload: z.unknown(),
  timestamp: z.string().datetime(),
});

/** Navigation item schema - flat structure for simplicity */
export const NavigationItemSchema = z.object({
  id: z.string(),
  label: z.string(),
  icon: z.string().optional(),
  route: z.string(),
  children: z
    .array(
      z.object({
        id: z.string(),
        label: z.string(),
        icon: z.string().optional(),
        route: z.string(),
      }),
    )
    .optional(),
  requiredPermission: z.string().optional(),
  badge: z.number().int().nonnegative().optional(),
  exact: z.boolean().optional(),
});

/** User role schema */
export const UserRoleSchema = z.enum([
  "admin",
  "editor",
  "viewer",
  "developer",
  "guest",
]);

/** Breadcrumb item schema */
export const BreadcrumbItemSchema = z.object({
  label: z.string(),
  route: z.string().optional(),
  icon: z.string().optional(),
});

/** Type exports */
export type DashboardSummary = z.infer<typeof DashboardSummarySchema>;
export type QuickAction = z.infer<typeof QuickActionSchema>;
export type ActivityItem = z.infer<typeof ActivityItemSchema>;
export type WidgetConfig = z.infer<typeof WidgetConfigSchema>;
export type WidgetData = z.infer<typeof WidgetDataSchema>;
export type DashboardUpdate = z.infer<typeof DashboardUpdateSchema>;
export type NavigationItem = z.infer<typeof NavigationItemSchema>;
export type UserRole = z.infer<typeof UserRoleSchema>;
export type BreadcrumbItem = z.infer<typeof BreadcrumbItemSchema>;
