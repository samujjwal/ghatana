import React from 'react';
import {
  Chip,
  CircularProgress,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import PageHeader from '../components/common/PageHeader';
import InfoCard from '../components/common/InfoCard';
import DataTable from '../components/common/DataTable';
import { usePolicyData } from '../hooks/usePolicies';
import StatusBadge from '../components/common/StatusBadge';

export default function PoliciesPage() {
  const { data, isLoading } = usePolicyData();
  const [selectedSubject, setSelectedSubject] = React.useState<string>();
  const [selectedPolicyId, setSelectedPolicyId] = React.useState<string>();

  React.useEffect(() => {
    if (data?.subjects?.length && !selectedSubject) {
      setSelectedSubject(data.subjects[0].id);
    }
    if (data?.catalogue?.length && !selectedPolicyId) {
      setSelectedPolicyId(data.catalogue[0].id);
    }
  }, [data, selectedSubject, selectedPolicyId]);

  if (isLoading || !data) {
    return (
      <Stack spacing={3}>
        <PageHeader
          title="Policy Governance"
          description="Review effective access, catalogue definitions, and pending diffs."
        />
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      </Stack>
    );
  }

  const selectedPolicy = data.catalogue.find((policy) => policy.id === selectedPolicyId);

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Policy Governance"
        description="Review effective access, catalogue definitions, and pending diffs."
        actions={[
          { label: 'Publish policy', variant: 'contained' },
          { label: 'Export catalogue', variant: 'outlined' },
        ]}
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 4 }}>
          <InfoCard title="Subjects" subtitle="Select a subject to view effective policies">
            <Stack spacing={2}>
              <TextField
                select
                label="Subject"
                size="small"
                value={selectedSubject ?? ''}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                  setSelectedSubject(event.target.value)
                }
              >
                {data.subjects.map((subject) => (
                  <MenuItem value={subject.id} key={subject.id}>
                    {subject.name}
                  </MenuItem>
                ))}
              </TextField>
              <Stack spacing={0.5}>
                <Typography variant="caption" color="text.secondary">
                  Roles
                </Typography>
                <Stack direction="row" spacing={1}>
                  {data.subjects
                    .find((subject) => subject.id === selectedSubject)
                    ?.roles.map((role) => (
                      <Chip key={role} label={role} size="small" />
                    ))}
                </Stack>
              </Stack>
            </Stack>
          </InfoCard>
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <InfoCard title="Policy catalogue" subtitle="Apply filters and drill into versions">
            <DataTable
              columns={[
                {
                  id: 'name',
                  header: 'Policy',
                  render: (row: (typeof data.catalogue)[number]) => (
                    <Stack spacing={0.5}>
                      <Typography variant="body2" fontWeight={600}>
                        {row.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {row.version}
                      </Typography>
                    </Stack>
                  ),
                },
                {
                  id: 'status',
                  header: 'Status',
                  render: (row: (typeof data.catalogue)[number]) => (
                    <StatusBadge status={row.status.toLowerCase()} label={row.status} />
                  ),
                },
                {
                  id: 'owner',
                  header: 'Owner',
                  accessor: (row) => row.owner,
                },
                {
                  id: 'updatedAt',
                  header: 'Updated',
                  render: (row: (typeof data.catalogue)[number]) =>
                    new Date(row.updatedAt).toLocaleString(),
                },
              ]}
              rows={data.catalogue}
              getRowId={(row) => row.id}
              onRowClick={(row) => setSelectedPolicyId(row.id)}
              dense
            />
          </InfoCard>
        </Grid>
      </Grid>

      {selectedPolicy ? (
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}>
            <InfoCard
              title="Policy detail"
              subtitle={`${selectedPolicy.name} · ${selectedPolicy.version}`}
            >
              <Stack spacing={2}>
                <Typography variant="body2" color="text.secondary">
                  Owner {selectedPolicy.owner}
                </Typography>
                <Stack direction="row" spacing={1}>
                  {selectedPolicy.tags.map((tag) => (
                    <Chip key={tag} label={tag} size="small" />
                  ))}
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Last updated {new Date(selectedPolicy.updatedAt).toLocaleString()}
                </Typography>
              </Stack>
            </InfoCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <InfoCard
              title="Pending diff"
              subtitle="Review changes before publishing"
            >
              <Stack spacing={1}>
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
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}
