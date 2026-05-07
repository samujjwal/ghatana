# Report Formulas and Data Sources

## Overview

This document defines the formulas and expected data sources for DMOS reporting: funnel analytics, attribution, ROI/ROAS, and client reporting. These formulas should be implemented before enabling the corresponding UI reporting routes.

## Funnel Analytics

### Funnel Stages

1. **Impressions** - Ad impressions across all channels
2. **Clicks** - Clicks on ads or landing pages
3. **Leads** - Lead captures (forms, signups, etc.)
4. **MQL** - Marketing Qualified Leads
5. **SQL** - Sales Qualified Leads
6. **Opportunities** - Sales opportunities created
7. **Customers** - Closed-won deals

### Funnel Drop-off Formula

**Definition**: Percentage of leads that drop off at each funnel stage.

**Formula**: `(stage_exit_count / stage_entry_count) * 100`

**Data Sources**:
- Stage transition events (lead_lifecycle_events table)
- Campaign lifecycle events (dmos.campaign.* events)
- Ad platform data (impressions, clicks via connector APIs)

**SQL Example**:
```sql
WITH funnel_stages AS (
  SELECT
    campaign_id,
    'impressions' as stage,
    COUNT(DISTINCT impression_id) as count
  FROM ad_impressions
  WHERE event_date BETWEEN :start_date AND :end_date
  GROUP BY campaign_id
  
  UNION ALL
  
  SELECT
    campaign_id,
    'clicks' as stage,
    COUNT(DISTINCT click_id) as count
  FROM ad_clicks
  WHERE event_date BETWEEN :start_date AND :end_date
  GROUP BY campaign_id
  
  UNION ALL
  
  SELECT
    campaign_id,
    'leads' as stage,
    COUNT(DISTINCT lead_id) as count
  FROM leads
  WHERE created_at BETWEEN :start_date AND :end_date
  GROUP BY campaign_id
)
SELECT
  stage,
  campaign_id,
  count,
  LAG(count) OVER (PARTITION BY campaign_id ORDER BY stage_order) as previous_count,
  (count - LAG(count) OVER (PARTITION BY campaign_id ORDER BY stage_order)) / NULLIF(LAG(count) OVER (PARTITION BY campaign_id ORDER BY stage_order), 0) * 100 as drop_off_percent
FROM funnel_stages
ORDER BY campaign_id, stage_order;
```

### Funnel Conversion Rate Formula

**Definition**: Overall conversion rate from top of funnel to bottom.

**Formula**: `(customers / impressions) * 100`

**Data Sources**: Same as funnel drop-off.

**Variants**:
- **Lead to Customer**: `(customers / leads) * 100`
- **MQL to Customer**: `(customers / mqls) * 100`
- **SQL to Customer**: `(customers / sqls) * 100`

---

## Attribution

### Attribution Models

DMOS supports multiple attribution models (see METRICS_TAXONOMY.md for definitions).

### First-Touch Attribution Formula

**Definition**: 100% credit to the first touchpoint in the customer journey.

**Formula**: Assign 100% of conversion value to the first touchpoint in the touchpoint sequence.

**Data Sources**:
- Touchpoint events (touchpoint_events table)
- Conversion events (conversions table)
- Campaign spend data (campaign_spend table)

**SQL Example**:
```sql
WITH first_touch AS (
  SELECT
    conversion_id,
    conversion_value,
    FIRST_VALUE(touchpoint_id) OVER (PARTITION BY conversion_id ORDER BY touchpoint_timestamp) as first_touchpoint_id,
    FIRST_VALUE(campaign_id) OVER (PARTITION BY conversion_id ORDER BY touchpoint_timestamp) as first_campaign_id
  FROM touchpoint_events
  WHERE conversion_id IS NOT NULL
)
SELECT
  first_campaign_id,
  COUNT(DISTINCT conversion_id) as conversions,
  SUM(conversion_value) as attributed_revenue
FROM first_touch
GROUP BY first_campaign_id;
```

### Linear Attribution Formula

**Definition**: Equal credit across all touchpoints.

**Formula**: `credit_per_touchpoint = conversion_value / COUNT(touchpoints)`

**Data Sources**: Same as first-touch.

**SQL Example**:
```sql
WITH touchpoint_counts AS (
  SELECT
    conversion_id,
    conversion_value,
    COUNT(*) as touchpoint_count
  FROM touchpoint_events
  WHERE conversion_id IS NOT NULL
  GROUP BY conversion_id, conversion_value
),
linear_credit AS (
  SELECT
    te.campaign_id,
    tc.conversion_id,
    tc.conversion_value / tc.touchpoint_count as attributed_credit
  FROM touchpoint_events te
  JOIN touchpoint_counts tc ON te.conversion_id = tc.conversion_id
)
SELECT
  campaign_id,
  COUNT(DISTINCT conversion_id) as conversions,
  SUM(attributed_credit) as attributed_revenue
FROM linear_credit
GROUP BY campaign_id;
```

### Time-Decay Attribution Formula

**Definition**: More credit to recent touchpoints (exponential decay).

**Formula**: `credit = conversion_value * (decay_factor ^ days_to_conversion)`

**Data Sources**: Same as first-touch.

**Parameters**:
- `decay_factor`: Configurable (default: 0.5 for half-life of 1 day)

**SQL Example**:
```sql
WITH decay_weights AS (
  SELECT
    touchpoint_id,
    campaign_id,
    conversion_id,
    conversion_value,
    POWER(0.5, DATEDIFF('day', touchpoint_timestamp, conversion_timestamp)) as decay_weight
  FROM touchpoint_events
  WHERE conversion_id IS NOT NULL
),
normalized_weights AS (
  SELECT
    campaign_id,
    conversion_id,
    conversion_value,
    decay_weight,
    decay_weight / SUM(decay_weight) OVER (PARTITION BY conversion_id) as normalized_weight
  FROM decay_weights
)
SELECT
  campaign_id,
  COUNT(DISTINCT conversion_id) as conversions,
  SUM(conversion_value * normalized_weight) as attributed_revenue
FROM normalized_weights
GROUP BY campaign_id;
```

---

## ROI/ROAS

### ROI Formula

**Definition**: Return on investment as percentage.

**Formula**: `((revenue - spend) / spend) * 100`

**Data Sources**:
- Campaign spend data (campaign_spend table)
- Attributed revenue (from attribution calculations)
- Campaign lifecycle events (for time-period filtering)

**SQL Example**:
```sql
SELECT
  campaign_id,
  SUM(spend) as total_spend,
  SUM(attributed_revenue) as total_revenue,
  ((SUM(attributed_revenue) - SUM(spend)) / NULLIF(SUM(spend), 0)) * 100 as roi_percent
FROM campaign_spend cs
LEFT JOIN campaign_revenue cr ON cs.campaign_id = cr.campaign_id
WHERE cs.event_date BETWEEN :start_date AND :end_date
GROUP BY campaign_id;
```

### ROAS Formula

**Definition**: Revenue per dollar of ad spend.

**Formula**: `revenue / spend`

**Data Sources**: Same as ROI.

**SQL Example**:
```sql
SELECT
  campaign_id,
  SUM(spend) as total_spend,
  SUM(attributed_revenue) as total_revenue,
  SUM(attributed_revenue) / NULLIF(SUM(spend), 0) as roas
FROM campaign_spend cs
LEFT JOIN campaign_revenue cr ON cs.campaign_id = cr.campaign_id
WHERE cs.event_date BETWEEN :start_date AND :end_date
GROUP BY campaign_id;
```

### Time-Period ROI/ROAS

**Definition**: ROI/ROAS calculated over specific time periods (daily, weekly, monthly).

**Formula**: Same as ROI/ROAS, grouped by time period.

**Data Sources**: Same as ROI/ROAS with time-period grouping.

**SQL Example**:
```sql
SELECT
  campaign_id,
  DATE_TRUNC('month', event_date) as month,
  SUM(spend) as total_spend,
  SUM(attributed_revenue) as total_revenue,
  ((SUM(attributed_revenue) - SUM(spend)) / NULLIF(SUM(spend), 0)) * 100 as roi_percent,
  SUM(attributed_revenue) / NULLIF(SUM(spend), 0) as roas
FROM campaign_spend cs
LEFT JOIN campaign_revenue cr ON cs.campaign_id = cr.campaign_id
WHERE cs.event_date BETWEEN :start_date AND :end_date
GROUP BY campaign_id, DATE_TRUNC('month', event_date)
ORDER BY campaign_id, month;
```

---

## Client Reporting

### Client-Level Aggregation

**Definition**: Aggregate metrics across all campaigns for a client (workspace).

**Formula**: Sum/average of campaign-level metrics grouped by workspace_id.

**Data Sources**:
- Campaign-level metrics (from above calculations)
- Workspace metadata (workspaces table)
- Client contracts (client_contracts table)

**SQL Example**:
```sql
SELECT
  workspace_id,
  client_name,
  COUNT(DISTINCT campaign_id) as active_campaigns,
  SUM(total_spend) as total_spend,
  SUM(total_revenue) as total_revenue,
  AVG(roi_percent) as avg_roi_percent,
  AVG(roas) as avg_roas,
  SUM(total_leads) as total_leads,
  SUM(total_mqls) as total_mqls,
  SUM(total_sqls) as total_sqls
FROM client_metrics cm
JOIN workspaces w ON cm.workspace_id = w.workspace_id
WHERE cm.event_date BETWEEN :start_date AND :end_date
GROUP BY workspace_id, client_name;
```

### Retainer-Based Reporting

**Definition**: Compare actual spend and performance against retainer commitments.

**Formula**: `(actual_spend / retainer_budget) * 100` (budget utilization)

**Data Sources**:
- Client contracts (retainer_budget, deliverables)
- Actual spend and performance metrics

**SQL Example**:
```sql
SELECT
  workspace_id,
  client_name,
  retainer_budget,
  SUM(total_spend) as actual_spend,
  (SUM(total_spend) / retainer_budget) * 100 as budget_utilization_percent,
  SUM(total_leads) as leads_delivered,
  SUM(total_mqls) as mqls_delivered
FROM client_contracts cc
LEFT JOIN client_metrics cm ON cc.workspace_id = cm.workspace_id
WHERE cc.contract_period BETWEEN :start_date AND :end_date
GROUP BY workspace_id, client_name, retainer_budget;
```

### White-Label Reporting

**Definition**: Generate client-branded reports without DMOS branding.

**Data Sources**: Same as client reporting with client branding configuration.

**Implementation Notes**:
- Client logo, colors, and branding stored in client_configuration table
- Report templates use client-specific branding
- PDF generation with client headers/footers

---

## Data Source Requirements

### Required Tables

| Table | Purpose | Status |
|-------|---------|--------|
| campaigns | Campaign metadata and lifecycle | ✅ Implemented |
| campaign_spend | Campaign spend by date and channel | ⚠️ Pending (connector integration) |
| ad_impressions | Ad impression events | ⚠️ Pending (connector integration) |
| ad_clicks | Ad click events | ⚠️ Pending (connector integration) |
| leads | Lead capture events | ⚠️ Pending (lead capture not implemented) |
| lead_lifecycle_events | Lead stage transitions | ⚠️ Pending (lead scoring not implemented) |
| touchpoint_events | Marketing touchpoints with timestamps | ⚠️ Pending (attribution not implemented) |
| conversions | Conversion events with revenue | ⚠️ Pending (CRM sync not implemented) |
| campaign_revenue | Attributed revenue by campaign | ⚠️ Pending (attribution not implemented) |
| workspaces | Workspace/client metadata | ✅ Implemented |
| client_contracts | Client contracts and retainers | ⚠️ Pending (agency ops not implemented) |
| client_configuration | Client branding configuration | ⚠️ Pending (agency ops not implemented) |

### Required Event Streams

| Event | Purpose | Status |
|-------|---------|--------|
| dmos.campaign.created | Campaign creation | ✅ Implemented |
| dmos.campaign.launched | Campaign launch | ✅ Implemented |
| dmos.campaign.paused | Campaign pause | ✅ Implemented |
| dmos.campaign.completed | Campaign completion | ✅ Implemented |
| dmos.campaign.archived | Campaign archive | ✅ Implemented |
| dmos.campaign.rolledBack | Campaign rollback | ✅ Implemented |
| dmos.lead.captured | Lead capture | ⚠️ Pending |
| dmos.lead.qualified | Lead qualification (MQL) | ⚠️ Pending |
| dmos.conversion.created | Conversion event | ⚠️ Pending |
| dmos.attribution.calculated | Attribution credit assignment | ⚠️ Pending |

---

## Implementation Dependencies

| Report Type | Dependencies |
|-------------|--------------|
| Funnel Analytics | Lead capture, lead lifecycle, ad platform connectors |
| Attribution | Touchpoint tracking, conversion events, attribution models |
| ROI/ROAS | Campaign spend, revenue attribution |
| Client Reporting | All of the above + client contracts, agency ops |

**Key Dependencies**:
- P1-004: Build real funnel analytics, attribution, ROI/ROAS APIs and persistence
- P1-017: Close table-stakes gaps (CRM sync, lead capture, agency ops)

---

## References

- METRICS_TAXONOMY.md: Canonical metric definitions
- P1-004: Build real funnel analytics, attribution, ROI/ROAS APIs and persistence
- P1-017: Close table-stakes gaps before commercial positioning
