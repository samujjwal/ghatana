import React from 'react';
import Grid from '@mui/material/Grid';
import { CircularProgress, Stack, Typography } from '@mui/material';
import PageHeader from '../components/common/PageHeader';
import { useDashboardData } from '../hooks/useDashboardData';
import MetricsSummary from '../components/dashboard/MetricsSummary';
import AgentStatusCard from '../components/dashboard/AgentStatusCard';
import ExtensionStatusCard from '../components/dashboard/ExtensionStatusCard';
import EventTimeline from '../components/dashboard/EventTimeline';
import RecommendationsCard from '../components/dashboard/RecommendationsCard';
import KpiTile from '../components/common/KpiTile';
import InfoCard from '../components/common/InfoCard';

const formatValue = (value: number, unit?: string) =>
  unit === 'events/min'
    ? `${Math.round(value).toLocaleString()}`
    : unit === '%'
      ? `${Math.round(value)}%`
      : unit === 'errors/min'
        ? `${Number(value).toFixed(1)}`
        : value.toString();

export default function DashboardPage() {
  const { data, isLoading } = useDashboardData();

  if (isLoading || !data) {
    return (
      <Stack spacing={3}>
        <PageHeader
          title="Operations Overview"
          description="Loading latest telemetry, events, and recommendations."
        />
        <Stack alignItems="center" justifyContent="center" sx={{ py: 8 }}>
          <CircularProgress />
        </Stack>
      </Stack>
    );
  }

  const { metrics, agent, extension, events, recommendations } = data;

  const lastValue = (series: { value: number }[]) =>
    series.length ? series[series.length - 1].value : 0;
  const prevValue = (series: { value: number }[]) =>
    series.length > 1 ? series[series.length - 2].value : 0;

  const metricCards = [
    {
      id: 'ingest',
      title: 'Ingest Throughput',
      data: metrics.ingestThroughput,
      value: `${formatValue(lastValue(metrics.ingestThroughput), 'events/min')}`,
      delta: `Δ ${Math.round(
        lastValue(metrics.ingestThroughput) - prevValue(metrics.ingestThroughput),
      )} events`,
      deltaColor: 'success' as const,
      unit: 'events/min',
    },
    {
      id: 'error',
      title: 'Error Rate',
      data: metrics.errorRate,
      value: `${formatValue(lastValue(metrics.errorRate), 'errors/min')}`,
      delta: `Δ ${Number(
        lastValue(metrics.errorRate) - prevValue(metrics.errorRate),
      ).toFixed(1)} /min`,
      deltaColor: 'warning' as const,
      unit: 'errors/min',
    },
    {
      id: 'cpu',
      title: 'CPU Utilisation',
      data: metrics.cpu,
      value: `${formatValue(lastValue(metrics.cpu), '%')}`,
      delta: `Δ ${Math.round(lastValue(metrics.cpu) - prevValue(metrics.cpu))}%`,
      deltaColor: 'info' as const,
      unit: '%',
    },
  ];

  const queueUtilisation = Math.round((agent.queue.depth / agent.queue.capacity) * 100);

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Operations Overview"
        description="Real-time visibility, proactive insights, and guided remediation across your fleet."
        actions={[
          { label: 'Export Snapshot', href: '#', variant: 'outlined' },
          { label: 'Create Report', href: '/reports', variant: 'contained' },
        ]}
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 3 }}>
          <KpiTile
            label="Live Agents"
            value="12"
            trend="+2 online"
            status="active"
            caption="Agent grid coverage"
          />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <KpiTile
            label="Exporter Queue"
            value={`${queueUtilisation}%`}
            trend={`${agent.queue.depth.toLocaleString()} messages`}
            status={queueUtilisation > 80 ? 'warning' : 'healthy'}
            caption="Aggregate backlog"
          />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <KpiTile
            label="Outstanding Approvals"
            value="4"
            trend="-1 vs yesterday"
            status="warning"
            caption="Awaiting reviewer action"
          />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <KpiTile
            label="Open Incidents"
            value="2"
            trend="Updated 5m ago"
            status="warning"
            caption="See Incident Command"
          />
        </Grid>
      </Grid>

      <MetricsSummary items={metricCards} />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, xl: 4 }}>
          <AgentStatusCard
            name={agent.name}
            version={agent.version}
            connected={agent.connected}
            uptimeSeconds={agent.uptimeSeconds}
            lastHeartbeat={agent.lastHeartbeat}
            queue={agent.queue}
            exporters={agent.exporters}
          />
        </Grid>
        <Grid size={{ xs: 12, xl: 4 }}>
          <ExtensionStatusCard
            connected={extension.connected}
            version={extension.version}
            latencyMs={extension.latencyMs}
            events={extension.events}
          />
        </Grid>
        <Grid size={{ xs: 12, xl: 4 }}>
          <InfoCard
            title="Next Best Actions"
            subtitle="Accelerate remediation with curated workflows"
            action={
              <Typography variant="caption" color="text.secondary">
                Powered by incidents + policies
              </Typography>
            }
          >
            <Stack spacing={2}>
              {recommendations.slice(0, 2).map((item) => (
                <Stack key={item.id} spacing={0.5}>
                  <Typography variant="subtitle1">{item.title}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {item.summary}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Confidence {Math.round(item.confidence * 100)}% · Risk {item.risk}
                  </Typography>
                </Stack>
              ))}
              <Typography variant="body2" color="primary.main">
                View full playbook →
              </Typography>
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 7 }}>
          <EventTimeline events={events} />
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <RecommendationsCard items={recommendations} />
        </Grid>
      </Grid>
    </Stack>
  );
}
