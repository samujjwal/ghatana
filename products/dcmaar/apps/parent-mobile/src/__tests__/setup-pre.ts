// Pre-setup executed before any modules are loaded by Jest.
// Purpose: enable synchronous act environment and ensure React internals exist
// before other modules possibly require React or react-test-renderer.

// Disable @testing-library/react-native's automatic cleanup-after-each hook.
// This hook can interfere with renderer instances during host-component detection.
// By setting this flag early, we prevent the library from installing the hook.
(global as any).RNTL_SKIP_AUTO_CLEANUP = true;
process.env.RNTL_SKIP_AUTO_CLEANUP = 'true';
 
console.log('[JEST-DIAG] RNTL_SKIP_AUTO_CLEANUP set to:', process.env.RNTL_SKIP_AUTO_CLEANUP);

// Enable React's act environment so react-test-renderer and testing utils
// can operate synchronously when required by host-detection logic.
// This must run as a setupFiles entry (not setupFilesAfterEnv) so it's executed
// before other modules that may import React.
 
(global as any).IS_REACT_ACT_ENVIRONMENT = true;
// Also set a flag that some React test utilities inspect. This can influence
// react-test-renderer behavior in certain builds (toggle concurrency heuristics).
(global as any).IS_REACT_NATIVE_TEST_ENVIRONMENT = false;

try {
     
    const React = require('react');
    if (!React.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED) {
        React.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED = {};
    }
} catch {
    // ignore if react cannot be required here
}
