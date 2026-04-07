import React from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { exportPatientBundle } from '../api/phrApi';

export function SettingsPage(): React.ReactElement {
  const [syncStatus, setSyncStatus] = React.useState<string>('No sync requested yet.');

  const onSync = async (): Promise<void> => {
    const response = await exportPatientBundle();
    setSyncStatus(response);
  };

  return (
    <div className="two-column-layout">
      <Card>
        <CardHeader title="Profile and preferences" subheader="Localization, emergency contacts, and device preferences" />
        <CardContent>
          <ul className="stack gap-sm">
            <li>Language: English / Nepali bilingual labels ready.</li>
            <li>Preferred facility: Kathmandu diabetes care network.</li>
            <li>Emergency SMS escalation enabled.</li>
          </ul>
        </CardContent>
      </Card>
      <Card>
        <CardHeader title="Nepal HIE sync" subheader="Export patient summary to the exchange gateway" />
        <CardContent>
          <div className="stack gap-md">
            <Button className="primary-cta" onClick={() => void onSync()}>Prepare HIE submission</Button>
            <code className="code-inline">{syncStatus}</code>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}