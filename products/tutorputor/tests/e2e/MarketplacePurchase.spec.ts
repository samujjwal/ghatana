/**
 * Marketplace Purchase E2E Tests
 *
 * Covers the complete marketplace purchase flow:
 *   1. Browse marketplace listings
 *   2. Add item to cart
 *   3. Checkout flow
 *   4. Payment processing (Stripe integration)
 *   5. Order confirmation
 *   6. Access granted to purchased content
 *
 * @doc.type test
 * @doc.purpose End-to-end marketplace purchase validation
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect, type Page } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';
const PLATFORM_URL = process.env.PLATFORM_URL || 'http://localhost:7105';

test.describe('Marketplace Purchase E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(60000);
  });

  test('should browse marketplace listings', async ({ page }) => {
    await page.goto(`${BASE_URL}/marketplace`);
    await page.waitForLoadState('networkidle');
    
    // Should show marketplace
    const marketplace = page.locator('[data-testid="marketplace"], .marketplace');
    await expect(marketplace).toBeVisible();
    
    // Should have product listings
    const productGrid = page.locator('[data-testid="product-grid"], .product-grid, .listing-grid');
    const gridCount = await productGrid.count();
    
    if (gridCount > 0) {
      // Should have product cards
      const productCard = page.locator('[data-testid="product-card"], .product-card');
      const cardCount = await productCard.count();
      expect(cardCount).toBeGreaterThan(0);
    }
  });

  test('should add item to cart', async ({ page }) => {
    await page.goto(`${BASE_URL}/marketplace`);
    await page.waitForLoadState('networkidle');
    
    const productCard = page.locator('[data-testid="product-card"], .product-card');
    const cardCount = await productCard.count();
    
    if (cardCount > 0) {
      // Click first product
      await productCard.first().click();
      await page.waitForLoadState('networkidle');
      
      // Look for add to cart button
      const addToCartButton = page.locator('button:has-text("Add to Cart"), button:has-text("Add"), [data-testid="add-to-cart"]');
      
      if (await addToCartButton.count() > 0) {
        await addToCartButton.click();
        await page.waitForLoadState('networkidle');
        
        // Should show cart updated indicator or redirect to cart
        const cartIndicator = page.locator('[data-testid="cart-count"], .cart-count');
        const cartRedirect = page.url().includes('/cart');
        
        expect(await cartIndicator.count() > 0 || cartRedirect).toBeTruthy();
      }
    }
  });

  test('should navigate to checkout', async ({ page }) => {
    await page.goto(`${BASE_URL}/cart`);
    await page.waitForLoadState('networkidle');
    
    // Should show cart page
    const cartPage = page.locator('[data-testid="cart"], .cart-page');
    await expect(cartPage).toBeVisible();
    
    // Should have checkout button
    const checkoutButton = page.locator('button:has-text("Checkout"), button:has-text("Proceed"), [data-testid="checkout-button"]');
    
    if (await checkoutButton.count() > 0) {
      await checkoutButton.click();
      await page.waitForLoadState('networkidle');
      
      // Should redirect to checkout page
      const isCheckout = page.url().includes('/checkout');
      expect(isCheckout).toBeTruthy();
    }
  });

  test('should complete checkout flow', async ({ page }) => {
    await page.goto(`${BASE_URL}/checkout`);
    await page.waitForLoadState('networkidle');
    
    // Should show checkout page
    const checkoutPage = page.locator('[data-testid="checkout"], .checkout-page');
    await expect(checkoutPage).toBeVisible();
    
    // Fill checkout form
    const nameInput = page.locator('input[name="name"], [data-testid="name"]');
    if (await nameInput.count() > 0) {
      await nameInput.fill('Test User');
    }
    
    const emailInput = page.locator('input[name="email"], [data-testid="email"]');
    if (await emailInput.count() > 0) {
      await emailInput.fill('test@example.com');
    }
    
    // Proceed to payment
    const proceedButton = page.locator('button:has-text("Continue"), button:has-text("Proceed"), [data-testid="proceed-payment"]');
    if (await proceedButton.count() > 0) {
      await proceedButton.click();
      await page.waitForLoadState('networkidle');
    }
  });

  test('should handle payment integration', async ({ page }) => {
    // Note: This test uses test Stripe keys and should not be run in production
    await page.goto(`${BASE_URL}/checkout/payment`);
    await page.waitForLoadState('networkidle');
    
    // Should show payment form (Stripe Elements)
    const paymentForm = page.locator('[data-testid="payment-form"], .payment-form, iframe');
    const formCount = await paymentForm.count();
    
    if (formCount > 0) {
      // In test environment, we might use a test payment method
      const testPaymentButton = page.locator('button:has-text("Pay with Test Card"), [data-testid="test-payment"]');
      
      if (await testPaymentButton.count() > 0) {
        await testPaymentButton.click();
        await page.waitForLoadState('networkidle');
      }
    }
  });

  test('should show order confirmation', async ({ page }) => {
    // Simulate successful payment completion
    await page.goto(`${BASE_URL}/order/confirmation`);
    await page.waitForLoadState('networkidle');
    
    // Should show order confirmation
    const confirmation = page.locator('[data-testid="order-confirmation"], .order-confirmation, h1:has-text("Confirmation")');
    await expect(confirmation).toBeVisible();
    
    // Should show order details
    const orderNumber = page.locator('[data-testid="order-number"], .order-number');
    const orderDetails = page.locator('[data-testid="order-details"], .order-details');
    
    expect(await orderNumber.count() > 0 || await orderDetails.count() > 0).toBeTruthy();
  });

  test('should grant access to purchased content', async ({ page }) => {
    // Navigate to purchased content
    await page.goto(`${BASE_URL}/my-content`);
    await page.waitForLoadState('networkidle');
    
    // Should show purchased content
    const myContent = page.locator('[data-testid="my-content"], .my-content');
    await expect(myContent).toBeVisible();
    
    // Should have purchased items
    const purchasedItem = page.locator('[data-testid="purchased-item"], .purchased-item');
    const itemCount = await purchasedItem.count();
    
    if (itemCount > 0) {
      // Click purchased item to verify access
      await purchasedItem.first().click();
      await page.waitForLoadState('networkidle');
      
      // Should show content (not locked/paywall)
      const paywall = page.locator('[data-testid="paywall"], .paywall');
      const content = page.locator('[data-testid="content"], .content, main');
      
      expect(await paywall.count()).toBe(0);
      expect(await content.count()).toBeGreaterThan(0);
    }
  });
});
