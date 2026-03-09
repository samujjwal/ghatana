/**
 * E2E Test: Image Capture Flow
 * Tests camera capture and image upload functionality
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, takeScreenshot } = require('./helpers/setup');

describe('Image Capture Flow', () => {
  beforeAll(async () => {
    await device.launchApp({
      permissions: { camera: 'YES', photos: 'YES' },
    });
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should navigate to image capture screen', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    await expect(element(by.id('image-capture-screen'))).toBeVisible();
    await expect(element(by.id('camera-view'))).toBeVisible();
    await expect(element(by.id('capture-photo-button'))).toBeVisible();
    
    await takeScreenshot('image-capture-screen');
  });

  it('should flip camera (front/back)', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Flip to front camera
    await element(by.id('flip-camera-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // Flip back to rear camera
    await element(by.id('flip-camera-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    await takeScreenshot('camera-flipped');
  });

  it('should capture photo from camera', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Capture photo
    await element(by.id('capture-photo-button')).tap();
    
    // Should show preview
    await waitFor(element(by.id('image-preview')))
      .toBeVisible()
      .withTimeout(3000);
    
    await expect(element(by.id('retake-button'))).toBeVisible();
    await expect(element(by.id('use-photo-button'))).toBeVisible();
    
    await takeScreenshot('photo-captured');
  });

  it('should select photo from gallery', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Open gallery
    await element(by.id('gallery-button')).tap();
    
    // Select first photo (mock)
    // Note: In real tests, you'd need to mock the image picker
    // or have test images in the simulator
    
    await takeScreenshot('gallery-opened');
  });

  it('should crop and rotate image', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Capture photo
    await element(by.id('capture-photo-button')).tap();
    await waitFor(element(by.id('image-preview'))).toBeVisible().withTimeout(3000);
    
    // Edit image
    await element(by.id('edit-button')).tap();
    await expect(element(by.id('image-editor'))).toBeVisible();
    
    // Rotate
    await element(by.id('rotate-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 500));
    
    // Crop
    await element(by.id('crop-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 500));
    
    // Apply
    await element(by.id('apply-edits-button')).tap();
    
    await takeScreenshot('image-edited');
  });

  it('should save image as moment', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Capture photo
    await element(by.id('capture-photo-button')).tap();
    await waitFor(element(by.id('image-preview'))).toBeVisible().withTimeout(3000);
    
    // Use photo
    await element(by.id('use-photo-button')).tap();
    
    // Add caption
    await element(by.id('moment-title-input')).typeText('Beautiful sunset');
    
    // Add tags
    await element(by.id('tag-input')).typeText('nature');
    await element(by.id('add-tag-button')).tap();
    
    // Save
    await element(by.id('save-button')).tap();
    
    // Verify success
    await waitFor(element(by.text('Moment saved successfully')))
      .toBeVisible()
      .withTimeout(5000);
    
    await takeScreenshot('image-moment-saved');
  });

  it('should show image optimization progress', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Capture photo
    await element(by.id('capture-photo-button')).tap();
    await waitFor(element(by.id('image-preview'))).toBeVisible().withTimeout(3000);
    await element(by.id('use-photo-button')).tap();
    
    // Should show optimization progress
    await expect(element(by.text('Optimizing image...'))).toBeVisible();
    
    // Wait for optimization to complete
    await waitFor(element(by.id('save-button')))
      .toBeVisible()
      .withTimeout(5000);
  });

  it('should handle camera permission denial', async () => {
    await device.launchApp({
      permissions: { camera: 'NO' },
      delete: true,
    });
    await loginUser();
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Should show permission error
    await expect(element(by.text('Camera permission required'))).toBeVisible();
    await expect(element(by.id('grant-permission-button'))).toBeVisible();
  });

  it('should retake photo', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Capture photo
    await element(by.id('capture-photo-button')).tap();
    await waitFor(element(by.id('image-preview'))).toBeVisible().withTimeout(3000);
    
    // Retake
    await element(by.id('retake-button')).tap();
    
    // Should return to camera view
    await expect(element(by.id('camera-view'))).toBeVisible();
    await expect(element(by.id('capture-photo-button'))).toBeVisible();
  });

  it('should support multiple image selection from gallery', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Open gallery in multi-select mode
    await element(by.id('gallery-button')).longPress();
    
    // Should show multi-select UI
    await expect(element(by.text('Select up to 5 photos'))).toBeVisible();
    
    await takeScreenshot('gallery-multi-select');
  });

  it('should queue image for offline upload', async () => {
    // Set offline mode
    await device.setURLBlacklist(['.*']);
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('image-mode-button')).tap();
    
    // Capture and save
    await element(by.id('capture-photo-button')).tap();
    await waitFor(element(by.id('image-preview'))).toBeVisible().withTimeout(3000);
    await element(by.id('use-photo-button')).tap();
    await element(by.id('moment-title-input')).typeText('Offline Image Test');
    await element(by.id('save-button')).tap();
    
    // Should show queued message
    await waitFor(element(by.text('Queued for upload')))
      .toBeVisible()
      .withTimeout(5000);
    
    // Restore online mode
    await device.setURLBlacklist([]);
  });
});
