import type { Meta, StoryObj } from '@storybook/react';
import DataTable from './DataTable';

interface Row {
  timestamp: string;
  severity: string;
  message: string;
}

const rows: Row[] = Array.from({ length: 5 }, (_, index) => ({
  timestamp: new Date(Date.now() - index * 60000).toISOString(),
  severity: index % 2 === 0 ? 'info' : 'warning',
  message: `Event ${index + 1} processed`,
}));

const meta: Meta<typeof DataTable<any>> = {
  title: 'Common/DataTable',
  component: DataTable<any>,
  args: {
    columns: [
      {
        id: 'timestamp',
        header: 'Timestamp',
        render: (row: Row) => new Date(row.timestamp).toLocaleTimeString(),
      },
      { id: 'severity', header: 'Severity', accessor: (row: Row) => row.severity },
      { id: 'message', header: 'Message', accessor: (row: Row) => row.message },
    ],
    rows,
  },
};

export default meta;

type Story = StoryObj<typeof DataTable<any>>;

export const Default: Story = {};
