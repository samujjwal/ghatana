#!/bin/bash
# Quick Extension Debug Test
# Run this after loading the extension in Chrome

echo "=== Guardian Extension Quick Debug Test ==="
echo ""
echo "This script will help you verify the extension is working."
echo ""
echo "MANUAL STEPS:"
echo ""
echo "1. Open Chrome DevTools Inspector for Extension (Background)"
echo "   - chrome://extensions/"
echo "   - Find Guardian extension"
echo "   - Click 'Inspect Views' -> 'background page'"
echo "   - Copy-paste code below into Console tab"
echo ""
echo "2. Then visit a few websites (google.com, github.com, etc)"
echo ""
echo "3. Check console for these logs:"
echo "   ✓ [GuardianController] PAGE_USAGE_TRACKED received {...}"
echo "   ✓ [GuardianController] Storing N records"
echo ""
echo "=== CODE TO PASTE IN EXTENSION CONSOLE ==="
echo ""
cat << 'EOF'
// ============ PASTE THIS INTO EXTENSION CONSOLE ============

console.log('=== Guardian Extension Debug Test ===');

// Test 1: Check if controller exists
console.log('1. Controller exists:', !!guardianController);

// Test 2: Check if router listeners registered
console.log('2. Message router context:', guardianController.router?.getContextType?.());

// Test 3: Get current state
const state = guardianController.getState();
console.log('3. Current state:', {
  initialized: state.initialized,
  metricsCollecting: state.metricsCollecting,
  totalMetricsCollected: state.totalMetricsCollected
});

// Test 4: Check storage for usage data
chrome.storage.local.get('guardian-usage', (result) => {
  const data = result['guardian-usage'] || [];
  console.log('4. Stored usage records:', data.length);
  if (data.length > 0) {
    console.log('   Sample record:', data[0]);
  }
});

// Test 5: Get analytics summary
(async () => {
  const analytics = await guardianController.getAnalyticsSummary();
  console.log('5. Analytics summary:', {
    totalRecords: analytics.totalUsageRecords,
    last24h: analytics.webUsage.last24h,
    topDomains: analytics.topDomains.length
  });
  if (analytics.topDomains.length > 0) {
    console.log('   Top domain:', analytics.topDomains[0].domain, 
                'visits:', analytics.topDomains[0].visitCount);
  }
})();

// Test 6: Send test GET_ANALYTICS message
chrome.runtime.sendMessage({ type: 'GET_ANALYTICS', payload: {} }, (response) => {
  console.log('6. GET_ANALYTICS response:', {
    success: response?.success,
    hasData: !!response?.data,
    records: response?.data?.totalUsageRecords
  });
});

console.log('=== Tests started, check output above ===');

// ============ END CODE TO PASTE ============
EOF

echo ""
echo "=== EXPECTED OUTPUT ==="
echo ""
echo "If everything is working:"
echo ""
echo "✓ Controller exists: true"
echo "✓ Message router context: background"
echo "✓ Current state:"
echo "    - initialized: true"
echo "    - metricsCollecting: true"  
echo "    - totalMetricsCollected: 1+ (after visiting pages)"
echo "✓ Stored usage records: 1+ (after visiting pages)"
echo "✓ Analytics summary:"
echo "    - totalRecords: 1+"
echo "    - last24h: 1+ (after visiting pages)"
echo "    - topDomains: 1+ (after visiting pages)"
echo "✓ GET_ANALYTICS response:"
echo "    - success: true"
echo "    - hasData: true"
echo "    - records: 1+"
echo ""
echo "=== NEXT STEPS ==="
echo ""
echo "If all tests pass:"
echo "  ✓ Rebuild extension: pnpm build"
echo "  ✓ Reload extension in Chrome"
echo "  ✓ Visit websites"
echo "  ✓ Open popup - should show usage data"
echo ""
echo "If tests fail:"
echo "  ✗ Check DEBUG_GUIDE.md for troubleshooting"
echo "  ✗ Review console errors"
echo "  ✗ Check content script console (F12 on web page)"
