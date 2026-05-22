import React from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { exportPatientBundle } from '../api/phrApi';
import { t } from '../i18n/phrI18n';

export function SettingsPage(): React.ReactElement {
  const [syncStatus, setSyncStatus] = React.useState<string>(t('settings.sync.initial'));

  const onSync = async (): Promise<void> => {
    const response = await exportPatientBundle();
    setSyncStatus(response);
  };

  return (
    <div className="two-column-layout">
      <Card>
        <CardHeader title={t('settings.profile.title')} subheader={t('settings.profile.subheader')} />
        <CardContent>
          <ul className="stack gap-sm">
            <li>{t('settings.profile.language')}</li>
            <li>{t('settings.profile.facility')}</li>
            <li>{t('settings.profile.emergencySms')}</li>
          </ul>
        </CardContent>
      </Card>
      <Card>
        <CardHeader title={t('settings.hie.title')} subheader={t('settings.hie.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <Button className="primary-cta" onClick={() => void onSync()}>{t('settings.hie.prepare')}</Button>
            <code className="code-inline">{syncStatus}</code>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
