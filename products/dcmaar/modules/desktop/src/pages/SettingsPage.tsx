import React from 'react';
import {
  Button,
  CircularProgress,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import PageHeader from '../components/common/PageHeader';
import InfoCard from '../components/common/InfoCard';
import ConnectorConfigUI from '../components/ConnectorConfigUI';
import { useSettingsSnapshot } from '../hooks/useSettings';
import { useThemeMode } from '../providers/ThemeModeProvider';

export default function SettingsPage() {
  const { data, isLoading } = useSettingsSnapshot();
  const [formState, setFormState] = React.useState(data);
  const { setPreference, preference, mode } = useThemeMode();

  React.useEffect(() => {
    if (data) {
      setFormState(data);
    }
  }, [data]);

  const handleChange = (key: string, value: unknown) => {
    setFormState((prev) => ({
      ...prev!,
      general: {
        ...prev!.general,
        ...(key.startsWith('general.') ? { [key.split('.')[1]]: value } : {}),
      },
      integrations: {
        ...prev!.integrations,
        ...(key.startsWith('integrations.') ? { [key.split('.')[1]]: value } : {}),
      },
      notifications: {
        ...prev!.notifications,
        ...(key.startsWith('notifications.') ? { [key.split('.')[1]]: value } : {}),
      },
      advanced: {
        ...prev!.advanced,
        ...(key.startsWith('advanced.') ? { [key.split('.')[1]]: value } : {}),
      },
    }));
  };

  const handleTextChange = (key: string) =>
    (event: React.ChangeEvent<HTMLInputElement>) => handleChange(key, event.target.value);

  const handleNumberChange = (key: string) =>
    (event: React.ChangeEvent<HTMLInputElement>) =>
      handleChange(key, Number(event.target.value));

  const handleSwitchChange = (key: string) =>
    (event: React.ChangeEvent<HTMLInputElement>) => handleChange(key, event.target.checked);

  const handleThemeToggle = (event: React.ChangeEvent<HTMLInputElement>) => {
    const nextTheme = event.target.checked ? 'dark' : 'light';
    setPreference(nextTheme);
    handleChange('general.theme', nextTheme);
  };

  const isDarkTheme =
    (formState?.general.theme ?? preference) === 'dark' ||
    (preference === 'system' && mode === 'dark');

  if (isLoading || !formState) {
    return (
      <Stack spacing={3}>
        <PageHeader
          title="Workspace Settings"
          description="Manage environment preferences, integrations, and advanced toggles."
        />
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      </Stack>
    );
  }

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Workspace Settings"
        description="Manage environment preferences, integrations, and advanced toggles."
        actions={[
          { label: 'Export config', variant: 'outlined' },
          { label: 'Apply changes', variant: 'contained' },
        ]}
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <InfoCard title="General" subtitle="Preferences that affect the desktop experience">
            <Stack spacing={2}>
              <TextField
                label="Timezone"
                size="small"
                value={formState.general.timezone}
                onChange={handleTextChange('general.timezone')}
              />
              <TextField
                label="Refresh interval (seconds)"
                size="small"
                type="number"
                value={formState.general.refreshIntervalSeconds}
                onChange={handleNumberChange('general.refreshIntervalSeconds')}
              />
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="body2">Dark theme</Typography>
                <Switch
                  inputProps={{ 'aria-label': 'Toggle dark theme' }}
                  checked={isDarkTheme}
                  onChange={handleThemeToggle}
                />
              </Stack>
            </Stack>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <InfoCard title="Integrations" subtitle="Service endpoints and tokens">
            <Stack spacing={2}>
              <TextField
                label="API base URL"
                size="small"
                value={formState.integrations.apiBaseUrl}
                onChange={handleTextChange('integrations.apiBaseUrl')}
              />
              <TextField
                label="Telemetry collector"
                size="small"
                value={formState.integrations.telemetryCollector}
                onChange={handleTextChange('integrations.telemetryCollector')}
              />
              <TextField
                label="Notification email"
                size="small"
                value={formState.integrations.notificationEmail}
                onChange={handleTextChange('integrations.notificationEmail')}
              />
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <InfoCard title="Notifications" subtitle="Choose how alerts reach your team">
            <Stack spacing={2}>
              {(['push', 'email', 'sms'] as const).map((channel) => (
                <Stack direction="row" spacing={1} alignItems="center" key={channel}>
                  <Typography variant="body2" sx={{ minWidth: 120, textTransform: 'capitalize' }}>
                    {channel}
                  </Typography>
                  <Switch
                    checked={formState.notifications[channel]}
                    onChange={handleSwitchChange(`notifications.${channel}`)}
                  />
                </Stack>
              ))}
            </Stack>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <InfoCard title="Advanced" subtitle="Diagnostics and feature flags">
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="body2">Verbose logging</Typography>
                <Switch
                  checked={formState.advanced.enableVerboseLogging}
                  onChange={handleSwitchChange('advanced.enableVerboseLogging')}
                />
              </Stack>
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="body2">UX telemetry</Typography>
                <Switch
                  checked={formState.advanced.collectAnonymisedUXMetrics}
                  onChange={handleSwitchChange('advanced.collectAnonymisedUXMetrics')}
                />
              </Stack>
              <TextField
                label="Feature flags"
                size="small"
                value={formState.advanced.featureFlags.join(', ')}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                  handleChange(
                    'advanced.featureFlags',
                    event.target.value.split(',').map((flag) => flag.trim()),
                  )
                }
              />
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>

      {/* Desktop Connector Configuration */}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12 }}>
          <InfoCard title="Desktop Connector System" subtitle="Configure telemetry sources and command sinks">
            <ConnectorConfigUI />
          </InfoCard>
        </Grid>
      </Grid>

      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button variant="outlined">Discard changes</Button>
        <Button variant="contained">Save settings</Button>
      </Stack>
    </Stack>
  );
}
