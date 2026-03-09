import React from 'react';
import Grid from '@mui/material/Grid';
import {
  CircularProgress,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import PageHeader from '../components/common/PageHeader';
import RecommendationCard from '../components/copilot/RecommendationCard';
import IncidentTimeline from '../components/copilot/IncidentTimeline';
import InfoCard from '../components/common/InfoCard';
import { useCopilotData } from '../hooks/useCopilot';

export default function CopilotPage() {
  const { data, isLoading } = useCopilotData();
  const [activeTab, setActiveTab] = React.useState(0);
  const [prompt, setPrompt] = React.useState(
    'High ingest latency on Splunk exporter across collectors',
  );

  if (isLoading || !data) {
    return (
      <Stack spacing={3}>
        <PageHeader
          title="Runbook Copilot"
          description="AI-assisted remediation, tailored guidance, and contextual insights."
        />
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      </Stack>
    );
  }

  const { recommendations, incidents } = data;

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Runbook Copilot"
        description="AI-assisted remediation, tailored guidance, and contextual insights."
        actions={[
          { label: 'Feedback', variant: 'outlined' },
          { label: 'Open Command Center', href: '/commands', variant: 'contained' },
        ]}
      />

      <InfoCard title="Describe your scenario" subtitle="Copilot analyses telemetry + policies to propose actions">
        <Stack spacing={2}>
          <TextField
            multiline
            minRows={3}
            maxRows={6}
            value={prompt}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              setPrompt(event.target.value)
            }
            placeholder="Describe the incident, symptoms, or question for Copilot"
          />
          <Tabs
            value={activeTab}
            onChange={(_event: React.SyntheticEvent, value: number) => setActiveTab(value)}
          >
            <Tab label="Recommendations" />
            <Tab label="Incident timeline" />
            <Tab label="Execution log" />
          </Tabs>
        </Stack>
      </InfoCard>

      {activeTab === 0 ? (
        <Grid container spacing={3}>
          {recommendations.map((item) => (
            <Grid key={item.id} size={{ xs: 12, md: 6 }}>
              <RecommendationCard recommendation={item} />
            </Grid>
          ))}
        </Grid>
      ) : activeTab === 1 ? (
        <IncidentTimeline items={incidents} />
      ) : (
        <InfoCard
          title="Command executions"
          subtitle="Copilot tracks the commands dispatched for this session"
        >
          <Typography variant="body2" color="text.secondary">
            Executions encapsulated to the current session appear here along with approvals,
            correlation IDs, and status. Run a recommendation to populate this list.
          </Typography>
        </InfoCard>
      )}
    </Stack>
  );
}
