import React from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { t } from '../i18n/phrI18n';

export function EmergencyAccessPage(): React.ReactElement {
  return (
    <div className="two-column-layout">
      <Card>
        <CardHeader title={t('emergency.title')} subheader={t('emergency.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <p>{t('emergency.summary')}</p>
            <ul className="stack gap-sm">
              <li>{t('emergency.reason')}</li>
              <li>{t('emergency.consent')}</li>
              <li>{t('emergency.redaction')}</li>
            </ul>
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader title={t('emergency.review.title')} subheader={t('emergency.review.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <Button className="danger-button">{t('emergency.request')}</Button>
            <Button className="secondary-button">{t('emergency.notify')}</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
