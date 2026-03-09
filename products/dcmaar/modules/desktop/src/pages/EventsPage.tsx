import React from 'react';
import {
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
// Use MUI Grid from the installed package
import Grid from '@mui/material/Grid';
import PageHeader from '../components/common/PageHeader';
import FilterToolbar from '../components/common/FilterToolbar';
import DataTable from '../components/common/DataTable';
import InfoCard from '../components/common/InfoCard';
import StatusBadge from '../components/common/StatusBadge';
import { useEventsData } from '../hooks/useEvents';

type EventRecord = ReturnType<typeof useEventsData> extends { data: infer D }
  ? D extends Array<infer Item>
  ? Item
  : never
  : never;

const severityOptions = ['critical', 'error', 'warning', 'info'] as const;

export default function EventsPage() {
  const { data = [], isLoading } = useEventsData();
  const [searchValue, setSearchValue] = React.useState('');
  const [selectedSeverities, setSelectedSeverities] = React.useState<string[]>([]);
  const [selectedTypes, setSelectedTypes] = React.useState<string[]>([]);
  const [selectedEvent, setSelectedEvent] = React.useState<EventRecord | null>(null);

  const allTypes = React.useMemo(
    () => Array.from(new Set(data.map((event) => event.type))),
    [data],
  );

  const filteredEvents = data.filter((event) => {
    const matchesSeverity =
      !selectedSeverities.length || selectedSeverities.includes(event.severity);
    const matchesType = !selectedTypes.length || selectedTypes.includes(event.type);
    const matchesSearch =
      !searchValue ||
      event.message.toLowerCase().includes(searchValue.toLowerCase()) ||
      event.title.toLowerCase().includes(searchValue.toLowerCase()) ||
      event.source?.toLowerCase().includes(searchValue.toLowerCase() ?? '');
    return matchesSeverity && matchesType && matchesSearch;
  });

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Event Stream"
        description="Inspect operational events with advanced filtering, details, and shareable correlations."
      />

      <FilterToolbar
        title="Filters"
        searchValue={searchValue}
        onSearchChange={setSearchValue}
        activeFilters={[
          ...selectedSeverities.map((value) => ({ label: `Severity ${value}`, value })),
          ...selectedTypes.map((value) => ({ label: `Type ${value}`, value })),
        ]}
        onClearFilters={() => {
          setSearchValue('');
          setSelectedSeverities([]);
          setSelectedTypes([]);
        }}
      />

      <Stack direction="row" spacing={1} flexWrap="wrap">
        {severityOptions.map((severity) => (
          <Chip
            key={severity}
            label={severity}
            color={selectedSeverities.includes(severity) ? 'primary' : 'default'}
            onClick={() =>
              setSelectedSeverities((prev) =>
                prev.includes(severity)
                  ? prev.filter((item) => item !== severity)
                  : [...prev, severity],
              )
            }
            sx={{ textTransform: 'capitalize', fontWeight: 600 }}
          />
        ))}
      </Stack>

      <Stack direction="row" spacing={1} flexWrap="wrap">
        {allTypes.map((type) => (
          <Chip
            key={type}
            label={type}
            color={selectedTypes.includes(type) ? 'secondary' : 'default'}
            onClick={() =>
              setSelectedTypes((prev) =>
                prev.includes(type) ? prev.filter((item) => item !== type) : [...prev, type],
              )
            }
            sx={{ textTransform: 'capitalize', fontWeight: 600 }}
          />
        ))}
      </Stack>

      {isLoading ? (
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      ) : (
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 7 }}>
            <InfoCard title="Events" subtitle={`Showing ${filteredEvents.length} results`}>
              <DataTable
                columns={[
                  {
                    id: 'timestamp',
                    header: 'Timestamp',
                    render: (row: EventRecord) => new Date(row.timestamp).toLocaleString(),
                  },
                  {
                    id: 'severity',
                    header: 'Severity',
                    render: (row: EventRecord) => <StatusBadge status={row.severity} />,
                  },
                  {
                    id: 'type',
                    header: 'Type',
                    render: (row: EventRecord) => (
                      <Typography variant="body2" textTransform="capitalize">
                        {row.type}
                      </Typography>
                    ),
                  },
                  {
                    id: 'title',
                    header: 'Title',
                    render: (row: EventRecord) => (
                      <Stack spacing={0.5}>
                        <Typography variant="body2" fontWeight={600}>
                          {row.title}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" noWrap>
                          {row.message}
                        </Typography>
                      </Stack>
                    ),
                  },
                ]}
                rows={filteredEvents}
                getRowId={(row) => row.id}
                onRowClick={setSelectedEvent}
                dense
              />
            </InfoCard>
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <InfoCard
              title="Details"
              subtitle={
                selectedEvent
                  ? `Correlation ${selectedEvent.correlationId}`
                  : 'Select an event to view detail'
              }
            >
              {selectedEvent ? (
                <Stack spacing={2}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <StatusBadge status={selectedEvent.severity} />
                    <Typography variant="caption" color="text.secondary">
                      {new Date(selectedEvent.timestamp).toLocaleString()}
                    </Typography>
                  </Stack>
                  <Typography variant="h6">{selectedEvent.title}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selectedEvent.message}
                  </Typography>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">
                      Source
                    </Typography>
                    <Typography variant="body2">{selectedEvent.source ?? 'Unknown'}</Typography>
                  </Stack>
                  {selectedEvent.commandId ? (
                    <Stack spacing={0.5}>
                      <Typography variant="caption" color="text.secondary">
                        Remediation command
                      </Typography>
                      <Typography variant="body2">{selectedEvent.commandId}</Typography>
                    </Stack>
                  ) : null}
                  <Typography variant="caption" color="text.secondary">
                    Corr ID {selectedEvent.correlationId}
                  </Typography>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Choose an event from the table to inspect metadata, source, and remediation links.
                </Typography>
              )}
            </InfoCard>
          </Grid>
        </Grid>
      )}
    </Stack>
  );
}
