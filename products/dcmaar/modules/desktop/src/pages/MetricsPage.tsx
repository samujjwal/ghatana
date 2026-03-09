import React from 'react';
import {
  Chip,
  CircularProgress,
  MenuItem,
  Stack,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import PageHeader from '../components/common/PageHeader';
import FilterToolbar from '../components/common/FilterToolbar';
import InfoCard from '../components/common/InfoCard';
import KpiTile from '../components/common/KpiTile';
import TimeSeriesChart from '../components/metrics/TimeSeriesChart';
import { useMetricsCatalogue, useMetricsSeries } from '../hooks/useMetrics';
import DataTable from '../components/common/DataTable';

export default function MetricsPage() {
  const theme = useTheme();
  const palette = [
    theme.palette.primary.main,
    theme.palette.secondary.main,
    theme.palette.error.main,
    theme.palette.success.main,
    theme.palette.warning.main,
  ];

  const { data: catalogue = [], isLoading: loadingCatalogue } = useMetricsCatalogue();
  const [selectedMetric, setSelectedMetric] = React.useState<string>();
  const [searchValue, setSearchValue] = React.useState('');
  const [selectedWindow, setSelectedWindow] = React.useState('24h');

  React.useEffect(() => {
    if (!selectedMetric && catalogue.length) {
      setSelectedMetric(catalogue[0].id);
    }
  }, [catalogue, selectedMetric]);

  const { data: series = [], isLoading: loadingSeries } = useMetricsSeries(selectedMetric ?? '');

  const filteredCatalogue = catalogue.filter((metric) =>
    metric.name.toLowerCase().includes(searchValue.toLowerCase()),
  );

  const metricMeta = catalogue.find((metric) => metric.id === selectedMetric);

  const values = series.map((point) => point.value);
  const latestValue = values.length ? values[values.length - 1] : 0;
  const previousValue = values.length > 1 ? values[values.length - 2] : latestValue;
  const change = latestValue - previousValue;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const avg = values.reduce((acc, value) => acc + value, 0) / (values.length || 1);

  const chartData = series.map((point) => ({
    timestamp: point.timestamp,
    [metricMeta?.name ?? 'Metric']: point.value,
  }));

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Metric Explorer"
        description="Analyse key telemetry streams with sharable filters, annotations, and thresholds."
        actions={[
          { label: 'Download CSV', variant: 'outlined' },
          { label: 'Schedule Report', variant: 'contained' },
        ]}
      />

      <FilterToolbar
        title="Catalogue"
        searchPlaceholder="Search metrics (e.g. ingest, cpu, latency)"
        searchValue={searchValue}
        onSearchChange={setSearchValue}
        activeFilters={[{ label: `Window ${selectedWindow}`, value: selectedWindow }]}
        onClearFilters={() => {
          setSearchValue('');
          setSelectedWindow('24h');
        }}
        endAdornment={
          <TextField
            select
            size="small"
            value={selectedWindow}
            label="Time window"
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              setSelectedWindow(event.target.value)
            }
          >
            <MenuItem value="1h">Last hour</MenuItem>
            <MenuItem value="6h">Last 6 hours</MenuItem>
            <MenuItem value="24h">Last 24 hours</MenuItem>
            <MenuItem value="7d">Last 7 days</MenuItem>
          </TextField>
        }
      />

      <Stack direction="row" spacing={1} flexWrap="wrap">
        {filteredCatalogue.map((metric) => (
          <Chip
            key={metric.id}
            label={metric.name}
            color={metric.id === selectedMetric ? 'primary' : 'default'}
            onClick={() => setSelectedMetric(metric.id)}
            sx={{ fontWeight: 600 }}
          />
        ))}
      </Stack>

      {loadingCatalogue || loadingSeries ? (
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      ) : selectedMetric ? (
        <Stack spacing={4}>
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 3 }}>
              <KpiTile
                label="Current value"
                value={`${latestValue.toFixed(2)} ${metricMeta?.unit ?? ''}`}
                trend={`${change >= 0 ? '+' : ''}${change.toFixed(2)} vs prev`}
                status={change >= 0 ? 'healthy' : 'warning'}
                caption="Most recent measurement"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <KpiTile
                label="Average"
                value={`${avg.toFixed(2)} ${metricMeta?.unit ?? ''}`}
                trend="All samples"
                caption="Aggregation across window"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <KpiTile
                label="Min"
                value={`${min.toFixed(2)} ${metricMeta?.unit ?? ''}`}
                caption="Lowest observed"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <KpiTile
                label="Max"
                value={`${max.toFixed(2)} ${metricMeta?.unit ?? ''}`}
                caption="Peak value"
                status="warning"
              />
            </Grid>
          </Grid>

          <InfoCard
            title={metricMeta?.name ?? 'Metric'}
            subtitle={metricMeta?.description}
          >
            <TimeSeriesChart
              title={`${metricMeta?.name ?? 'Metric'} Trend`}
              data={chartData}
              description="Values are interpolated across the selected time window. Hover to inspect exact samples."
              series={[
                {
                  key: metricMeta?.name ?? 'Metric',
                  label: metricMeta?.name ?? 'Metric',
                  color: palette[0],
                },
              ]}
            />
          </InfoCard>

          <InfoCard title="Sample data" subtitle="Last 20 samples for quick reference">
            <DataTable
              dense
              columns={[
                {
                  id: 'timestamp',
                  header: 'Timestamp',
                  render: (row: (typeof series)[number]) =>
                    new Date(row.timestamp).toLocaleString(),
                },
                {
                  id: 'value',
                  header: 'Value',
                  render: (row: (typeof series)[number]) => `${row.value} ${metricMeta?.unit ?? ''}`,
                },
              ]}
              rows={series.slice(-20).reverse()}
              getRowId={(_, index) => index}
            />
          </InfoCard>
        </Stack>
      ) : (
        <InfoCard title="No metric selected">
          <Typography variant="body2" color="text.secondary">
            Use the catalogue chips above to choose a metric stream to explore.
          </Typography>
        </InfoCard>
      )}
    </Stack>
  );
}
