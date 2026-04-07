import React from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { demoDashboard } from '../mockData';

export function DashboardPage(): React.ReactElement {
  const { patient, consents, appointments, labs, medications } = demoDashboard;

  return (
    <div className="stack gap-lg">
      <section className="hero-panel">
        <div>
          <p className="eyebrow">Patient summary</p>
          <h2>{patient.name}</h2>
          <p className="muted">{patient.location} · Blood group {patient.bloodType} · Emergency contact {patient.emergencyContact}</p>
        </div>
        <div className="metric-strip">
          <div><span>{consents.length}</span><small>Active consent flows</small></div>
          <div><span>{appointments.length}</span><small>Upcoming visits</small></div>
          <div><span>{labs.length}</span><small>Recent lab reports</small></div>
          <div><span>{medications.length}</span><small>Medication plans</small></div>
        </div>
      </section>
      <section className="dashboard-grid">
        <Card>
          <CardHeader title="Care plan" subheader="Next steps for this week" />
          <CardContent>
            <ul className="stack gap-sm">
              <li>Complete endocrinology follow-up before 10 April.</li>
              <li>Renew expiring lab data consent by 15 April.</li>
              <li>Share latest HbA1c observation with Nepal HIE for continuity of care.</li>
            </ul>
          </CardContent>
        </Card>
        <Card>
          <CardHeader title="Emergency readiness" subheader="Protected break-glass access" />
          <CardContent>
            <ul className="stack gap-sm">
              <li>Emergency contact verified.</li>
              <li>Two caregivers authorized for after-hours review.</li>
              <li>Last audit review completed 36 hours ago.</li>
            </ul>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}