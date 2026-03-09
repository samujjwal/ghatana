# Quick Testing Guide - Load Extension into Browsers

## 🚀 Chrome

### Load Extension

```bash
# 1. Open Chrome
chrome://extensions

# 2. Enable "Developer mode" (toggle in top-right)

# 3. Click "Load unpacked"

# 4. Navigate to and select:
/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/browser-extension/dist/chrome
```

### Test the Extension

1. Click the Guardian icon in toolbar
2. **Popup should load** (no syntax errors in console)
3. See usage stats: "24 Hours", "7 Days", "All Time"
4. Click "View Detailed Report"
5. **Dashboard should load** (no syntax errors in console)
6. See more detailed statistics and top websites
7. Click settings icon
8. **Options page should load** (no syntax errors in console)

### Check Console for Errors

1. Right-click → "Inspect"
2. Go to "Console" tab
3. **Should see NO errors like:**
   - ❌ "Cannot use import statement outside a module"
   - ❌ "Uncaught SyntaxError"
   - ❌ "Failed to load analytics"

---

## 🔥 Firefox

### Load Extension

```bash
# 1. Open Firefox
about:debugging#/runtime/this-firefox

# 2. Click "Load Temporary Add-on..."

# 3. Navigate to and select ANY file in:
/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/browser-extension/dist/firefox

# 4. Extension will be loaded temporarily
```

### Test the Extension

1. Click the Guardian icon in toolbar
2. **Popup should load** (no syntax errors)
3. Same tests as Chrome (steps 2-8)

### Check Console for Errors

1. Press F12 to open Developer Tools
2. Go to "Console" tab
3. Same checks as Chrome

---

## 🔷 Edge

### Load Extension

```bash
# 1. Open Edge
edge://extensions

# 2. Enable "Developer mode" (toggle in bottom-left)

# 3. Click "Load unpacked"

# 4. Navigate to and select:
/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/apps/browser-extension/dist/edge
```

### Test the Extension

1. Same as Chrome (steps 1-8)

### Check Console for Errors

1. Same as Chrome

---

## ✅ Expected Results

### If Fix Works ✓

- ✅ Popup loads immediately
- ✅ Popup displays "Loading..." briefly, then shows stats
- ✅ Dashboard button works (opens new tab with full report)
- ✅ Settings loads without errors
- ✅ Browser console shows NO syntax errors
- ✅ All 3 browsers work identically

### If Something is Wrong ✗

- ❌ Popup shows blank page
- ❌ Console shows "Cannot use import statement outside a module"
- ❌ Console shows "Cannot read properties of null"
- ❌ Dashboard doesn't load after clicking button
- ❌ Settings page shows error

---

## 📊 Verification Checklist

### Popup Page

- [ ] Opens without errors
- [ ] Shows "Monitoring Active" or "Monitoring Inactive"
- [ ] Displays "24 Hours", "7 Days", "All Time" stats
- [ ] Shows "Tracked: X" count
- [ ] Shows current site policy (allow/block/warn)
- [ ] "View Detailed Report" button works

### Dashboard Page

- [ ] Opens when button clicked from popup
- [ ] Shows header with Guardian title
- [ ] Displays larger statistics
- [ ] Shows "Top Websites" section
- [ ] Shows "Events Captured" count
- [ ] All data populated (not blank)

### Options Page

- [ ] Opens when settings icon clicked
- [ ] Displays configuration options
- [ ] Can interact with form elements
- [ ] No console errors

### Console Verification

- [ ] No syntax errors
- [ ] No import errors
- [ ] No "Cannot read properties of null" errors
- [ ] No 404 errors for assets

---

## 🐛 Troubleshooting

### "Cannot use import statement outside a module"

- **Status**: 🟢 FIXED (this session)
- **Should not appear in console anymore**

### "Failed to load analytics"

- **Status**: 🟢 FIXED (cascading fix from above)
- **Should not appear in console anymore**

### Popup appears blank

- Check console for errors
- Refresh popup (sometimes needed after build)
- Try reloading extension

### Data not showing in popup

- Navigate to a few websites first
- Wait 2-3 seconds for content script to collect data
- Refresh popup

### Dashboard doesn't open

- Check browser console for errors
- Verify `dist/chrome/dashboard.js` exists
- Try reloading extension

---

## 📁 File Locations

### Chrome Build

```
dist/chrome/
  manifest.json
  src/popup/index.html
  src/dashboard/index.html
  src/options/index.html
  popup.js          ← loaded by popup/index.html
  dashboard.js      ← loaded by dashboard/index.html
  options.js        ← loaded by options/index.html
  assets/           ← shared dependencies
```

### Firefox Build

```
dist/firefox/
  [same structure as Chrome]
```

### Edge Build

```
dist/edge/
  [same structure as Chrome]
```

---

## 🔍 What to Look For

### In Popup Console

**Should show analytics messages:**

```
Content script → Background: GET_ANALYTICS
Background → Content script: {success: true, data: {...}}
```

### In Dashboard Console

**Should show dashboard data load:**

```
Loaded 42 analytics events
Displaying stats for 7 days
Top 5 websites calculated
```

### In Options Console

**Should show options initialized:**

```
Settings page loaded
Retrieving current policy settings
```

---

## 🎯 Summary

1. **Load extension** from `dist/{browser}/` folder
2. **Test popup** - should load and show usage stats
3. **Test dashboard** - should open and show detailed report
4. **Test options** - should open settings page
5. **Check console** - should show NO syntax errors
6. **Verify analytics** - should see usage data flow end-to-end

If all ✅, the fix is working correctly!

---

**Built**: Session 12, Part 4c  
**Status**: ✅ All 3 browsers built successfully  
**Entry points**: `src/{popup,dashboard,options}/index.ts`  
**HTML references**: `<script type="module" src="./index.ts"></script>`  
**Next step**: Load into browsers and test!
