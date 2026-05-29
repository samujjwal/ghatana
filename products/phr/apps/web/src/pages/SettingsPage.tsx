import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader, Select } from '@ghatana/design-system';
import { logoutSession } from '../api/authApi';
import { exportPatientBundle } from '../api/patientApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';

export function SettingsPage(): React.ReactElement {
  const [syncStatus, setSyncStatus] = useState<string>(t('settings.sync.initial'));
  const [logoutPending, setLogoutPending] = useState<boolean>(false);
  const [language, setLanguage] = useState<string>('en');
  const [exporting, setExporting] = useState<boolean>(false);
  const { session, clearSession } = usePhrSession();
  const navigate = useNavigate();

  const onSync = async (): Promise<void> => {
    if (!session) {
      setSyncStatus(t('settings.sync.authRequired'));
      return;
    }
    setExporting(true);
    try {
      const response = await exportPatientBundle({
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      setSyncStatus(response);
    } catch (err: unknown) {
      logError('Failed to export patient bundle', undefined, { error: err });
      setSyncStatus('Export failed');
    } finally {
      setExporting(false);
    }
  };

  const onLogout = async (): Promise<void> => {
    setLogoutPending(true);
    try {
      if (session) {
        await logoutSession({ tenantId: session.tenantId, principalId: session.principalId, role: session.role });
      }
    } catch (err: unknown) {
      logError('Server-side logout failed, proceeding with local cleanup', undefined, { error: err });
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
          <div className="stack gap-md">
            <div>
              <label htmlFor="language-select">{t('settings.profile.language')}</label>
              <Select
                id="language-select"
                value={language}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setLanguage(e.target.value)}
                aria-label={t('settings.profile.language')}
              >
                <option value="en">English</option>
                <option value="ne">नेपाली</option>
              </Select>
            </div>
            <div className="stack gap-sm">
              <div className="row gap-sm align-center">
                <span>{t('settings.profile.facility')}</span>
                <span className="muted">{t('common.notAvailable')}</span>
              </div>
              <div className="row gap-sm align-center">
                <span>{t('settings.profile.emergencySms')}</span>
                <span className="muted">{t('common.notAvailable')}</span>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader title={t('settings.hie.title')} subheader={t('settings.hie.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <Button 
              className="primary-cta" 
              onClick={() => void onSync()}
              disabled={exporting}
              aria-busy={exporting}
            >
              {exporting ? 'Exporting...' : t('settings.hie.prepare')}
            </Button>
            <code className="code-inline" role="status" aria-live="polite">{syncStatus}</code>
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
