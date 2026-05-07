# DMOS Metrics Taxonomy

## Overview

This document defines the canonical metrics taxonomy for Digital Marketing Operating System (DMOS). These metrics provide a consistent language for reporting, analytics, and performance measurement across campaigns, channels, and time periods.

## Funnel Metrics

### Leads

**Definition**: Total number of lead captures across all channels.

**Data Sources**: Landing page forms, intake questionnaires, lead magnets, partner referrals.

**Formula**: `COUNT(DISTINCT lead_id)` where `lead_status IN ('new', 'contacted', 'qualified')`

**Granularity**: Daily, weekly, monthly by campaign, channel, workspace.

---

### MQL (Marketing Qualified Lead)

**Definition**: Leads that meet marketing qualification criteria (e.g., industry fit, company size, intent signals).

**Data Sources**: Lead scoring models, form data, enrichment providers.

**Formula**: `COUNT(DISTINCT lead_id)` where `lead_score >= MQL_THRESHOLD` AND `lead_status = 'qualified'`

**Granularity**: Daily, weekly, monthly by campaign, channel, workspace.

**Notes**: MQL threshold configurable per workspace (default: 70/100).

---

### SQL (Sales Qualified Lead)

**Definition**: Leads accepted by sales as qualified for outreach (e.g., scheduled demo, requested proposal).

**Data Sources**: CRM sync, opportunity records, sales acceptance workflow.

**Formula**: `COUNT(DISTINCT lead_id)` where `sales_status = 'accepted'` AND `opportunity_stage IN ('demo_scheduled', 'proposal_requested')`

**Granularity**: Daily, weekly, monthly by campaign, channel, workspace.

**Notes**: Requires CRM integration (P1-017 table-stakes gap).

---

### Conversion Rate

**Definition**: Percentage of leads that convert to the next funnel stage.

**Data Sources**: Lead lifecycle events, stage transitions.

**Formula**: `(leads_converted / leads_entered) * 100`

**Variants**:
- **Lead to MQL**: `MQLs / Leads * 100`
- **MQL to SQL**: `SQLs / MQLs * 100`
- **SQL to Opportunity**: `Opportunities / SQLs * 100`
- **Opportunity to Customer**: `Customers / Opportunities * 100`

**Granularity**: Daily, weekly, monthly by campaign, channel, workspace.

---

## Acquisition Metrics

### CPL (Cost Per Lead)

**Definition**: Average cost to acquire one lead.

**Data Sources**: Campaign spend data, lead capture events.

**Formula**: `total_campaign_spend / total_leads`

**Granularity**: Campaign-level, channel-level, workspace-level.

**Notes**: Excludes organic leads (spend = 0).

---

### CAC (Customer Acquisition Cost)

**Definition**: Average cost to acquire one customer (closed deal).

**Data Sources**: Campaign spend data, CRM opportunity close events.

**Formula**: `total_campaign_spend / total_customers`

**Granularity**: Campaign-level, channel-level, workspace-level.

**Notes**: Includes all spend attributed to closed-won opportunities (first-touch or multi-touch attribution).

---

### CPA (Cost Per Acquisition)

**Definition**: Generic cost per acquisition metric (can be leads, signups, trials, etc.).

**Data Sources**: Campaign spend data, acquisition events.

**Formula**: `total_campaign_spend / total_acquisitions`

**Granularity**: Campaign-level, channel-level, workspace-level.

**Notes**: Acquisition type configurable (lead, signup, trial, customer).

---

## Performance Metrics

### ROI (Return on Investment)

**Definition**: Return on investment as percentage of revenue relative to spend.

**Data Sources**: Campaign spend data, revenue attribution.

**Formula**: `((revenue - spend) / spend) * 100`

**Granularity**: Campaign-level, channel-level, workspace-level, time-period.

**Notes**: Revenue attribution requires attribution model (P1-004 backend pending).

---

### ROAS (Return on Ad Spend)

**Definition**: Revenue generated per dollar of ad spend.

**Data Sources**: Campaign spend data, revenue attribution.

**Formula**: `revenue / spend`

**Granularity**: Campaign-level, channel-level, workspace-level, time-period.

**Notes**: Commonly used in paid advertising (Google Ads, Meta, etc.).

---

### CTR (Click-Through Rate)

**Definition**: Percentage of ad impressions that result in clicks.

**Data Sources**: Ad platform APIs (Google Ads, Meta, LinkedIn).

**Formula**: `(clicks / impressions) * 100`

**Granularity**: Campaign-level, ad-group-level, creative-level, daily.

**Notes**: Platform-specific metric; aggregated across channels for reporting.

---

### CPC (Cost Per Click)

**Definition**: Average cost per click on ads.

**Data Sources**: Ad platform APIs (Google Ads, Meta, LinkedIn).

**Formula**: `spend / clicks`

**Granularity**: Campaign-level, ad-group-level, daily.

**Notes**: Platform-specific metric; averaged across channels for reporting.

---

## Retention Metrics

### LTV (Lifetime Value)

**Definition**: Total revenue expected from a customer over their relationship.

**Data Sources**: CRM subscription data, historical revenue, churn models.

**Formula**: `ARPU * average_customer_lifetime`

Where:
- `ARPU` = Average Revenue Per User (monthly or annual)
- `average_customer_lifetime` = Average months/years before churn

**Granularity**: Customer-level, segment-level, workspace-level.

**Notes**: Requires historical revenue data and churn modeling (P1-017 table-stakes gap).

---

### Retention Rate

**Definition**: Percentage of customers retained over a period.

**Data Sources**: Subscription data, churn events.

**Formula**: `((customers_end - customers_new) / customers_start) * 100`

**Granularity**: Monthly, quarterly, annually by segment, workspace.

**Notes**: Critical for subscription-based revenue models.

---

## Attribution Metrics

### Attribution Credit

**Definition**: Revenue or conversion credit assigned to each touchpoint in the customer journey.

**Data Sources**: Touchpoint events, conversion events, attribution model.

**Attribution Models**:
- **First-Touch**: 100% credit to first touchpoint
- **Last-Touch**: 100% credit to last touchpoint
- **Linear**: Equal credit across all touchpoints
- **Time-Decay**: More credit to recent touchpoints
- **Position-Based**: More credit to first and last touchpoints

**Formula**: Model-specific algorithm applied to touchpoint sequence.

**Granularity**: Touchpoint-level, campaign-level, channel-level.

**Notes**: Attribution model selection configurable per workspace (P1-004 backend pending).

---

## Implementation Status

| Metric | Backend Implementation | Data Sources | Status |
|--------|----------------------|-------------|--------|
| Leads | Campaign entity | Lead capture events | Partial (lead capture not yet implemented) |
| MQL | Lead scoring service | Lead scoring models | Pending (lead scoring not yet implemented) |
| SQL | CRM integration | CRM opportunity data | Pending (CRM sync is P1-017 table-stakes gap) |
| Conversion Rate | Analytics service | Lead lifecycle events | Pending (analytics backend is P1-004) |
| CPL | Campaign spend + leads | Campaign spend, lead capture | Pending (lead capture not yet implemented) |
| CAC | Campaign spend + customers | Campaign spend, CRM | Pending (CRM sync is P1-017 table-stakes gap) |
| CPA | Campaign spend + acquisitions | Campaign spend, acquisition events | Partial (depends on acquisition type) |
| ROI | Analytics service | Campaign spend, revenue | Pending (analytics backend is P1-004) |
| ROAS | Analytics service | Campaign spend, revenue | Pending (analytics backend is P1-004) |
| CTR | Ad platform connectors | Google Ads, Meta APIs | Partial (connector layer exists, runtime wiring pending) |
| CPC | Ad platform connectors | Google Ads, Meta APIs | Partial (connector layer exists, runtime wiring pending) |
| LTV | Analytics service | Subscription data, churn models | Pending (subscription data not yet implemented) |
| Retention Rate | Analytics service | Subscription data, churn events | Pending (subscription data not yet implemented) |
| Attribution Credit | Attribution service | Touchpoint events, conversions | Pending (attribution backend is P1-004) |

## References

- P1-004: Build real funnel analytics, attribution, ROI/ROAS APIs and persistence
- P1-017: Close table-stakes gaps before commercial positioning (CRM sync, lead capture)
