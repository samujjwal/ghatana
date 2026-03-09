import React from 'react';
import {
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import PageHeader from '../../components/common/PageHeader';
import InfoCard from '../../components/common/InfoCard';
import DataTable from '../../components/common/DataTable';
import { useAuditLog } from '../../hooks/useAudit';
import StatusBadge from '../../components/common/StatusBadge';

export default function AuditViewer() {
  const { data = [], isLoading } = useAuditLog();

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Audit Log"
        description="Immutable record of configuration changes, command executions, and approvals."
        actions={[{ label: 'Export JSON', variant: 'outlined' }]}
      />

      {isLoading ? (
        <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
          <CircularProgress />
        </Stack>
      ) : (
        <InfoCard title="Entries" subtitle={`Showing ${data.length} most recent entries`}>
          <DataTable
            columns={[
              {
                id: 'timestamp',
                header: 'Timestamp',
                render: (row: (typeof data)[number]) => new Date(row.timestamp).toLocaleString(),
              },
              {
                id: 'user',
                header: 'User',
                accessor: (row) => row.user,
              },
              {
                id: 'action',
                header: 'Action',
                render: (row: (typeof data)[number]) => <StatusBadge status={row.action} label={row.action} />,
              },
              {
                id: 'summary',
                header: 'Summary',
                accessor: (row) => row.summary,
              },
            ]}
            rows={data}
            getRowId={(row) => row.id}
            dense
          />
        </InfoCard>
      )}
    </Stack>
  );
}
