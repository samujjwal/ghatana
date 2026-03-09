import React from 'react';
import { CircularProgress, Stack, Typography } from '@mui/material';
import Grid from '@mui/material/Grid';
import PageHeader from '../components/common/PageHeader';
import FilterToolbar from '../components/common/FilterToolbar';
import CommandCatalogueList from '../components/commands/CommandCatalogueList';
import CommandDetail from '../components/commands/CommandDetail';
import ExecutionTimeline from '../components/commands/ExecutionTimeline';
import { useCommandsData } from '../hooks/useCommands';

export default function CommandsPage() {
  const { data, isLoading } = useCommandsData();
  const [searchValue, setSearchValue] = React.useState('');
  const [selectedCommandId, setSelectedCommandId] = React.useState<string>();

  if (isLoading || !data) {
    return (
      <Stack spacing={3}>
        <PageHeader
          title="Command Center"
          description="Execute runbooks, capture approvals, and monitor automation progress."
        />
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      </Stack>
    );
  }

  const { catalogue, executions } = data;
  const filteredCatalogue = catalogue.filter((command) =>
    command.name.toLowerCase().includes(searchValue.toLowerCase()),
  );
  const selected = catalogue.find((command) => command.id === selectedCommandId) ?? filteredCatalogue[0];

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Command Center"
        description="Execute runbooks, capture approvals, and monitor automation progress."
        actions={[
          { label: 'New runbook', variant: 'contained' },
          { label: 'Approval queue', variant: 'outlined' },
        ]}
      />

      <FilterToolbar
        title="Catalogue"
        searchValue={searchValue}
        onSearchChange={setSearchValue}
        onClearFilters={() => setSearchValue('')}
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 3 }}>
          <CommandCatalogueList
            commands={filteredCatalogue}
            selected={selected?.id}
            onSelect={setSelectedCommandId}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          {selected ? (
            <CommandDetail
              name={selected.name}
              description={selected.description}
              riskLevel={selected.riskLevel}
              requiresApproval={selected.requiresApproval}
              estimatedDurationMinutes={selected.estimatedDurationMinutes}
              parameters={selected.parameters}
            />
          ) : (
            <Typography variant="body2" color="text.secondary">
              Select a command from the left to inspect details and run it.
            </Typography>
          )}
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <ExecutionTimeline items={executions} />
        </Grid>
      </Grid>
    </Stack>
  );
}
