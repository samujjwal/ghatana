import React from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { t } from '../i18n/phrI18n';

export function LoginPage(): React.ReactElement {
  return (
    <div className="centered-page">
      <Card className="hero-card">
        <CardHeader title={t('login.title')} subheader={t('login.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <Input aria-label={t('login.nationalId.label')} placeholder={t('login.nationalId.placeholder')} />
            <Input aria-label={t('login.password.label')} type="password" placeholder={t('login.password.placeholder')} />
            <div className="row gap-sm">
              <Button className="primary-cta">{t('login.signIn')}</Button>
              <Link className="inline-link" to="/dashboard">{t('login.demo')}</Link>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
