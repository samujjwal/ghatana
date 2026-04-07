import React from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { demoDashboard } from '../mockData';

export function AppointmentsPage(): React.ReactElement {
  return (
    <div className="two-column-layout">
      <Card>
        <CardHeader title="Upcoming appointments" subheader="Schedule and reschedule visits" />
        <CardContent>
          <div className="stack gap-md">
            {demoDashboard.appointments.map((appointment) => (
              <div key={appointment.id} className="data-card">
                <div>
                  <strong>{appointment.provider}</strong>
                  <p className="muted">{appointment.specialty} · {appointment.location}</p>
                </div>
                <span className="pill">{new Date(appointment.startsAt).toLocaleString()}</span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader title="Request a new slot" subheader="Patient self-scheduling" />
        <CardContent>
          <div className="stack gap-md">
            <Input aria-label="Specialty" placeholder="Specialty" />
            <Input aria-label="Preferred date" type="date" />
            <Button className="primary-cta">Submit scheduling request</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}