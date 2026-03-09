/**
 * E2E Test Suite: Collaboration Features
 * 
 * Comprehensive end-to-end tests for collaboration functionality covering:
 * - Real-time synchronization
 * - Concurrent editing
 * - Presence indicators
 * - Conflict resolution
 * - Comments and annotations
 */

import { test, expect, Browser, BrowserContext } from '@playwright/test';

const STORYBOOK_URL = 'http://localhost:6006';
const CANVAS_STORY = `${STORYBOOK_URL}/iframe.html?id=canvas--collaboration&viewMode=story`;

/**
 * Suite 1: Real-time Presence
 */
test.describe('Collaboration Real-time Presence', () => {
  test('should display user cursors', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const presenceIndicators = page.locator('[data-testid^="presence-cursor-"]');
    
    // Should have at least placeholder for current user
    await expect(presenceIndicators).toHaveCount(1);
  });

  test('should show active users list', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const activeUsers = page.locator('[data-testid="active-users-list"]');
    
    await expect(activeUsers).toBeVisible();
  });

  test('should track user color assignments', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const cursor = page.locator('[data-testid^="presence-cursor-"]').first();
    
    const color = await cursor.evaluate((el) => {
      return window.getComputedStyle(el).backgroundColor;
    });
    
    expect(color).toBeTruthy();
  });

  test('should move user cursor on mouse move', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Move mouse
    await canvas.hover({ position: { x: 100, y: 100 } });
    
    const cursor = page.locator('[data-testid^="presence-cursor-"]').first();
    const position = await cursor.boundingBox();
    
    expect(position).not.toBeNull();
  });

  test('should show user selections', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add and select node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    await node.click();
    
    // Selection should be visible
    const selection = node.locator('[data-testid="selection-indicator"]');
    await expect(selection).toBeVisible();
  });
});

/**
 * Suite 2: Concurrent Editing
 */
test.describe('Collaboration Concurrent Editing', () => {
  test('should handle simultaneous node creation', async ({ browser }) => {
    const context1 = await browser.newContext();
    const page1 = await context1.newPage();
    
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    
    try {
      await page1.goto(CANVAS_STORY);
      await page2.goto(CANVAS_STORY);
      
      const canvas1 = page1.locator('[data-testid="canvas-container"]');
      const canvas2 = page2.locator('[data-testid="canvas-container"]');
      
      // Both users add nodes simultaneously
      await Promise.all([
        canvas1.dblClick({ position: { x: 100, y: 100 } }),
        canvas2.dblClick({ position: { x: 300, y: 100 } })
      ]);
      
      // Wait for sync
      await page1.waitForTimeout(500);
      await page2.waitForTimeout(500);
      
      // Both should see 2 nodes
      const nodes1 = page1.locator('[data-testid^="node-"]');
      const nodes2 = page2.locator('[data-testid^="node-"]');
      
      await expect(nodes1).toHaveCount(2);
      await expect(nodes2).toHaveCount(2);
    } finally {
      await context1.close();
      await context2.close();
    }
  });

  test('should sync node deletion across users', async ({ browser }) => {
    const context1 = await browser.newContext();
    const page1 = await context1.newPage();
    
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    
    try {
      await page1.goto(CANVAS_STORY);
      await page2.goto(CANVAS_STORY);
      
      const canvas1 = page1.locator('[data-testid="canvas-container"]');
      const canvas2 = page2.locator('[data-testid="canvas-container"]');
      
      // User 1 creates node
      await canvas1.dblClick({ position: { x: 200, y: 200 } });
      
      // Wait for sync
      await page1.waitForTimeout(500);
      await page2.waitForTimeout(500);
      
      // User 2 sees node
      let nodes2 = page2.locator('[data-testid^="node-"]');
      await expect(nodes2).toHaveCount(1);
      
      // User 1 deletes node
      const node1 = page1.locator('[data-testid^="node-"]').first();
      await node1.click();
      await page1.keyboard.press('Delete');
      
      // Wait for sync
      await page1.waitForTimeout(500);
      await page2.waitForTimeout(500);
      
      // User 2 sees deletion
      nodes2 = page2.locator('[data-testid^="node-"]');
      await expect(nodes2).toHaveCount(0);
    } finally {
      await context1.close();
      await context2.close();
    }
  });

  test('should sync node movement', async ({ browser }) => {
    const context1 = await browser.newContext();
    const page1 = await context1.newPage();
    
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    
    try {
      await page1.goto(CANVAS_STORY);
      await page2.goto(CANVAS_STORY);
      
      const canvas1 = page1.locator('[data-testid="canvas-container"]');
      const canvas2 = page2.locator('[data-testid="canvas-container"]');
      
      // Add node on page1
      await canvas1.dblClick({ position: { x: 200, y: 200 } });
      
      // Wait for sync
      await page1.waitForTimeout(500);
      await page2.waitForTimeout(500);
      
      // Get initial positions
      const node1 = page1.locator('[data-testid^="node-"]').first();
      const node2 = page2.locator('[data-testid^="node-"]').first();
      
      const box1Before = await node1.boundingBox();
      const box2Before = await node2.boundingBox();
      
      // User 1 drags node
      await node1.drag({ x: 100, y: 100 });
      
      // Wait for sync
      await page1.waitForTimeout(500);
      await page2.waitForTimeout(500);
      
      // Check User 2 sees movement
      const box2After = await node2.boundingBox();
      
      expect(box2After?.x).toBeGreaterThan(box2Before?.x || 0);
      expect(box2After?.y).toBeGreaterThan(box2Before?.y || 0);
    } finally {
      await context1.close();
      await context2.close();
    }
  });

  test('should resolve conflicts gracefully', async ({ browser }) => {
    const context1 = await browser.newContext();
    const page1 = await context1.newPage();
    
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    
    try {
      await page1.goto(CANVAS_STORY);
      await page2.goto(CANVAS_STORY);
      
      const canvas1 = page1.locator('[data-testid="canvas-container"]');
      const canvas2 = page2.locator('[data-testid="canvas-container"]');
      
      // Add node visible to both
      await canvas1.dblClick({ position: { x: 200, y: 200 } });
      await page1.waitForTimeout(500);
      await page2.waitForTimeout(500);
      
      // Both users simultaneously edit the same node
      const node1 = page1.locator('[data-testid^="node-"]').first();
      const node2 = page2.locator('[data-testid^="node-"]').first();
      
      await node1.click();
      await node2.click();
      
      // Simulate conflict - both try to drag
      await Promise.all([
        node1.drag({ x: 50, y: 50 }),
        node2.drag({ x: -50, y: -50 })
      ]);
      
      await page1.waitForTimeout(500);
      await page2.waitForTimeout(500);
      
      // Should resolve to one consistent state
      const finalBox1 = await node1.boundingBox();
      const finalBox2 = await node2.boundingBox();
      
      expect(finalBox1?.x).toBe(finalBox2?.x);
      expect(finalBox1?.y).toBe(finalBox2?.y);
    } finally {
      await context1.close();
      await context2.close();
    }
  });
});

/**
 * Suite 3: Comments and Annotations
 */
test.describe('Collaboration Comments and Annotations', () => {
  test('should add comment to node', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    
    // Right-click for context menu
    await node.click({ button: 'right' });
    
    const addCommentOption = page.locator('[data-testid="context-add-comment"]');
    await addCommentOption.click();
    
    // Type comment
    const commentInput = page.locator('[data-testid="comment-input"]');
    await commentInput.fill('This is a comment');
    await commentInput.press('Enter');
    
    // Verify comment added
    const comments = page.locator('[data-testid^="comment-"]');
    await expect(comments).toHaveCount(1);
  });

  test('should display comment threads', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    // Add comments
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    const node = page.locator('[data-testid^="node-"]').first();
    await node.click({ button: 'right' });
    
    const addCommentOption = page.locator('[data-testid="context-add-comment"]');
    await addCommentOption.click();
    
    const commentInput = page.locator('[data-testid="comment-input"]');
    await commentInput.fill('First comment');
    await commentInput.press('Enter');
    
    // Add reply
    const replyBtn = page.locator('[data-testid="comment-reply"]').first();
    await replyBtn.click();
    
    const replyInput = page.locator('[data-testid="comment-reply-input"]');
    await replyInput.fill('This is a reply');
    await replyInput.press('Enter');
    
    // Verify thread
    const comments = page.locator('[data-testid^="comment-"]');
    await expect(comments).toHaveCount(2);
  });

  test('should resolve comments', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    // Add comment
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    const node = page.locator('[data-testid^="node-"]').first();
    await node.click({ button: 'right' });
    
    const addCommentOption = page.locator('[data-testid="context-add-comment"]');
    await addCommentOption.click();
    
    const commentInput = page.locator('[data-testid="comment-input"]');
    await commentInput.fill('Issue to resolve');
    await commentInput.press('Enter');
    
    // Resolve comment
    const resolveBtn = page.locator('[data-testid="comment-resolve"]').first();
    await resolveBtn.click();
    
    // Verify resolved state
    const comment = page.locator('[data-testid^="comment-"]').first();
    await expect(comment).toHaveClass(/resolved/);
  });

  test('should mention users in comments', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    const node = page.locator('[data-testid^="node-"]').first();
    await node.click({ button: 'right' });
    
    const addCommentOption = page.locator('[data-testid="context-add-comment"]');
    await addCommentOption.click();
    
    // Type mention
    const commentInput = page.locator('[data-testid="comment-input"]');
    await commentInput.type('@');
    
    // Select user from dropdown
    const userOption = page.locator('[data-testid^="mention-user-"]').first();
    await userOption.click();
    
    // Complete comment
    await commentInput.type(' please review this');
    await commentInput.press('Enter');
    
    // Verify mention
    const comment = page.locator('[data-testid^="comment-"]').first();
    const text = await comment.textContent();
    expect(text).toContain('@');
  });
});

/**
 * Suite 4: Activity Timeline
 */
test.describe('Collaboration Activity Timeline', () => {
  test('should display activity log', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const activityLog = page.locator('[data-testid="activity-log"]');
    
    await expect(activityLog).toBeVisible();
  });

  test('should show creation events', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    // Check activity log
    const activityEntries = page.locator('[data-testid^="activity-entry-"]');
    const lastEntry = activityEntries.last();
    
    const text = await lastEntry.textContent();
    expect(text).toContain('added');
  });

  test('should show modification events', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add and modify node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    
    // Drag to modify
    await node.drag({ x: 50, y: 50 });
    
    // Check activity log
    const activityEntries = page.locator('[data-testid^="activity-entry-"]');
    const lastEntry = activityEntries.last();
    
    const text = await lastEntry.textContent();
    expect(text).toContain('moved');
  });

  test('should show deletion events', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add and delete node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    
    await node.click();
    await page.keyboard.press('Delete');
    
    // Check activity log
    const activityEntries = page.locator('[data-testid^="activity-entry-"]');
    const lastEntry = activityEntries.last();
    
    const text = await lastEntry.textContent();
    expect(text).toContain('deleted');
  });
});

/**
 * Suite 5: Permissions and Access Control
 */
test.describe('Collaboration Permissions', () => {
  test('should show read-only indicator for guests', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    // Set as read-only
    await page.evaluate(() => {
      sessionStorage.setItem('permissions', 'read-only');
    });
    
    await page.reload();
    
    const readOnlyBadge = page.locator('[data-testid="read-only-badge"]');
    await expect(readOnlyBadge).toBeVisible();
  });

  test('should disable editing for read-only users', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Set as read-only
    await page.evaluate(() => {
      sessionStorage.setItem('permissions', 'read-only');
    });
    
    await page.reload();
    
    // Try to add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    const nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(0); // Should not add
  });

  test('should show permission level in UI', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    const permissionIndicator = page.locator('[data-testid="permission-indicator"]');
    await expect(permissionIndicator).toBeVisible();
    
    const permission = await permissionIndicator.textContent();
    expect(['Owner', 'Editor', 'Viewer'].some(p => permission?.includes(p))).toBeTruthy();
  });
});

/**
 * Suite 6: Session Management
 */
test.describe('Collaboration Session Management', () => {
  test('should maintain connection status', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    const connectionStatus = page.locator('[data-testid="connection-status"]');
    await expect(connectionStatus).toHaveClass(/connected/);
  });

  test('should show disconnection warning', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    // Simulate network issue
    await page.context().setOffline(true);
    
    const disconnectWarning = page.locator('[data-testid="disconnect-warning"]');
    await expect(disconnectWarning).toBeVisible();
    
    // Reconnect
    await page.context().setOffline(false);
    
    await expect(disconnectWarning).not.toBeVisible();
  });

  test('should queue operations during disconnect', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Go offline
    await page.context().setOffline(true);
    
    // Try to add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    // Should show in queue
    const queueIndicator = page.locator('[data-testid="queue-indicator"]');
    await expect(queueIndicator).toBeVisible();
    
    // Come back online
    await page.context().setOffline(false);
    
    // Operations should sync
    await page.waitForTimeout(1000);
    
    const nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(1);
  });
});
