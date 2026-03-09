import React from 'react';
import Grid from '@mui/material/Grid';
import {
  Button,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import PageHeader from '../../components/common/PageHeader';
import InfoCard from '../../components/common/InfoCard';
import { useControlHubDefaults } from '../../hooks/useControlHub';

export default function ControlHubPage() {
  const { data, isLoading } = useControlHubDefaults();
  const [editorValue, setEditorValue] = React.useState(data?.editor ?? '');
  const [beautifyError, setBeautifyError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (data?.editor) {
      setEditorValue(data.editor);
      setBeautifyError(null);
    }
  }, [data]);

  const handleBeautify = () => {
    try {
      const parsed = JSON.parse(editorValue);
      setEditorValue(JSON.stringify(parsed, null, 2));
      setBeautifyError(null);
    } catch (error) {
      console.warn('Failed to beautify config', error);
      setBeautifyError('Configuration must be valid JSON before it can be formatted.');
    }
  };

  if (isLoading || !data) {
    return (
      <Stack spacing={3}>
        <PageHeader
          title="Control Hub"
          description="Validate, diff, and deploy configuration bundles."
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
        title="Control Hub"
        description="Validate, diff, and deploy configuration bundles."
        actions={[
          { label: 'Dry-run', variant: 'outlined' },
          { label: 'Deploy change', variant: 'contained' },
        ]}
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 7 }}>
          <InfoCard
            title="Desired configuration"
            subtitle="Paste or edit JSON before running validation"
            action={
              <Button size="small" variant="outlined" onClick={handleBeautify}>
                Beautify
              </Button>
            }
          >
            <TextField
              multiline
              minRows={16}
              value={editorValue}
              onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                setEditorValue(event.target.value)
              }
              fullWidth
              InputProps={{ sx: { fontFamily: 'monospace' } }}
              error={Boolean(beautifyError)}
              helperText={beautifyError ?? ' '}
              FormHelperTextProps={{ 'aria-live': 'polite' }}
            />
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <InfoCard title="Change impact" subtitle="Diff summary prior to deployment">
            <Stack spacing={1.5}>
              <Typography variant="caption" color="success.main">
                Added
              </Typography>
              {data.diff.added.map((item) => (
                <Typography key={item} variant="body2">
                  + {item}
                </Typography>
              ))}

              <Typography variant="caption" color="warning.main">
                Modified
              </Typography>
              {data.diff.modified.map((entry) => (
                <Typography key={entry.path} variant="body2">
                  {entry.path}: {String(entry.oldValue)} → {String(entry.newValue)}
                </Typography>
              ))}
            </Stack>
          </InfoCard>
          <InfoCard title="Deployment checklist" subtitle="Ensure gating conditions are met">
            <Stack spacing={1}>
              <Typography variant="body2">• Approval quorum reached</Typography>
              <Typography variant="body2">• Affected exporters acknowledged</Typography>
              <Typography variant="body2">• Backup snapshot stored</Typography>
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>
    </Stack>
  );
}
