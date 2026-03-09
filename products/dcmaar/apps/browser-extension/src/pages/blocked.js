/**
 * Blocked Page Handler
 * Handles navigation and events for the access blocked page
 * CSP-compliant: No inline event handlers, all events via addEventListener
 */

// Parse URL parameters
const params = new URLSearchParams(window.location.search);
const blockedUrl = params.get('url') || 'Unknown URL';
const reason = params.get('reason') || 'This website is blocked by your parental controls.';
const policyId = params.get('policyId') || '';
const timeRemainingParam = params.get('timeRemainingMinutes');
const timeRemainingMinutes = timeRemainingParam ? parseInt(timeRemainingParam, 10) : NaN;

// Display blocked URL
const blockedUrlEl = document.getElementById('blocked-url');
if (blockedUrlEl) {
  blockedUrlEl.textContent = decodeURIComponent(blockedUrl);
}

// Display reason
const reasonEl = document.getElementById('reason-text');
if (reasonEl) {
  reasonEl.textContent = decodeURIComponent(reason);
}

// Display time remaining for time-window blocks (if provided)
const timeRemainingEl = document.getElementById('time-remaining');
if (timeRemainingEl && !Number.isNaN(timeRemainingMinutes) && timeRemainingMinutes > 0) {
  const rounded = Math.max(1, Math.round(timeRemainingMinutes));
  const label = rounded === 1 ? 'minute' : 'minutes';
  timeRemainingEl.textContent = `This site is temporarily blocked. It should be available again in about ${rounded} ${label}.`;
}

// Display timestamp
const timestampEl = document.getElementById('timestamp');
if (timestampEl) {
  const now = new Date();
  timestampEl.textContent = now.toLocaleString();
}

/**
 * Navigate back in browser history or go to homepage
 */
function goBack() {
  if (window.history.length > 1) {
    window.history.back();
  } else {
    goHome();
  }
}

/**
 * Navigate to blank/home page
 */
function goHome() {
  window.location.href = 'about:blank';
}

/**
 * Log block event to extension
 */
function logBlockEvent() {
  try {
    if (typeof chrome !== 'undefined' && chrome.runtime) {
      chrome.runtime.sendMessage({
        type: 'BLOCK_EVENT_VIEWED',
        url: blockedUrl,
        reason: reason,
        timestamp: Date.now()
      }).catch(() => {
        // Ignore errors (extension context might not be available)
      });
    }
  } catch (_error) {
    // Silently handle errors
    console.debug('Block event logging not available');
  }
}

/**
 * Send unblock / more-time request to extension
 */
function requestUnblock() {
  const btn = document.getElementById('btn-request-unblock');
  const statusEl = document.getElementById('request-status');
  const notesEl = document.getElementById('request-notes');

  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Requesting...';
  }

  const childReason = notesEl && 'value' in notesEl ? notesEl.value.trim() : '';

  try {
    if (typeof chrome !== 'undefined' && chrome.runtime) {
      chrome.runtime.sendMessage(
        {
          type: 'REQUEST_UNBLOCK',
          url: blockedUrl,
          policyId: policyId || undefined,
          blockReason: reason,
          childReason: childReason || undefined,
          timeRemainingMinutes: !Number.isNaN(timeRemainingMinutes)
            ? timeRemainingMinutes
            : undefined,
          source: 'blocked_page',
          timestamp: Date.now(),
        },
        (response) => {
          const ok = !chrome.runtime.lastError && (!response || response.success !== false);

          if (statusEl) {
            statusEl.textContent = ok
              ? 'Your request has been sent to your parent or guardian.'
              : 'Unable to send request. Please ask your parent directly.';
            statusEl.classList.remove('request-status-success', 'request-status-error');
            statusEl.classList.add(ok ? 'request-status-success' : 'request-status-error');
          }

          if (btn) {
            btn.textContent = ok ? 'Request Sent' : 'Request Access';
            btn.disabled = ok;
          }
        }
      );
    } else {
      if (statusEl) {
        statusEl.textContent = 'Extension is not available to send a request.';
        statusEl.classList.remove('request-status-success');
        statusEl.classList.add('request-status-error');
      }

      if (btn) {
        btn.textContent = 'Request Access';
        btn.disabled = false;
      }
    }
  } catch (error) {
    if (statusEl) {
      statusEl.textContent = 'Something went wrong while sending your request.';
      statusEl.classList.remove('request-status-success');
      statusEl.classList.add('request-status-error');
    }

    if (btn) {
      btn.textContent = 'Request Access';
      btn.disabled = false;
    }
  }
}

// Attach event listeners (CSP compliant - no inline handlers)
const backBtn = document.getElementById('btn-back');
if (backBtn) {
  backBtn.addEventListener('click', goBack);
}

const homeBtn = document.getElementById('btn-home');
if (homeBtn) {
  homeBtn.addEventListener('click', goHome);
}

const requestBtn = document.getElementById('btn-request-unblock');
if (requestBtn) {
  // If we know this is a time-window block, make the label more specific
  if (!Number.isNaN(timeRemainingMinutes) && timeRemainingMinutes > 0) {
    requestBtn.textContent = 'Request More Time';
  }
  requestBtn.addEventListener('click', requestUnblock);
}

// Log the block event when page loads
window.addEventListener('load', logBlockEvent);

// Prevent page reload attempts
window.addEventListener('beforeunload', () => {
  // Allow navigation
  return undefined;
});
