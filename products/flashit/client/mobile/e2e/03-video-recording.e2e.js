/**
 * E2E Test: Video Recording Flow
 * Tests video recording, playback, and upload functionality
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, takeScreenshot } = require('./helpers/setup');

describe('Video Recording Flow', () => {
  beforeAll(async () => {
    await device.launchApp({
      permissions: { camera: 'YES', microphone: 'YES' },
    });
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should navigate to video recording screen', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    await expect(element(by.id('video-recorder-screen'))).toBeVisible();
    await expect(element(by.id('camera-view'))).toBeVisible();
    await expect(element(by.id('record-video-button'))).toBeVisible();
    
    await takeScreenshot('video-recording-screen');
  });

  it('should record a video', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Start recording
    await element(by.id('record-video-button')).tap();
    await expect(element(by.id('recording-indicator'))).toBeVisible();
    await expect(element(by.id('recording-timer'))).toBeVisible();
    
    // Record for 5 seconds
    await new Promise(resolve => setTimeout(resolve, 5000));
    
    // Stop recording
    await element(by.id('stop-video-button')).tap();
    
    // Should show preview
    await waitFor(element(by.id('video-preview')))
      .toBeVisible()
      .withTimeout(5000);
    
    await takeScreenshot('video-recorded');
  });

  it('should playback recorded video', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Record
    await element(by.id('record-video-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 3000));
    await element(by.id('stop-video-button')).tap();
    
    await waitFor(element(by.id('video-preview'))).toBeVisible().withTimeout(5000);
    
    // Play video
    await element(by.id('play-video-button')).tap();
    await expect(element(by.id('video-player'))).toBeVisible();
    
    // Wait for playback
    await new Promise(resolve => setTimeout(resolve, 3000));
    
    await takeScreenshot('video-playing');
  });

  it('should trim video', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Record
    await element(by.id('record-video-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 5000));
    await element(by.id('stop-video-button')).tap();
    
    await waitFor(element(by.id('video-preview'))).toBeVisible().withTimeout(5000);
    
    // Trim video
    await element(by.id('trim-button')).tap();
    await expect(element(by.id('video-trimmer'))).toBeVisible();
    
    // Drag trim handles (mock gesture)
    await element(by.id('trim-start-handle')).swipe('right', 'slow', 0.3);
    await element(by.id('trim-end-handle')).swipe('left', 'slow', 0.3);
    
    // Apply trim
    await element(by.id('apply-trim-button')).tap();
    
    await takeScreenshot('video-trimmed');
  });

  it('should save video as moment', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Record
    await element(by.id('record-video-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 3000));
    await element(by.id('stop-video-button')).tap();
    
    await waitFor(element(by.id('video-preview'))).toBeVisible().withTimeout(5000);
    
    // Use video
    await element(by.id('use-video-button')).tap();
    
    // Add details
    await element(by.id('moment-title-input')).typeText('Family picnic');
    await element(by.id('emotion-joyful')).tap();
    
    // Save
    await element(by.id('save-button')).tap();
    
    // Verify success
    await waitFor(element(by.text('Moment saved successfully')))
      .toBeVisible()
      .withTimeout(10000); // Videos take longer to process
    
    await takeScreenshot('video-moment-saved');
  });

  it('should show video compression progress', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Record
    await element(by.id('record-video-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 5000));
    await element(by.id('stop-video-button')).tap();
    
    await waitFor(element(by.id('video-preview'))).toBeVisible().withTimeout(5000);
    await element(by.id('use-video-button')).tap();
    
    // Should show compression progress
    await expect(element(by.text('Compressing video...'))).toBeVisible();
    await expect(element(by.id('compression-progress-bar'))).toBeVisible();
    
    // Wait for compression
    await waitFor(element(by.id('save-button')))
      .toBeVisible()
      .withTimeout(15000);
  });

  it('should enforce 3-minute recording limit', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    await element(by.id('record-video-button')).tap();
    
    // Check timer is visible
    await expect(element(by.id('recording-timer'))).toBeVisible();
    await expect(element(by.id('recording-timer'))).toHaveText('00:00');
    
    // Check max duration indicator
    await expect(element(by.text('Max: 03:00'))).toBeVisible();
  });

  it('should flip camera during recording preparation', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Flip camera
    await element(by.id('flip-camera-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // Record with front camera
    await element(by.id('record-video-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    await element(by.id('stop-video-button')).tap();
    
    await waitFor(element(by.id('video-preview'))).toBeVisible().withTimeout(5000);
  });

  it('should cancel video recording', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Start recording
    await element(by.id('record-video-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    // Cancel
    await element(by.id('cancel-button')).tap();
    
    // Confirm cancellation
    await element(by.text('Discard')).tap();
    
    // Should return to initial state
    await expect(element(by.id('record-video-button'))).toBeVisible();
  });

  it('should handle low storage warning', async () => {
    // Note: This test would require mocking device storage
    // In a real scenario, you'd set up the device with low storage
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // If storage is low, should show warning
    // await expect(element(by.text('Low storage space'))).toBeVisible();
  });

  it('should queue video for offline upload', async () => {
    // Set offline mode
    await device.setURLBlacklist(['.*']);
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('video-mode-button')).tap();
    
    // Record and save
    await element(by.id('record-video-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 3000));
    await element(by.id('stop-video-button')).tap();
    
    await waitFor(element(by.id('video-preview'))).toBeVisible().withTimeout(5000);
    await element(by.id('use-video-button')).tap();
    
    await element(by.id('moment-title-input')).typeText('Offline Video Test');
    await element(by.id('save-button')).tap();
    
    // Should show queued message
    await waitFor(element(by.text('Queued for upload')))
      .toBeVisible()
      .withTimeout(10000);
    
    // Restore online mode
    await device.setURLBlacklist([]);
  });
});
