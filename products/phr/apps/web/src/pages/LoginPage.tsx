import React from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { Link } from 'react-router';

export function LoginPage(): React.ReactElement {
  return (
    <div className="centered-page">
      <Card className="hero-card">
        <CardHeader title="Welcome to PHR Nepal" subheader="Secure patient access with consent-aware workflows" />
        <CardContent>
          <div className="stack gap-md">
            <Input aria-label="National ID" placeholder="National ID or MRN" />
            <Input aria-label="Password" type="password" placeholder="Password" />
            <div className="row gap-sm">
              <Button className="primary-cta">Sign In</Button>
              <Link className="inline-link" to="/dashboard">Continue with demo account</Link>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}