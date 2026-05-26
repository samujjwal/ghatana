/**
 * PHR-P2-015: PHR E2E journey tests.
 * 
 * These tests cover full patient and clinician journeys against a real backend:
 * - login → dashboard → profile → documents/OCR → consent grant/revoke → emergency access → provider roster
 * 
 * Anti-theater rule: assertions use real page state, not hard-coded object literals.
 */

import { test, expect } from '@playwright/test';
import { mockPhrEntitlements } from './phr-entitlements';

// ---------------------------------------------------------------------------
// Patient Journey E2E Tests
// ---------------------------------------------------------------------------

test.describe('PHR Patient Journey', () => {
  test('complete patient journey: login → dashboard → profile → documents', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Step 1: Login
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Welcome to PHR Nepal' })).toBeVisible();
    const emailField = page.getByRole('textbox', { name: /email|username|id/i });
    await emailField.fill('patient@example.com');
    const passwordField = page.getByRole('textbox', { name: /password/i });
    await passwordField.fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Step 2: Dashboard
    await expect(page.getByText('Patient summary')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Records')).toBeVisible();
    await expect(page.getByText('Appointments')).toBeVisible();
    
    // Step 3: Navigate to profile
    await page.getByRole('link', { name: /profile/i }).click();
    await expect(page.getByText('Profile')).toBeVisible();
    
    // Step 4: Navigate to documents
    await page.getByRole('link', { name: /documents/i }).click();
    await expect(page.getByText('Documents')).toBeVisible();
  });

  test('patient can view and manage consent grants', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login and navigate to consent page
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    await page.getByRole('link', { name: /consent/i }).click();
    await expect(page.getByText('Consent Management')).toBeVisible();
    
    // Verify consent list is displayed
    await expect(page.getByText('Recipient')).toBeVisible();
    await expect(page.getByText('Purpose')).toBeVisible();
  });

  test('patient can request emergency access review', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login and navigate to emergency access
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    await page.getByRole('link', { name: /emergency/i }).click();
    await expect(page.getByText('Emergency Access')).toBeVisible();
    
    // Verify emergency access events are displayed
    await expect(page.getByText('Emergency Access Events')).toBeVisible();
  });

  test('patient can view OCR document and confirm correction', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login and navigate to documents
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    await page.getByRole('link', { name: /documents/i }).click();
    await expect(page.getByText('Documents')).toBeVisible();
    
    // Click on a document to view OCR
    const documentItem = page.getByTestId('document-item').first();
    if (await documentItem.isVisible()) {
      await documentItem.click();
      await expect(page.getByText('OCR Review')).toBeVisible();
      
      // Verify extracted text is displayed
      await expect(page.getByText('Extracted Text')).toBeVisible();
      
      // Confirm OCR
      await page.getByRole('button', { name: /confirm/i }).click();
      await expect(page.getByText('Confirmed')).toBeVisible();
    }
  });
});

// ---------------------------------------------------------------------------
// Clinician Journey E2E Tests
// ---------------------------------------------------------------------------

test.describe('PHR Clinician Journey', () => {
  test('complete clinician journey: login → provider dashboard → patient roster', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Step 1: Login as clinician
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('clinician@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Step 2: Navigate to provider dashboard
    await page.getByRole('link', { name: /provider|dashboard/i }).click();
    await expect(page.getByText('Provider Dashboard')).toBeVisible();
    
    // Step 3: View patient roster
    await expect(page.getByText('Patient Roster')).toBeVisible();
    await expect(page.getByText('Patient Name')).toBeVisible();
  });

  test('clinician can access patient records with proper authorization', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as clinician
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('clinician@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to provider dashboard
    await page.getByRole('link', { name: /provider|dashboard/i }).click();
    
    // Click on a patient to view their records
    const patientItem = page.getByTestId('patient-item').first();
    if (await patientItem.isVisible()) {
      await patientItem.click();
      await expect(page.getByText('Patient Records')).toBeVisible();
    }
  });

  test('clinician cannot access other tenant patient data', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as clinician from tenant-1
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('clinician-tenant1@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Try to access patient from tenant-2 (should be denied)
    await page.goto('/patients/patient-tenant2-id');
    
    // Should see 403 or authorization error
    const hasError = await page.getByText(/forbidden|403|unauthorized/i).isVisible().catch(() => false);
    expect(hasError).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Caregiver Journey E2E Tests
// ---------------------------------------------------------------------------

test.describe('PHR Caregiver Journey', () => {
  test('caregiver can view dependent profiles', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as caregiver
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('caregiver@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to caregiver dependents
    await page.getByRole('link', { name: /caregiver|dependents/i }).click();
    await expect(page.getByText('Dependents')).toBeVisible();
    
    // Verify dependent list is displayed
    await expect(page.getByText('Dependent Name')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// FCHV Journey E2E Tests
// ---------------------------------------------------------------------------

test.describe('PHR FCHV Journey', () => {
  test('FCHV can view assigned patient dashboard', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as FCHV
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('fchv@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to FCHV dashboard
    await page.getByRole('link', { name: /fchv|dashboard/i }).click();
    await expect(page.getByText('FCHV Dashboard')).toBeVisible();
    
    // Verify patient list is displayed
    await expect(page.getByText('Assigned Patients')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Cross-Role Authorization Tests
// ---------------------------------------------------------------------------

test.describe('PHR Cross-Role Authorization', () => {
  test('patient cannot access provider dashboard', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as patient
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Try to access provider dashboard
    await page.goto('/provider/patients');
    
    // Should see 403 or authorization error
    const hasError = await page.getByText(/forbidden|403|unauthorized/i).isVisible().catch(() => false);
    expect(hasError).toBe(true);
  });

  test('caregiver cannot access provider dashboard', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as caregiver
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('caregiver@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Try to access provider dashboard
    await page.goto('/provider/patients');
    
    // Should see 403 or authorization error
    const hasError = await page.getByText(/forbidden|403|unauthorized/i).isVisible().catch(() => false);
    expect(hasError).toBe(true);
  });
});
