import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { logoutSession } from '../api/authApi';
import { exportPatientBundle } from '../api/patientApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';

export function SettingsPage(): React.ReactElement {
  const [syncStatus, setSyncStatus] = React.useState<string>(t('settings.sync.initial'));
  const [logoutPending, setLogoutPending] = React.useState<boolean>(false);
  const { session, clearSession } = usePhrSession();
  const navigate = useNavigate();

  const onSync = async (): Promise<void> => {
    if (!session) {
      setSyncStatus(t('settings.sync.authRequired'));
      return;
    }
    const response = await exportPatientBundle({
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    });
    setSyncStatus(response);
  };

  const onLogout = async (): Promise<void> => {
    setLogoutPending(true);
    try {
      if (session) {
        await logoutSession({ tenantId: session.tenantId, principalId: session.principalId, role: session.role });
      }
    } catch {
      // Server-side logout failure must not block local session cleanup.
    } finally {
      clearSession();
      navigate('/login', { replace: true });
    }
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
      <Card>
        <CardHeader title={t('settings.logout.title')} subheader={t('settings.logout.subheader')} />
        <CardContent>
          <Button
            className="destructive-cta"
            onClick={() => void onLogout()}
            disabled={logoutPending}
            aria-label={t('settings.logout.button')}
          >
            {t('settings.logout.button')}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
