# Content Security Policy (CSP) Fix for Blocked Page

**Date:** November 24, 2025  
**Issue:** Inline event handlers violating CSP directive `script-src 'self' 'wasm-unsafe-eval'`  
**Status:** ✅ **FIXED**

---

## Problem

The blocked page (`blocked.html`) was using inline event handler attributes, which violate the Content Security Policy (CSP) directive for extension pages.

### Error Messages

```
Executing inline event handler violates the following Content Security Policy directive
'script-src 'self' 'wasm-unsafe-eval''. Either the 'unsafe-inline' keyword, a hash
('sha256-...'), or a nonce ('nonce-...') is required to enable inline execution.
```

### Root Cause

**Non-compliant Code (WRONG)**:

```html
<button class="btn btn-primary" onclick="goBack()">Go Back</button>
<button class="btn btn-secondary" onclick="goHome()">Go to Homepage</button>

<script>
  // Inline script with functions
  function goBack() { ... }
  function goHome() { ... }
</script>
```

This violates the CSP because:

1. ❌ `onclick` attributes are inline event handlers
2. ❌ `<script>` tags with inline code in HTML
3. ❌ CSP policy doesn't allow `'unsafe-inline'` or `'unsafe-hashes'`

---

## Solution

### 1. External HTML File (CSP Compliant)

**File:** `src/pages/blocked.html`

- ✅ Removed all `onclick` attributes from buttons
- ✅ Added element IDs for JavaScript targeting (`id="btn-back"`, `id="btn-home"`)
- ✅ Uses external `<script src="blocked.js">` instead of inline script

**Compliant Code (CORRECT)**:

```html
<button class="btn btn-primary" id="btn-back">Go Back</button>
<button class="btn btn-secondary" id="btn-home">Go to Homepage</button>

<!-- External script reference -->
<script src="blocked.js"></script>
```

### 2. External JavaScript File (CSP Compliant)

**File:** `src/pages/blocked.js`

- ✅ All event listeners attached via `addEventListener()` (not inline)
- ✅ Functions defined in external file
- ✅ Safe DOM access with null checks
- ✅ Proper error handling for chrome API availability

**Compliant Code (CORRECT)**:

```javascript
// Attach event listeners (CSP compliant - no inline handlers)
const backBtn = document.getElementById("btn-back");
if (backBtn) {
  backBtn.addEventListener("click", goBack);
}

const homeBtn = document.getElementById("btn-home");
if (homeBtn) {
  homeBtn.addEventListener("click", goHome);
}

function goBack() {
  if (window.history.length > 1) {
    window.history.back();
  } else {
    goHome();
  }
}
```

### 3. Manifest Configuration Update

**File:** `manifest.config.ts`

Added blocked page resources to web_accessible_resources:

```typescript
web_accessible_resources: [
  {
    resources: [
      "src/dashboard/index.html",
      "src/options/index.html",
      "src/pages/blocked.html",      // ← Added
      "src/pages/blocked.js"          // ← Added
    ],
    matches: ["<all_urls>"],
  },
],
```

This ensures the blocked page and its JavaScript can be served as web-accessible resources when the extension redirects to them.

---

## Files Changed

| File                     | Change  | Purpose                                             |
| ------------------------ | ------- | --------------------------------------------------- |
| `src/pages/blocked.html` | Created | Blocked page UI (CSP compliant)                     |
| `src/pages/blocked.js`   | Created | Event handlers via addEventListener (CSP compliant) |
| `manifest.config.ts`     | Updated | Added blocked page resources                        |

---

## How It Works

### Original (Broken) Flow

```
Website Navigation
  ↓
WebsiteBlocker checks policy
  ↓
If blocked: chrome.tabs.update({ url: 'blocked.html?...' })
  ↓
blocked.html loads with:
  ❌ onclick="goBack()" attributes → CSP VIOLATION
  ❌ Inline <script>...</script> → CSP VIOLATION
  ↓
Errors in console, buttons don't work
```

### Fixed Flow

```
Website Navigation
  ↓
WebsiteBlocker checks policy
  ↓
If blocked: chrome.tabs.update({ url: chrome.runtime.getURL('blocked.html?...') })
  ↓
blocked.html loads with:
  ✅ <button id="btn-back"> → No inline attributes
  ✅ <script src="blocked.js"></script> → External script
  ↓
blocked.js executes and:
  ✅ Finds buttons by ID
  ✅ Attaches event listeners via addEventListener
  ✅ Functions properly scoped in external file
  ↓
✅ All events fire correctly, CSP compliant
```

---

## CSP Compliance Verification

### CSP Policy (From manifest.config.ts)

```
script-src 'self' 'wasm-unsafe-eval';
style-src 'self' 'unsafe-inline';
connect-src 'self' ws: wss:;
object-src 'self'
```

### Compliance Checklist

- ✅ `script-src 'self'` - Only allow scripts from extension origin (blocked.js is extension resource)
- ✅ No inline `onclick` attributes
- ✅ No `<script>` tags with inline code
- ✅ All JavaScript in external file (blocked.js)
- ✅ CSS remains inline (allowed by `style-src 'unsafe-inline'`)
- ✅ No `eval()`, `Function()`, or similar constructs
- ✅ No `javascript:` protocol navigation

---

## Testing

### Browser DevTools Check

1. Load extension in Chrome/Firefox/Edge
2. Navigate to a blocked website (e.g., youtube.com during school hours)
3. Blocked page should load
4. **Console Check** (F12 → Console):
   - ❌ Should NOT see: `Executing inline event handler violates CSP`
   - ❌ Should NOT see: `Executing inline script violates CSP`
   - ✅ Should see: Page displays correctly
5. **Functionality Check**:
   - ✅ "Go Back" button works
   - ✅ "Go to Homepage" button works
   - ✅ No JavaScript errors

### Expected Console Output (Clean)

```
✅ No CSP violations
✅ blocked.js loads successfully
✅ Event listeners attached
✅ Block event logged (or skipped if API unavailable)
```

### Expected Console Output (Before Fix - Broken)

```
❌ Executing inline event handler violates the following Content Security Policy
   directive 'script-src 'self' 'wasm-unsafe-eval''
❌ Executing inline script violates the following Content Security Policy directive
❌ onclick is not a function
```

---

## Best Practices Applied

### 1. CSP Compliance

- ✅ No inline event handlers
- ✅ No inline scripts
- ✅ All JavaScript in external files
- ✅ All files are web-accessible resources

### 2. Error Handling

- ✅ Null checks before DOM operations
- ✅ Try-catch for chrome API calls
- ✅ Safe property access
- ✅ Graceful degradation

### 3. Security

- ✅ No eval() or dynamic code execution
- ✅ No XSS vulnerabilities from URL parameters
- ✅ Safe use of decodeURIComponent()
- ✅ Proper element ID isolation

### 4. Accessibility

- ✅ Semantic button elements
- ✅ Proper heading hierarchy
- ✅ Clear labels and instructions
- ✅ Good contrast ratios

---

## Related Components

### WebsiteBlocker.ts

The blocker correctly uses `chrome.runtime.getURL('blocked.html')` to get the proper URL:

```typescript
const blockedUrl =
  chromeApi.runtime.getURL("blocked.html") +
  `?url=${encodeURIComponent(changeInfo.url)}&reason=${encodeURIComponent(result.reason || "blocked")}`;
```

This ensures the blocked page is served as a web-accessible resource, which allows the external script to load properly.

---

## Summary

The blocked page now uses **CSP-compliant patterns**:

1. ❌ **Don't use inline handlers** → ✅ **Use external script files**
2. ❌ **Don't use inline styles** → ✅ **Use style tags with internal CSS** (or external stylesheets)
3. ❌ **Don't use eval()** → ✅ **Use DOM methods and event listeners**
4. ✅ **Reference external scripts** → Add to web_accessible_resources in manifest
5. ✅ **Test in all browsers** → Chrome, Firefox, Edge

---

## Deployment

After applying these changes:

1. Rebuild extension: `pnpm build`
2. Verify all 3 browsers build successfully
3. Load extension in test browser
4. Navigate to blocked website
5. Check for CSP errors in DevTools
6. Verify buttons work without errors

✅ **Status: Ready for deployment**

---

## References

- [CSP for Manifest V3](https://developer.chrome.com/docs/extensions/mv3/content_security_policy/)
- [Web Accessible Resources](https://developer.chrome.com/docs/extensions/mv3/manifest/web_accessible_resources/)
- [MDN: Content Security Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP)
- [MDN: addEventListener](https://developer.mozilla.org/en-US/docs/Web/API/EventTarget/addEventListener)
