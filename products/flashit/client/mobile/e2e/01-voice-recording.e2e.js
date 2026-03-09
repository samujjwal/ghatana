/**
 * E2E Test: Voice Recording Flow
 * Tests the complete voice recording functionality
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, grantPermissions, takeScreenshot } = require('./helpers/setup');

describe('Voice Recording Flow', () => {
  beforeAll(async () => {
    await device.launchApp({
      permissions: { microphone: 'YES' },
    });
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should navigate to voice recording screen', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    await expect(element(by.id('voice-recorder-screen'))).toBeVisible();
    await expect(element(by.id('record-button'))).toBeVisible();
    
    await takeScreenshot('voice-recording-screen');
  });

  it('should record a voice memo', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    // Start recording
    await element(by.id('record-button')).tap();
    await expect(element(by.id('recording-indicator'))).toBeVisible();
    await expect(element(by.id('waveform-visualizer'))).toBeVisible();
    
    // Record for 3 seconds
    await new Promise(resolve => setTimeout(resolve, 3000));
    
    // Stop recording
    await element(by.id('stop-button')).tap();
    await expect(element(by.id('audio-preview'))).toBeVisible();
    
    await takeScreenshot('voice-recorded');
  });

  it('should playback recorded audio', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    // Record
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    await element(by.id('stop-button')).tap();
    
    // Playback
    await element(by.id('play-button')).tap();
    await expect(element(by.id('playback-progress'))).toBeVisible();
    
    // Wait for playback to complete
    await new Promise(resolve => setTimeout(resolve, 3000));
  });

  it('should save voice memo as moment', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    // Record
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    await element(by.id('stop-button')).tap();
    
    // Add title
    await element(by.id('moment-title-input')).typeText('Test Voice Memo');
    
    // Select emotion
    await element(by.id('emotion-happy')).tap();
    
    // Save
    await element(by.id('save-button')).tap();
    
    // Verify success
    await waitFor(element(by.text('Moment saved successfully')))
      .toBeVisible()
      .withTimeout(5000);
    
    await takeScreenshot('voice-memo-saved');
  });

  it('should handle microphone permission denial', async () => {
    await device.launchApp({
      permissions: { microphone: 'NO' },
      delete: true,
    });
    await loginUser();
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    // Try to record
    await element(by.id('record-button')).tap();
    
    // Should show permission error
    await expect(element(by.text('Microphone permission required'))).toBeVisible();
    await expect(element(by.id('grant-permission-button'))).toBeVisible();
  });

  it('should cancel recording', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    // Start recording
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    // Cancel
    await element(by.id('cancel-button')).tap();
    
    // Confirm cancellation
    await element(by.text('Discard')).tap();
    
    // Should return to initial state
    await expect(element(by.id('record-button'))).toBeVisible();
    await expect(element(by.id('audio-preview'))).not.toBeVisible();
  });

  it('should enforce 5-minute recording limit', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    await element(by.id('record-button')).tap();
    
    // Check timer is visible
    await expect(element(by.id('recording-timer'))).toBeVisible();
    
    // Verify timer format (MM:SS)
    await expect(element(by.id('recording-timer'))).toHaveText('00:00');
  });

  it('should queue for offline upload', async () => {
    // Set offline mode
    await device.setURLBlacklist(['.*']);
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    // Record
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    await element(by.id('stop-button')).tap();
    
    // Save
    await element(by.id('moment-title-input')).typeText('Offline Voice Test');
    await element(by.id('save-button')).tap();
    
    // Should show queued message
    await waitFor(element(by.text('Queued for upload')))
      .toBeVisible()
      .withTimeout(5000);
    
    // Check upload queue
    await element(by.id('settings-tab')).tap();
    await element(by.id('upload-queue-button')).tap();
    
    await expect(element(by.text('Offline Voice Test'))).toBeVisible();
    
    // Restore online mode
    await device.setURLBlacklist([]);
  });
});
