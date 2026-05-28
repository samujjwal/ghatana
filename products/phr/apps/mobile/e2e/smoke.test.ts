import { by, device, element, expect as detoxExpect } from 'detox';

const nationalId = process.env.PHR_MOBILE_E2E_NATIONAL_ID ?? 'PHR_E2E_PATIENT';
const password = process.env.PHR_MOBILE_E2E_PASSWORD ?? 'phr-e2e-password';

describe('PHR Mobile E2E Smoke Test', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  afterAll(async () => {
    await device.terminateApp();
  });

  it('completes login, dashboard, records, and logout', async () => {
    await element(by.label('National ID')).replaceText(nationalId);
    await element(by.label('Password')).replaceText(password);
    await element(by.label('Sign In')).tap();

    await detoxExpect(element(by.label('Home'))).toBeVisible();
    await detoxExpect(element(by.text('PHR Nepal'))).toBeVisible();

    await element(by.label('Records')).tap();
    await detoxExpect(element(by.label('Records'))).toBeVisible();

    await element(by.label('Settings')).tap();
    await element(by.label('Sign Out')).tap();
    await element(by.text('Sign Out')).tap();

    await detoxExpect(element(by.label('National ID'))).toBeVisible();
  });

  it('announces offline state while preserving the app shell', async () => {
    await device.setURLBlacklist(['.*']);
    await device.reloadReactNative();

    await detoxExpect(element(by.text('You are offline. Some features may be unavailable.'))).toBeVisible();

    await device.setURLBlacklist([]);
    await device.reloadReactNative();
  });
});
