// Clear stale localStorage data
// Run this in browser console: window.clearYappcCache()

window.clearYappcCache = function () {
    const keys = [
        'yappc:currentWorkspaceId',
        'yappc_active_personas',
        'yappc_primary_persona',
        'onboarding_complete',
        'yappc_sidebar_collapsed'
    ];

    keys.forEach(key => {
        localStorage.removeItem(key);
        console.log(`✓ Cleared ${key}`);
    });

    console.log('✨ Cache cleared. Refresh the page.');
};

console.log('💡 Run: window.clearYappcCache() to reset app state');
