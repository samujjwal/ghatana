import { mobileDashboard } from '../data/mockData';

describe('PHR mobile app contract', () => {
  it('ships the demo dashboard needed by the mobile shell', () => {
    expect(mobileDashboard.patient.name).toBe('Aarati Shrestha');
    expect(mobileDashboard.records.length).toBeGreaterThan(0);
    expect(mobileDashboard.consents.length).toBeGreaterThan(0);
    expect(mobileDashboard.notifications.length).toBeGreaterThan(0);
  });
});
