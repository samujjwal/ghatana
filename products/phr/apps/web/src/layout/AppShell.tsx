import React from 'react';
import { Button } from '@ghatana/design-system';
import { NavLink, Outlet } from 'react-router';

const links = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/records', label: 'Records' },
  { to: '/consents', label: 'Consents' },
  { to: '/appointments', label: 'Appointments' },
  { to: '/labs', label: 'Labs' },
  { to: '/medications', label: 'Medications' },
  { to: '/emergency', label: 'Emergency' },
  { to: '/settings', label: 'Settings' },
];

export function AppShell(): React.ReactElement {
  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div>
          <p className="eyebrow">PHR Nepal</p>
          <h1>Patient Portal</h1>
          <p className="muted">FHIR-native health records, consent, and emergency workflows.</p>
        </div>
        <nav className="nav-list" aria-label="Primary navigation">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <Button className="sidebar-button">Emergency Access Review</Button>
        </div>
      </aside>
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  );
}