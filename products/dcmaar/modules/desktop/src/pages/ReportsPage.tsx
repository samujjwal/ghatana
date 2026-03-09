import React from 'react';
import {
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import PageHeader from '../components/common/PageHeader';
import InfoCard from '../components/common/InfoCard';
import DataTable from '../components/common/DataTable';
import { useReportsData } from '../hooks/useReports';

export default function ReportsPage() {
  const { data = [], isLoading } = useReportsData();

  if (isLoading) {
    return (
      <Stack spacing={3}>
        <PageHeader
          title="Workspace Reports"
          description="Automated summaries that keep stakeholders aligned."
        />
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      </Stack>
    );
  }

  const dailyCount = data.filter((report) =>
    report.schedule.toLowerCase().includes('daily'),
  ).length;
  const weeklyCount = data.filter((report) =>
    report.schedule.toLowerCase().includes('weekly'),
  ).length;

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Workspace Reports"
        description="Review delivery cadence, recipients, and data scoping across automation."
        actions={[
          { label: 'Create report', variant: 'contained' },
          { label: 'Export list', variant: 'outlined' },
        ]}
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 4 }}>
          <InfoCard
            title="Report volume"
            subtitle="Active scheduled reports"
          >
            <Stack spacing={0.5}>
              <Typography variant="h5" fontWeight={600}>
                {data.length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Total definitions across this workspace
              </Typography>
            </Stack>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <InfoCard title="Daily cadence" subtitle="Operational runbooks">
            <Stack spacing={0.5}>
              <Typography variant="h5" fontWeight={600}>
                {dailyCount}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Run before business hours
              </Typography>
            </Stack>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <InfoCard title="Weekly cadence" subtitle="Strategic digests">
            <Stack spacing={0.5}>
              <Typography variant="h5" fontWeight={600}>
                {weeklyCount}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Monday exports at 08:00 UTC
              </Typography>
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>

      <InfoCard title="Scheduled reports" subtitle="Delivery window, owners, and scope">
        <DataTable
          dense
          columns={[
            {
              id: 'name',
              header: 'Name',
              render: (row: (typeof data)[number]) => (
                <Stack spacing={0.25}>
                  <Typography variant="body2" fontWeight={600}>
                    {row.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {row.owner}
                  </Typography>
                </Stack>
              ),
            },
            {
              id: 'schedule',
              header: 'Schedule',
              accessor: (row) => row.schedule,
            },
            {
              id: 'recipients',
              header: 'Recipients',
              render: (row: (typeof data)[number]) => (
                <Stack direction="row" spacing={0.5} flexWrap="wrap">
                  {row.recipients.slice(0, 3).map((recipient) => (
                    <Chip key={recipient} label={recipient} size="small" />
                  ))}
                  {row.recipients.length > 3 ? (
                    <Chip label={`+${row.recipients.length - 3}`} size="small" variant="outlined" />
                  ) : null}
                </Stack>
              ),
            },
            {
              id: 'lastGenerated',
              header: 'Last generated',
              render: (row: (typeof data)[number]) =>
                new Date(row.lastGenerated).toLocaleString(),
            },
          ]}
          rows={data}
          getRowId={(row) => row.id}
          emptyState={
            <Typography variant="body2" color="text.secondary">
              No reports defined yet. Create your first automation to keep stakeholders in sync.
            </Typography>
          }
        />
      </InfoCard>

      <InfoCard title="Common filters" subtitle="Frequently applied criteria">
        <Stack direction="row" spacing={1} flexWrap="wrap">
          {data
            .flatMap((report) => Object.entries(report.filters))
            .slice(0, 10)
            .map(([key, value], index) => (
              <Chip key={`${key}-${index}`} label={`${key}: ${String(value)}`} size="small" />
            ))}
        </Stack>
      </InfoCard>
    </Stack>
  );
}

