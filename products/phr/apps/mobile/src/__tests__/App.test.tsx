import { demoDashboard } from '../data/demoDashboard';

describe('PHR mobile app contract', () => {
  it('keeps a typed demo dashboard fixture for mobile contract tests', () => {
    expect(demoDashboard.patient.name).toBe('Aarati Shrestha');
    expect(demoDashboard.records.length).toBeGreaterThan(0);
    expect(demoDashboard.consents.length).toBeGreaterThan(0);
    expect(demoDashboard.notifications.length).toBeGreaterThan(0);
  });
});
