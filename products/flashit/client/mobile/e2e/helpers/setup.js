/**
 * E2E Test Setup Helpers
 * Common setup and teardown functions for all E2E tests
 */

const { device } = require('detox');

/**
 * Login helper for authenticated flows
 */
export async function loginUser(email = 'test@flashit.app', password = 'Test1234!') {
  await element(by.id('email-input')).typeText(email);
  await element(by.id('password-input')).typeText(password);
  await element(by.id('login-button')).tap();
  await waitFor(element(by.id('home-screen')))
    .toBeVisible()
    .withTimeout(5000);
}

/**
 * Logout helper
 */
export async function logoutUser() {
  await element(by.id('settings-tab')).tap();
  await element(by.id('logout-button')).tap();
  await element(by.text('Logout')).tap(); // Confirm dialog
  await waitFor(element(by.id('login-screen')))
    .toBeVisible()
    .withTimeout(3000);
}

/**
 * Grant permissions helper
 */
export async function grantPermissions() {
  if (device.getPlatform() === 'ios') {
    await device.takeScreenshot('before-permissions');
  }
  // Microphone permission
  await device.launchApp({ permissions: { microphone: 'YES' } });
  // Camera permission
  await device.launchApp({ permissions: { camera: 'YES' } });
  // Photo library permission
  await device.launchApp({ permissions: { photos: 'YES' } });
}

/**
 * Clear app data
 */
export async function clearAppData() {
  await device.clearKeychain();
  // Clear AsyncStorage
  await device.sendToHome();
  await device.launchApp({ delete: true });
}

/**
 * Wait for element with custom timeout
 */
export async function waitForElement(elementMatcher, timeout = 5000) {
  await waitFor(element(elementMatcher))
    .toBeVisible()
    .withTimeout(timeout);
}

/**
 * Scroll to element
 */
export async function scrollToElement(scrollViewId, elementMatcher) {
  await waitFor(element(elementMatcher))
    .toBeVisible()
    .whileElement(by.id(scrollViewId))
    .scroll(200, 'down');
}

/**
 * Take screenshot with timestamp
 */
export async function takeScreenshot(name) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  await device.takeScreenshot(`${name}-${timestamp}`);
}

/**
 * Network condition helpers
 */
export async function setOfflineMode() {
  if (device.getPlatform() === 'android') {
    await device.setURLBlacklist(['.*']);
  }
}

export async function setOnlineMode() {
  if (device.getPlatform() === 'android') {
    await device.setURLBlacklist([]);
  }
}

/**
 * Wait for network request
 */
export async function waitForNetworkIdle(timeout = 3000) {
  await new Promise(resolve => setTimeout(resolve, timeout));
}
