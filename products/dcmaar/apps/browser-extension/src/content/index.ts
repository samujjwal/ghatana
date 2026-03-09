/**
 * @fileoverview Guardian Content Script
 *
 * Runs in page context to monitor page activity and DOM events.
 * Captures rich page metrics and sends them to the background pipeline.
 * 
 * Features:
 * - Page metadata extraction (title, meta tags, OpenGraph)
 * - User interaction tracking (scroll, clicks, forms)
 * - Media event tracking (video play/pause)
 * - DOM analysis for safety scanning
 * 
 * @module content
 */

import type { PageMetadata, InteractionData, MediaTrackingData, DOMAnalysisData } from '../pipeline/types';
import type { ContentScriptMessage } from '../pipeline/sources/ContentScriptSource';

// Configuration
const CONFIG = {
    /** Minimum time between interaction updates (ms) */
    interactionThrottleMs: 5000,
    /** Whether to track scroll depth */
    trackScroll: true,
    /** Whether to track clicks */
    trackClicks: true,
    /** Whether to track form interactions */
    trackForms: true,
    /** Whether to track media events */
    trackMedia: true,
    /** Whether to perform DOM analysis */
    enableDomAnalysis: true,
    /** Keywords to detect for safety scanning */
    safetyKeywords: ['violence', 'drugs', 'gambling', 'adult'],
};

// State
let interactionData: InteractionData = {
    scrollDepth: 0,
    clickCount: 0,
    formInteractions: 0,
    activeTime: 0,
    hasScrolled: false,
};

let lastInteractionUpdate = 0;
let pageLoadTime = Date.now();
let isPageVisible = true;
let activeTimeStart = Date.now();

/**
 * Send message to background script
 */
function sendMessage(message: ContentScriptMessage): void {
    try {
        chrome.runtime.sendMessage(message, () => {
            if (chrome.runtime.lastError) {
                console.debug('[Guardian Content] Message error:', chrome.runtime.lastError.message);
            }
        });
    } catch (error) {
        console.debug('[Guardian Content] Failed to send message:', error);
    }
}

/**
 * Get current page URL and domain
 */
function getPageInfo(): { url: string; domain: string } {
    const url = window.location.href;
    let domain = 'unknown';
    try {
        domain = new URL(url).hostname.replace(/^www\./, '');
    } catch {
        // Ignore URL parsing errors
    }
    return { url, domain };
}

/**
 * Extract page metadata
 */
function extractPageMetadata(): PageMetadata {
    const getMeta = (name: string): string | undefined => {
        const el = document.querySelector(`meta[name="${name}"], meta[property="${name}"]`);
        return el?.getAttribute('content') || undefined;
    };

    return {
        title: document.title,
        description: getMeta('description'),
        ogTitle: getMeta('og:title'),
        ogDescription: getMeta('og:description'),
        ogImage: getMeta('og:image'),
        canonicalUrl: document.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href,
        keywords: getMeta('keywords')?.split(',').map((k) => k.trim()).filter(Boolean),
        language: document.documentElement.lang || undefined,
    };
}

/**
 * Perform DOM analysis for safety scanning
 */
function analyzeDom(): DOMAnalysisData {
    const bodyText = document.body?.innerText?.toLowerCase() || '';

    // Detect safety keywords
    const detectedKeywords = CONFIG.safetyKeywords.filter((keyword) =>
        bodyText.includes(keyword.toLowerCase())
    );

    // Calculate safety score (higher is safer)
    const safetyScore = Math.max(0, 100 - detectedKeywords.length * 20);

    // Check for forms
    const forms = document.querySelectorAll('form');
    const hasForms = forms.length > 0;
    const hasLoginForm = Array.from(forms).some((form) => {
        const inputs = form.querySelectorAll('input');
        return Array.from(inputs).some((input) =>
            input.type === 'password' || input.name?.toLowerCase().includes('password')
        );
    });

    // Count external links
    const links = document.querySelectorAll('a[href]');
    const currentHost = window.location.hostname;
    const externalLinkCount = Array.from(links).filter((link) => {
        try {
            const href = (link as HTMLAnchorElement).href;
            return new URL(href).hostname !== currentHost;
        } catch {
            return false;
        }
    }).length;

    return {
        detectedKeywords,
        safetyScore,
        hasForms,
        hasLoginForm,
        externalLinkCount,
    };
}

/**
 * Send page metadata on load
 */
function sendPageMetadata(): void {
    const { url, domain } = getPageInfo();
    const pageMetadata = extractPageMetadata();

    sendMessage({
        type: 'PAGE_METADATA',
        url,
        domain,
        timestamp: Date.now(),
        data: pageMetadata,
    });
}

/**
 * Send DOM analysis
 */
function sendDomAnalysis(): void {
    if (!CONFIG.enableDomAnalysis) return;

    const { url, domain } = getPageInfo();
    const domAnalysis = analyzeDom();

    sendMessage({
        type: 'DOM_ANALYSIS',
        url,
        domain,
        timestamp: Date.now(),
        data: domAnalysis,
    });
}

/**
 * Send interaction update (throttled)
 */
function sendInteractionUpdate(force = false): void {
    const now = Date.now();
    if (!force && now - lastInteractionUpdate < CONFIG.interactionThrottleMs) {
        return;
    }

    lastInteractionUpdate = now;
    const { url, domain } = getPageInfo();

    // Update active time
    if (isPageVisible) {
        interactionData.activeTime += now - activeTimeStart;
        activeTimeStart = now;
    }

    sendMessage({
        type: 'PAGE_INTERACTION',
        url,
        domain,
        timestamp: now,
        data: { ...interactionData },
    });
}

/**
 * Handle scroll events
 */
function handleScroll(): void {
    if (!CONFIG.trackScroll) return;

    const scrollTop = window.scrollY || document.documentElement.scrollTop;
    const scrollHeight = document.documentElement.scrollHeight - window.innerHeight;
    const scrollDepth = scrollHeight > 0 ? Math.round((scrollTop / scrollHeight) * 100) : 0;

    interactionData.scrollDepth = Math.max(interactionData.scrollDepth, scrollDepth);
    interactionData.hasScrolled = true;

    sendInteractionUpdate();
}

/**
 * Handle click events
 */
function handleClick(): void {
    if (!CONFIG.trackClicks) return;

    interactionData.clickCount++;
    sendInteractionUpdate();
}

/**
 * Handle form interactions
 */
function handleFormInteraction(): void {
    if (!CONFIG.trackForms) return;

    interactionData.formInteractions++;
    sendInteractionUpdate();
}

/**
 * Handle visibility change
 */
function handleVisibilityChange(): void {
    const now = Date.now();

    if (document.hidden) {
        // Page became hidden - update active time
        if (isPageVisible) {
            interactionData.activeTime += now - activeTimeStart;
        }
        isPageVisible = false;
    } else {
        // Page became visible - restart active time tracking
        activeTimeStart = now;
        isPageVisible = true;
    }
}

/**
 * Handle media events (video/audio)
 */
function setupMediaTracking(): void {
    if (!CONFIG.trackMedia) return;

    const trackMedia = (element: HTMLMediaElement, mediaType: 'video' | 'audio') => {
        const platform = detectPlatform();

        const sendMediaEvent = (state: MediaTrackingData['state']) => {
            const { url, domain } = getPageInfo();

            const mediaData: MediaTrackingData = {
                mediaType,
                platform,
                title: document.title,
                duration: Math.round(element.currentTime),
                state,
                videoId: extractVideoId(),
            };

            sendMessage({
                type: 'MEDIA_EVENT',
                url,
                domain,
                timestamp: Date.now(),
                data: mediaData,
            });
        };

        element.addEventListener('play', () => sendMediaEvent('playing'));
        element.addEventListener('pause', () => sendMediaEvent('paused'));
        element.addEventListener('ended', () => sendMediaEvent('ended'));
    };

    // Track existing media elements
    document.querySelectorAll('video').forEach((el) => trackMedia(el, 'video'));
    document.querySelectorAll('audio').forEach((el) => trackMedia(el, 'audio'));

    // Watch for dynamically added media elements
    const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
            for (const node of mutation.addedNodes) {
                if (node instanceof HTMLVideoElement) {
                    trackMedia(node, 'video');
                } else if (node instanceof HTMLAudioElement) {
                    trackMedia(node, 'audio');
                } else if (node instanceof Element) {
                    node.querySelectorAll('video').forEach((el) => trackMedia(el, 'video'));
                    node.querySelectorAll('audio').forEach((el) => trackMedia(el, 'audio'));
                }
            }
        }
    });

    observer.observe(document.body, { childList: true, subtree: true });
}

/**
 * Detect media platform from URL
 */
function detectPlatform(): string {
    const hostname = window.location.hostname;
    if (hostname.includes('youtube.com')) return 'youtube';
    if (hostname.includes('netflix.com')) return 'netflix';
    if (hostname.includes('spotify.com')) return 'spotify';
    if (hostname.includes('twitch.tv')) return 'twitch';
    if (hostname.includes('vimeo.com')) return 'vimeo';
    if (hostname.includes('soundcloud.com')) return 'soundcloud';
    return 'other';
}

/**
 * Extract video ID from URL (YouTube, etc.)
 */
function extractVideoId(): string | undefined {
    const url = new URL(window.location.href);

    // YouTube
    if (url.hostname.includes('youtube.com')) {
        return url.searchParams.get('v') || undefined;
    }

    // YouTube short URLs
    if (url.hostname.includes('youtu.be')) {
        return url.pathname.slice(1) || undefined;
    }

    return undefined;
}

/**
 * Handle page unload
 */
function handleUnload(): void {
    // Send final interaction data
    sendInteractionUpdate(true);

    const { url, domain } = getPageInfo();
    sendMessage({
        type: 'PAGE_UNLOAD',
        url,
        domain,
        timestamp: Date.now(),
        data: {
            ...interactionData,
            totalTime: Date.now() - pageLoadTime,
        },
    });
}

/**
 * Initialize content script
 */
function initialize(): void {
    console.debug('[Guardian Content Script] Initializing...');

    // Send initial page metadata
    if (document.readyState === 'complete') {
        sendPageMetadata();
        sendDomAnalysis();
    } else {
        window.addEventListener('load', () => {
            sendPageMetadata();
            sendDomAnalysis();
        });
    }

    // Setup event listeners
    window.addEventListener('scroll', handleScroll, { passive: true });
    document.addEventListener('click', handleClick, { passive: true });
    document.addEventListener('input', handleFormInteraction, { passive: true });
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('beforeunload', handleUnload);

    // Setup media tracking
    setupMediaTracking();

    console.debug('[Guardian Content Script] Initialized');

    // Check for any active daily time quota and show a gentle warning toast
    // without blocking the page. This relies on background evaluating
    // usage-based limits via GET_QUOTA_STATUS.
    void checkQuotaAndShowToast();
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize);
} else {
    initialize();
}

interface QuotaStatus {
    hasQuota: boolean;
    domain?: string;
    limitMinutes?: number;
    usedMinutes?: number;
    remainingMinutes?: number;
    policyId?: string;
}

async function fetchQuotaStatus(url: string, domain: string): Promise<QuotaStatus | null> {
    return new Promise<QuotaStatus | null>((resolve) => {
        try {
            if (typeof chrome === 'undefined' || !chrome.runtime?.sendMessage) {
                resolve(null);
                return;
            }

            chrome.runtime.sendMessage(
                {
                    type: 'GET_QUOTA_STATUS',
                    url,
                    domain,
                },
                (response) => {
                    if (chrome.runtime.lastError) {
                        console.debug(
                            '[Guardian Content] GET_QUOTA_STATUS error:',
                            chrome.runtime.lastError.message,
                        );
                        resolve(null);
                        return;
                    }

                    if (!response || response.success === false || !response.data) {
                        resolve(null);
                        return;
                    }

                    resolve(response.data as QuotaStatus);
                },
            );
        } catch (error) {
            console.debug('[Guardian Content] Failed to fetch quota status:', error);
            resolve(null);
        }
    });
}

async function checkQuotaAndShowToast(): Promise<void> {
    try {
        // Only show quota UI in the top-level frame
        if (window.top !== window) return;

        const { url, domain } = getPageInfo();
        const status = await fetchQuotaStatus(url, domain);

        if (!status || !status.hasQuota) return;

        const remaining = typeof status.remainingMinutes === 'number'
            ? status.remainingMinutes
            : undefined;

        if (!remaining || remaining <= 0) {
            // Quota exhausted or no remaining time – enforcement is handled
            // by the background pipeline; no need for a warning toast here.
            return;
        }

        showQuotaToast(remaining, status.limitMinutes, status.policyId);
    } catch (error) {
        console.debug('[Guardian Content] Quota toast error:', error);
    }
}

function showQuotaToast(
    remainingMinutes: number,
    limitMinutes?: number,
    policyId?: string,
): void {
    if (document.getElementById('guardian-quota-toast')) {
        return;
    }

    const rounded = Math.max(1, Math.round(remainingMinutes));
    const label = rounded === 1 ? 'minute' : 'minutes';

    const container = document.createElement('div');
    container.id = 'guardian-quota-toast';
    container.style.position = 'fixed';
    container.style.right = '16px';
    container.style.bottom = '16px';
    container.style.zIndex = '2147483647';
    container.style.maxWidth = '320px';
    container.style.fontFamily =
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif";
    container.style.boxShadow = '0 10px 30px rgba(15, 23, 42, 0.35)';
    container.style.borderRadius = '12px';
    container.style.overflow = 'hidden';

    const inner = document.createElement('div');
    inner.style.background = 'rgba(15, 23, 42, 0.96)';
    inner.style.color = 'white';
    inner.style.padding = '12px 14px';
    inner.style.display = 'flex';
    inner.style.flexDirection = 'column';
    inner.style.gap = '6px';

    const header = document.createElement('div');
    header.style.display = 'flex';
    header.style.alignItems = 'center';
    header.style.justifyContent = 'space-between';

    const title = document.createElement('div');
    title.textContent = 'Time limit warning';
    title.style.fontSize = '13px';
    title.style.fontWeight = '600';

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.textContent = '×';
    closeBtn.setAttribute('aria-label', 'Close');
    closeBtn.style.background = 'transparent';
    closeBtn.style.border = 'none';
    closeBtn.style.color = '#9CA3AF';
    closeBtn.style.cursor = 'pointer';
    closeBtn.style.fontSize = '16px';
    closeBtn.onclick = () => {
        container.remove();
    };

    header.appendChild(title);
    header.appendChild(closeBtn);

    const message = document.createElement('div');
    message.style.fontSize = '12px';
    message.style.color = '#E5E7EB';
    message.textContent = limitMinutes
        ? `You have about ${rounded} ${label} left out of ${limitMinutes} minutes for this site today.`
        : `You have about ${rounded} ${label} left on this site today.`;

    const actions = document.createElement('div');
    actions.style.display = 'flex';
    actions.style.justifyContent = 'flex-end';
    actions.style.marginTop = '6px';
    actions.style.gap = '6px';

    const moreTimeBtn = document.createElement('button');
    moreTimeBtn.type = 'button';
    moreTimeBtn.textContent = 'Request more time';
    moreTimeBtn.style.background = '#4F46E5';
    moreTimeBtn.style.color = 'white';
    moreTimeBtn.style.border = 'none';
    moreTimeBtn.style.borderRadius = '9999px';
    moreTimeBtn.style.padding = '6px 10px';
    moreTimeBtn.style.fontSize = '12px';
    moreTimeBtn.style.fontWeight = '500';
    moreTimeBtn.style.cursor = 'pointer';

    moreTimeBtn.onclick = () => {
        try {
            const { url } = getPageInfo();
            if (typeof chrome !== 'undefined' && chrome.runtime?.sendMessage) {
                chrome.runtime.sendMessage({
                    type: 'REQUEST_UNBLOCK',
                    url,
                    policyId: policyId || undefined,
                    blockReason: 'Daily time limit warning',
                    timeRemainingMinutes: remainingMinutes,
                    source: 'quota_toast',
                    timestamp: Date.now(),
                });
            }
        } catch (error) {
            console.debug('[Guardian Content] Failed to send REQUEST_UNBLOCK from quota toast:', error);
        }

        moreTimeBtn.textContent = 'Request sent';
        moreTimeBtn.disabled = true;
        moreTimeBtn.style.opacity = '0.8';
    };

    actions.appendChild(moreTimeBtn);

    inner.appendChild(header);
    inner.appendChild(message);
    inner.appendChild(actions);

    container.appendChild(inner);
    document.documentElement.appendChild(container);
}
