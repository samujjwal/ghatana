// Fallback logging and visible error message for popup debugging
// Kept as a module (external file) to comply with Manifest V3 CSP (no inline scripts).

try {
  console.log('Guardian popup loaded (fallback script)');
} catch (e) {
  // ignore
}

if (typeof window !== 'undefined') {
  window.addEventListener('error', (ev) => {
    try {
      console.error('Guardian popup caught error:', (ev as ErrorEvent).error || (ev as any).message, ev);
      const root = document.getElementById('root');
      if (root) root.innerText = 'Error loading UI — check console.';
    } catch (e) {
      // ignore
    }
  });

  // If React hasn't mounted within 2s, show a helpful message
  setTimeout(() => {
    try {
      const root = document.getElementById('root');
      if (root && !root.hasChildNodes()) {
        root.innerText = 'UI not mounted yet — check background/service-worker console for errors.';
      }
    } catch (e) {
      // ignore
    }
  }, 2000);
}
