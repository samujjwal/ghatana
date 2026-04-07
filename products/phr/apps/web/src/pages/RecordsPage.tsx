import React from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router';
import { demoDashboard } from '../mockData';

export function RecordsPage(): React.ReactElement {
  return (
    <Card>
      <CardHeader title="Patient records" subheader="All record types are accessible through the portal" />
      <CardContent>
        <div className="stack gap-md">
          {demoDashboard.records.map((record) => (
            <Link key={record.id} className="data-card" to={`/records/${record.id}`}>
              <div>
                <strong>{record.title}</strong>
                <p className="muted">{record.resourceType} · Updated {new Date(record.updatedAt).toLocaleString()}</p>
              </div>
              <span className="pill">{record.category}</span>
            </Link>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}