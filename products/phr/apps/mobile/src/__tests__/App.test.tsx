import { mobileDashboardFixture } from './fixtures/mobileDashboardFixture';

describe('PHR mobile app contract', () => {
  it('keeps a typed dashboard fixture for mobile contract tests', () => {
    expect(mobileDashboardFixture.patient.name).toBe('Aarati Shrestha');
    expect(mobileDashboardFixture.records.length).toBeGreaterThan(0);
    expect(mobileDashboardFixture.consents.length).toBeGreaterThan(0);
    expect(mobileDashboardFixture.notifications.length).toBeGreaterThan(0);
  });
});
