import React from 'react';
import Grid from '@mui/material/Grid';
import {
  Button,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import PageHeader from '../components/common/PageHeader';
import InfoCard from '../components/common/InfoCard';
import { useDiagnosticsSnapshot } from '../hooks/useDiagnostics';

export default function DiagnosticsPage() {
  const { data, isLoading } = useDiagnosticsSnapshot();
  const [corrId, setCorrId] = React.useState('');

  const generateCorrId = () => {
    const v4 = ([1e7] as any + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, (c: string) =>
      (Number(c) ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (Number(c) / 4)))).toString(16),
    );
    setCorrId(v4);
  };

  if (isLoading || !data) {
    return (
      <Stack spacing={3}>
        <PageHeader title="Diagnostics" description="Quick health checks and useful utilities." />
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      </Stack>
    );
  }

  return (
    <Stack spacing={4}>
      <PageHeader title="Diagnostics" description="Quick health checks and useful utilities." />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 4 }}>
          <InfoCard title="Health status" subtitle="Realtime health snapshot">
            <Stack spacing={1.5}>
              {Object.entries(data.health).map(([service, status]) => (
                <Stack direction="row" justifyContent="space-between" key={service}>
                  <Typography variant="body2">{service}</Typography>
                  <Typography variant="body2" color={status === 'healthy' ? 'success.main' : 'warning.main'}>
                    {status}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <InfoCard title="Identity" subtitle="Current desktop session identity">
            <Stack spacing={1}>
              <Typography variant="body2">Subject {data.identity.subject}</Typography>
              <Typography variant="body2">Tenant {data.identity.tenant}</Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {data.identity.roles.map((role) => (
                  <Typography key={role} variant="caption" color="text.secondary">
                    {role}
                  </Typography>
                ))}
              </Stack>
            </Stack>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <InfoCard title="Correlation ID" subtitle="Generate and share tracing IDs">
            <Stack spacing={2}>
              <Typography variant="body2">{corrId || data.correlationId}</Typography>
              <Stack direction="row" spacing={1}>
                <Button variant="contained" size="small" onClick={generateCorrId}>
                  Generate new
                </Button>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => navigator.clipboard.writeText(corrId || data.correlationId)}
                >
                  Copy
                </Button>
              </Stack>
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <InfoCard title="Prometheus metrics" subtitle="Sample scrape for quick verification">
            <pre style={{ maxHeight: 220, overflow: 'auto', margin: 0 }}>
              {data.prometheusSample}
            </pre>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <InfoCard title="Recent failures" subtitle="Latest issues requiring attention">
            <Stack spacing={1.5}>
              {data.recentFailures.map((failure) => (
                <Stack key={failure.correlationId} spacing={0.5}>
                  <Typography variant="subtitle2">{failure.subsystem}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {failure.message}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {new Date(failure.timestamp).toLocaleString()} · Corr {failure.correlationId}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>
    </Stack>
  );
}
