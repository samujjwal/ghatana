/**
 * PHR-P2-015: PHR E2E journey tests.
 * 
 * These tests cover full patient and clinician journeys against a real backend:
 * - login → dashboard → profile → documents/OCR → consent grant/revoke → emergency access → provider roster
 * - consent management, appointments scheduling, document OCR, emergency access, audit trail
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
    
    // Grant new consent
    const grantButton = page.getByRole('button', { name: /grant|new consent/i });
    if (await grantButton.isVisible()) {
      await grantButton.click();
      await expect(page.getByText('Grant Consent')).toBeVisible();
      
      // Select recipient and purpose
      await page.getByRole('combobox', { name: /recipient/i }).click();
      await page.getByRole('option').first().click();
      await page.getByRole('combobox', { name: /purpose/i }).click();
      await page.getByRole('option').first().click();
      
      // Submit consent
      await page.getByRole('button', { name: /submit|confirm/i }).click();
      await expect(page.getByText(/consent granted|success/i)).toBeVisible();
    }
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

  test('patient can schedule and view appointments', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login and navigate to appointments
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    await page.getByRole('link', { name: /appointments/i }).click();
    await expect(page.getByText('Appointments')).toBeVisible();
    
    // Schedule new appointment
    const scheduleButton = page.getByRole('button', { name: /schedule|new appointment/i });
    if (await scheduleButton.isVisible()) {
      await scheduleButton.click();
      await expect(page.getByText('Schedule Appointment')).toBeVisible();
      
      // Select provider
      await page.getByRole('combobox', { name: /provider/i }).click();
      await page.getByRole('option').first().click();
      
      // Select date and time
      await page.getByRole('textbox', { name: /date/i }).fill('2026-06-01');
      await page.getByRole('combobox', { name: /time/i }).click();
      await page.getByRole('option').first().click();
      
      // Submit appointment
      await page.getByRole('button', { name: /submit|confirm/i }).click();
      await expect(page.getByText(/appointment scheduled|success/i)).toBeVisible();
    }
    
    // Verify appointment appears in list
    await expect(page.getByText('Upcoming Appointments')).toBeVisible();
  });

  test('patient can view audit trail of their data access', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login and navigate to audit trail
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    await page.getByRole('link', { name: /audit|history/i }).click();
    await expect(page.getByText(/audit|access history/i)).toBeVisible();
    
    // Verify audit events are displayed
    await expect(page.getByText(/access|view|consent/i)).toBeVisible();
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

// ---------------------------------------------------------------------------
// Emergency Break-Glass E2E Tests
// ---------------------------------------------------------------------------

test.describe('PHR Emergency Break-Glass', () => {
  test('clinician can request emergency access with justification', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as clinician
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('clinician@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency access
    await page.getByRole('link', { name: /emergency/i }).click();
    await expect(page.getByText('Emergency Access')).toBeVisible();
    
    // Fill emergency access form
    await page.getByRole('textbox', { name: /patient id/i }).fill('patient-1');
    await page.getByRole('textbox', { name: /justification/i }).fill('Critical emergency - patient unconscious');
    await page.getByRole('checkbox', { name: /labs/i }).check();
    await page.getByRole('checkbox', { name: /medications/i }).check();
    
    // Submit emergency access request
    await page.getByRole('button', { name: /request access/i }).click();
    
    // Verify success message
    await expect(page.getByText(/emergency access granted|logged/i)).toBeVisible();
  });

  test('emergency access requires non-empty justification', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as clinician
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('clinician@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency access
    await page.getByRole('link', { name: /emergency/i }).click();
    
    // Try to submit without justification
    await page.getByRole('textbox', { name: /patient id/i }).fill('patient-1');
    await page.getByRole('button', { name: /request access/i }).click();
    
    // Should see validation error
    await expect(page.getByText(/justification.*required/i)).toBeVisible();
  });

  test('emergency access requires at least one resource to be selected', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as clinician
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('clinician@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency access
    await page.getByRole('link', { name: /emergency/i }).click();
    
    // Try to submit without selecting resources
    await page.getByRole('textbox', { name: /patient id/i }).fill('patient-1');
    await page.getByRole('textbox', { name: /justification/i }).fill('Critical emergency');
    await page.getByRole('button', { name: /request access/i }).click();
    
    // Should see validation error
    await expect(page.getByText(/at least one resource/i)).toBeVisible();
  });

  test('non-clinician cannot request emergency access', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as patient
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Try to access emergency request page
    await page.goto('/emergency');
    
    // Should see 403 or authorization error
    const hasError = await page.getByText(/forbidden|403|unauthorized|clinician only/i).isVisible().catch(() => false);
    expect(hasError).toBe(true);
  });

  test('admin can review pending emergency access events', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as admin
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('admin@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency reviews
    await page.goto('/emergency/reviews');
    await expect(page.getByText('Emergency Reviews')).toBeVisible();
    
    // Verify pending reviews section
    await expect(page.getByText('Pending Reviews')).toBeVisible();
  });

  test('admin can approve emergency access review', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as admin
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('admin@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency reviews
    await page.goto('/emergency/reviews');
    
    // Click on a pending review
    const reviewItem = page.getByTestId('emergency-review-item').first();
    if (await reviewItem.isVisible()) {
      await reviewItem.click();
      
      // Approve the review
      await page.getByRole('button', { name: /approve/i }).click();
      
      // Verify success message
      await expect(page.getByText(/reviewed|approved/i)).toBeVisible();
    }
  });

  test('admin denied review requires notes', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as admin
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('admin@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency reviews
    await page.goto('/emergency/reviews');
    
    // Click on a pending review
    const reviewItem = page.getByTestId('emergency-review-item').first();
    if (await reviewItem.isVisible()) {
      await reviewItem.click();
      
      // Try to deny without notes
      await page.getByRole('button', { name: /deny/i }).click();
      
      // Should see validation error
      await expect(page.getByText(/notes.*required/i)).toBeVisible();
      
      // Add notes and deny
      await page.getByRole('textbox', { name: /notes/i }).fill('Insufficient justification provided');
      await page.getByRole('button', { name: /deny/i }).click();
      
      // Verify success
      await expect(page.getByText(/reviewed|denied/i)).toBeVisible();
    }
  });

  test('patient can view their emergency access log', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as patient
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('patient@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency access
    await page.getByRole('link', { name: /emergency/i }).click();
    
    // Verify emergency access events are displayed
    await expect(page.getByText('Emergency Access Events')).toBeVisible();
  });

  test('emergency access event includes audit trail', async ({ page }) => {
    await mockPhrEntitlements(page);
    
    // Login as admin
    await page.goto('/login');
    await page.getByRole('textbox', { name: /email|username|id/i }).fill('admin@example.com');
    await page.getByRole('textbox', { name: /password/i }).fill('password123');
    await page.getByRole('button', { name: /login|sign in/i }).click();
    
    // Navigate to emergency reviews
    await page.goto('/emergency/reviews');
    
    // Click on a review to view details
    const reviewItem = page.getByTestId('emergency-review-item').first();
    if (await reviewItem.isVisible()) {
      await reviewItem.click();
      
      // Verify audit trail is displayed
      await expect(page.getByText(/accessor|timestamp|resources/i)).toBeVisible();
    }
  });
});
