/**
 * Global setup for Playwright tests
 * 
 * Prepares test environment, starts services, and validates dependencies
 */

import { chromium, FullConfig } from '@playwright/test';

async function globalSetup(config: FullConfig) {
  console.log('🚀 Starting E2E test setup...');
  
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();
  
  try {
    // Wait for web application to be ready
    const baseURL = config.webServer?.url || 'http://localhost:5173';
    console.log(`📱 Checking web app at ${baseURL}`);
    
    await page.goto(baseURL, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    
    // Check if page loaded successfully
    const title = await page.title();
    console.log(`✅ Web app loaded: ${title}`);
    
    // Check platform service health
    const platformURL = process.env.PLATFORM_URL || 'http://localhost:7105';
    console.log(`🔍 Checking platform service at ${platformURL}`);
    
    try {
      const response = await page.request.get(`${platformURL}/health`);
      if (response.status() === 200) {
        console.log('✅ Platform service is healthy');
      } else {
        console.log(`⚠️ Platform service returned status: ${response.status()}`);
      }
    } catch (error) {
      console.log('⚠️ Platform service not available, continuing...');
    }
    
    // Validate critical APIs are accessible
    const criticalEndpoints = [
      '/api/health',
      '/api/status',
      '/health',
    ];
    
    for (const endpoint of criticalEndpoints) {
      try {
        const response = await page.request.get(`${baseURL}${endpoint}`);
        if (response.status() < 500) {
          console.log(`✅ Endpoint ${endpoint} is accessible`);
        }
      } catch (error) {
        console.log(`⚠️ Endpoint ${endpoint} not accessible`);
      }
    }
    
    console.log('✅ E2E test setup completed successfully');
    
  } catch (error) {
    console.error('❌ E2E test setup failed:', error);
    throw error;
  } finally {
    await context.close();
    await browser.close();
  }
}

export default globalSetup;
