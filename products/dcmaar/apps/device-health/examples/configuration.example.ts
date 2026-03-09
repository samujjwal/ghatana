/**
 * Comprehensive Configuration Example for DCMAAR Extension
 * 
 * This example demonstrates how to configure the extension's metrics collection,
 * processing pipeline, and sinks. It shows both the configuration structure and
 * how the extension consumes this configuration.
 */

import type { 
  ExtensionConfig, 
  MonitoringConfig, 
  MetricItem, 
  BatchEnvelope,
  RuntimeContext,
  SinkAdapter,
  SourceAdapter,
  QueueStorage
} from '../src/core/interfaces';
import { DEFAULT_CONFIG } from '../src/shared/config';

// =============================================
// 1. DEFINING THE CONFIGURATION
// =============================================

/**
 * Example of a complete configuration for the extension
 */
const extensionConfig: ExtensionConfig = {
  // Source configuration - where to get updates from
  source: {
    type: 'http',
    options: {
      // URL to fetch the initial configuration
      url: 'https://config['dcmaar'].example.com/v1/config',
      
      // How often to check for updates (in milliseconds)
      pollInterval: 300000, // 5 minutes
      
      // Authentication (if needed)
      auth: {
        type: 'jwt',
        token: 'your-jwt-token-here',
        refreshUrl: 'https://auth['dcmaar'].example.com/refresh'
      },
      
      // Headers to include in the request
      headers: {
        'User-Agent': 'DCMAAR-Extension/1.0',
        'X-Client-ID': 'your-client-id'
      }
    }
  },
  
  // Sink configuration - where to send metrics
  sink: {
    type: 'http',
    options: {
      // Endpoint to send metrics to
      endpoint: 'https://ingest['dcmaar'].example.com/v1/metrics',
      
      // Batching configuration
      batchSize: 50,          // Max number of metrics per batch
      batchTimeoutMs: 5000,   // Max time to wait before sending a batch
      maxRetries: 3,          // Number of retry attempts for failed sends
      retryDelayMs: 1000,     // Initial delay between retries (with exponential backoff)
      
      // Compression
      compress: true,         // Enable gzip compression
      
      // Authentication
      auth: {
        type: 'api-key',
        key: 'your-api-key-here',
        header: 'X-API-Key'
      },
      
      // Queue configuration (for offline support)
      queue: {
        enabled: true,        // Enable offline queue
        maxSize: 1000,       // Max number of batches to queue
        retryIntervalMs: 30000, // How often to retry failed sends
        
        // Storage configuration for the queue
        storage: {
          type: 'indexeddb',  // or 'localstorage' for smaller data
          name: 'dcmaar_metrics_queue'
        }
      }
    }
  },
  
  // Privacy and data handling
  privacy: {
    // Data redaction
    redactPII: true,
    sanitizeUrls: true,
    sanitizeQueries: true,
    
    // PII detection and handling
    piiTokens: [
      'email', 'phone', 'ssn', 'credit_card', 'ip_address'
    ],
    
    // Sensitive parameters to redact from URLs and forms
    sensitiveParams: [
      'password', 'token', 'secret', 'api_key', 'auth', 'session'
    ],
    
    // Sampling configuration
    sampling: {
      enabled: true,
      rate: 0.1, // 10% of sessions
      
      // Override sampling for specific metrics
      overrides: [
        { pattern: 'error.*', rate: 1.0 },  // Always capture errors
        { pattern: 'performance.*', rate: 0.5 } // Higher rate for performance metrics
      ]
    },
    
    // Data retention
    retentionDays: 30,
    maxEventSizeBytes: 25000 // 25KB
  },
  
  // Transport configuration
  transport: {
    // Connection settings
    maxConcurrentRequests: 3,
    requestTimeoutMs: 10000,
    
    // Retry logic
    retry: {
      maxRetries: 3,
      initialDelayMs: 1000,
      maxDelayMs: 30000,
      factor: 2,
      
      // HTTP status codes to retry on
      retryableStatusCodes: [408, 429, 500, 502, 503, 504],
      
      // Network errors to retry on
      retryableErrors: [
        'ECONNABORTED', 'ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND'
      ]
    },
    
    // Circuit breaker configuration
    circuitBreaker: {
      enabled: true,
      failureThreshold: 0.5,  // 50% failure rate trips the circuit
      resetTimeoutMs: 60000,  // 1 minute before attempting to close the circuit
      minimumRequests: 5,     // Minimum requests before circuit can trip
      windowDurationMs: 30000 // Rolling window for failure rate calculation
    }
  },
  
  // Monitoring configuration
  monitoring: {
    enabled: true,
    
    // What to capture
    captureCoreWebVitals: true,
    captureResourceTimings: true,
    captureLongTasks: true,
    captureInteractions: true,
    captureSaasContext: true,
    capturePaintTimings: true,
    
    // Performance thresholds (in ms)
    thresholds: {
      tti: 5000,           // Time to Interactive
      fid: 100,            // First Input Delay
      cls: 0.1,            // Cumulative Layout Shift
      lcp: 2500,           // Largest Contentful Paint
      fcp: 2000,           // First Contentful Paint
      tbt: 300,            // Total Blocking Time
      
      // Custom thresholds
      apiResponseTime: 1000, // API response time threshold
      pageLoad: 3000        // Full page load time
    },
    
    // Session tracking
    session: {
      maxDuration: 3600000, // 1 hour max session duration
      inactivityTimeout: 1800000, // 30 minutes of inactivity
      
      // What to track in the session
      trackPageViews: true,
      trackClicks: true,
      trackScroll: true,
      trackErrors: true,
      trackConsole: true,
      trackNetwork: true
    },
    
    // Error tracking
    errors: {
      trackUnhandledRejections: true,
      trackUncaughtExceptions: true,
      trackConsoleErrors: true,
      trackNetworkErrors: true,
      
      // Error grouping
      groupErrors: true,
      groupTimeoutMs: 1000, // Time to wait for additional context
      
      // Source maps
      sourceMaps: {
        enabled: true,
        url: 'https://assets.example.com/sourcemaps/[name].[hash].map',
        localPath: '/path/to/sourcemaps'
      }
    },
    
    // Performance monitoring
    performance: {
      // Resource timing
      resourceTimingBufferSize: 250,
      maxResourceTimingEntries: 150,
      
      // Long tasks
      longTaskThreshold: 50, // ms
      
      // Memory monitoring
      memorySamplingInterval: 60000, // 1 minute
      
      // Custom metrics
      customMetrics: [
        { name: 'time_to_first_byte', type: 'timing' },
        { name: 'dom_interactive', type: 'timing' },
        { name: 'dom_complete', type: 'timing' }
      ]
    },
    
    // User behavior
    userBehavior: {
      // Click tracking
      trackClicks: true,
      clickDebounceMs: 100,
      
      // Scroll tracking
      trackScroll: true,
      scrollThresholds: [10, 25, 50, 75, 90],
      
      // Form tracking
      trackForms: true,
      trackInputs: true,
      
      // Session replay
      sessionReplay: {
        enabled: false, // Disabled by default for privacy
        sampleRate: 0.1, // 10% of sessions
        maskInputs: true,
        maskAllText: false,
        blockClass: 'sensitive-data',
        blockSelector: '[data-no-record]',
        maskTextClass: 'mask-text'
      }
    },
    
    // Feature flags
    featureFlags: {
      'new-navigation': { enabled: false, variant: 'control' },
      'dark-mode': { enabled: true, variant: 'treatment' },
      'experimental-feature': { enabled: false, variant: 'off' }
    },
    
    // Custom dimensions
    customDimensions: {
      environment: 'production',
      version: '1.0.0',
      region: 'us-west-2',
      // Add any other custom dimensions here
    },
    
    // Heartbeat configuration
    heartbeat: {
      enabled: true,
      intervalMs: 30000, // 30 seconds
      maxMissedBeats: 3, // Consider session dead after 3 missed heartbeats
      
      // What to include in heartbeat
      includeMetrics: ['cpu', 'memory', 'network'],
      
      // Custom heartbeat handler
      onHeartbeat: (metrics) => {
        console.log('Heartbeat:', metrics);
      }
    },
    
    // Debug mode
    debug: {
      enabled: false, // Enable debug logging
      logLevel: 'warn', // 'error', 'warn', 'info', 'debug'
      
      // What to log
      logEvents: true,
      logBatches: false,
      logNetwork: false,
      
      // Custom logger
      logger: {
        log: (level, message, data) => {
          console[level](`[DCMAAR:${level.toUpperCase()}]`, message, data);
        }
      }
    },
    
    // Storage configuration
    storage: {
      type: 'indexeddb', // or 'localstorage' for smaller data
      name: 'dcmaar_metrics',
      version: 1,
      
      // Storage limits
      maxSize: 50 * 1024 * 1024, // 50MB
      maxEvents: 10000,
      
      // Encryption
      encryption: {
        enabled: true,
        key: 'auto', // 'auto' to generate a key, or provide a key
        
        // Key rotation
        keyRotation: {
          enabled: true,
          intervalDays: 30,
          retainPreviousKeys: true
        }
      }
    },
    
    // Update configuration
    update: {
      checkIntervalMs: 3600000, // 1 hour
      url: 'https://updates['dcmaar'].example.com/check',
      
      // Auto-update settings
      autoUpdate: {
        enabled: true,
        silent: false,
        
        // When to check for updates
        checkOnStartup: true,
        checkOnResume: true,
        
        // Update channels
        channel: 'stable', // 'stable', 'beta', 'alpha'
        
        // Rollout percentage (0-100)
        rolloutPercentage: 100
      },
      
      // Version requirements
      requirements: {
        minBrowserVersion: '88',
        minOsVersion: {
          windows: '10',
          macos: '10.15',
          linux: 'Ubuntu 18.04',
          android: '8.0',
          ios: '13.0'
        },
        
        // Required APIs
        requiredApis: [
          'indexedDB',
          'fetch',
          'performance',
          'serviceWorker',
          'webWorker'
        ]
      }
    },
    
    // Telemetry configuration
    telemetry: {
      enabled: true,
      
      // What to collect
      collectUsageStats: true,
      collectErrorReports: true,
      collectPerformanceStats: true,
      
      // Where to send telemetry
      endpoint: 'https://telemetry['dcmaar'].example.com/v1/telemetry',
      
      // How often to send telemetry
      flushIntervalMs: 60000, // 1 minute
      
      // What to include in telemetry
      include: [
        'version',
        'browser',
        'os',
        'device',
        'screen',
        'connection',
        'memory',
        'timing',
        'errors',
        'events'
      ]
    },
    
    // Custom plugins
    plugins: [
      // Add any custom plugins here
      // Example: new CustomAnalyticsPlugin()
    ]
  },
  
  // Identity configuration
  identity: {
    tenantId: 'your-tenant-id',
    environment: 'production', // 'development', 'staging', 'production'
    
    // User identification
    user: {
      id: 'user-123',
      email: 'user@example.com',
      name: 'John Doe',
      
      // Custom user properties
      properties: {
        plan: 'premium',
        signupDate: '2023-01-01',
        lastLogin: new Date().toISOString()
      }
    },
    
    // Session tracking
    session: {
      id: 'session-456',
      startTime: new Date().toISOString(),
      
      // Session properties
      properties: {
        referrer: document.referrer,
        landingPage: window.location.href,
        utmSource: new URLSearchParams(window.location.search).get('utm_source'),
        utmMedium: new URLSearchParams(window.location.search).get('utm_medium'),
        utmCampaign: new URLSearchParams(window.location.search).get('utm_campaign')
      }
    },
    
    // Device information
    device: {
      id: 'device-789',
      type: 'desktop', // 'desktop', 'mobile', 'tablet', 'smarttv', 'wearable', 'embedded'
      
      // Browser information
      browser: {
        name: navigator.userAgent,
        version: '', // Will be extracted from user agent
        os: '',      // Will be extracted from user agent
        
        // Screen information
        screen: {
          width: window.screen.width,
          height: window.screen.height,
          colorDepth: window.screen.colorDepth,
          pixelRatio: window.devicePixelRatio || 1
        },
        
        // Network information
        network: {
          type: 'unknown',
          effectiveType: '4g', // 'slow-2g', '2g', '3g', '4g'
          downlink: 10, // in MB/s
          rtt: 50, // in ms
          saveData: false
        },
        
        // Timezone and locale
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        locale: navigator.language,
        
        // Other capabilities
        capabilities: {
          cookies: navigator.cookieEnabled,
          localStorage: !!window.localStorage,
          sessionStorage: !!window.sessionStorage,
          serviceWorker: 'serviceWorker' in navigator,
          webWorker: 'Worker' in window,
          webAssembly: 'WebAssembly' in window,
          webGL: (() => {
            try {
              const canvas = document.createElement('canvas');
              return !!(window.WebGLRenderingContext && 
                (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')));
            } catch (e) {
              return false;
            }
          })(),
          webRTC: !!(window.RTCPeerConnection || window.webkitRTCPeerConnection),
          webSocket: 'WebSocket' in window,
          webAudio: 'AudioContext' in window || 'webkitAudioContext' in window,
          webShare: 'share' in navigator,
          webBluetooth: 'bluetooth' in navigator,
          webUSB: 'usb' in navigator,
          webNFC: 'NDEFReader' in window,
          webMIDI: 'requestMIDIAccess' in navigator,
          webVR: 'getVRDisplays' in navigator,
          webXR: 'xr' in navigator,
          webGPU: 'gpu' in navigator,
          webCodecs: 'VideoEncoder' in window,
          webHID: 'HID' in navigator,
          webSerial: 'serial' in navigator,
          webBluetooth: 'bluetooth' in navigator,
          webUSB: 'usb' in navigator,
          webNFC: 'NDEFReader' in window,
          webMIDI: 'requestMIDIAccess' in navigator,
          webVR: 'getVRDisplays' in navigator,
          webXR: 'xr' in navigator,
          webGPU: 'gpu' in navigator,
          webCodecs: 'VideoEncoder' in window,
          webHID: 'HID' in navigator,
          webSerial: 'serial' in navigator
        }
      }
    },
    
    // Custom identity providers
    providers: [
      // Example: new GoogleAnalyticsProvider('UA-XXXXX-Y')
    ]
  },
  
  // UI configuration
  ui: {
    theme: 'auto', // 'light', 'dark', 'auto'
    showNotifications: true,
    compactMode: false,
    
    // Customization
    branding: {
      logo: 'https://example.com/logo.png',
      primaryColor: '#007bff',
      secondaryColor: '#6c757d',
      
      // Custom CSS
      customCss: `
        ['dcmaar']-widget {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        }
      `
    },
    
    // Widget configuration
    widgets: {
      // Feedback widget
      feedback: {
        enabled: true,
        position: 'right', // 'left', 'right', 'top', 'bottom'
        buttonText: 'Feedback',
        formTitle: 'Send us your feedback',
        formFields: [
          { name: 'message', type: 'textarea', required: true, label: 'Your feedback' },
          { name: 'email', type: 'email', required: false, label: 'Email (optional)' },
          { 
            name: 'type', 
            type: 'select', 
            required: true, 
            label: 'Type',
            options: [
              { value: 'bug', label: 'Bug report' },
              { value: 'feature', label: 'Feature request' },
              { value: 'question', label: 'Question' },
              { value: 'other', label: 'Other' }
            ]
          },
          { 
            name: 'satisfaction', 
            type: 'rating', 
            required: false, 
            label: 'How would you rate your experience?',
            max: 5
          }
        ],
        
        // Custom submit handler
        onSubmit: async (data) => {
          console.log('Feedback submitted:', data);
          // Send to your backend
          try {
            const response = await fetch('https://api.example.com/feedback', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                ...data,
                url: window.location.href,
                userAgent: navigator.userAgent,
                timestamp: new Date().toISOString()
              })
            });
            
            if (!response.ok) {
              throw new Error('Failed to submit feedback');
            }
            
            return { success: true, message: 'Thank you for your feedback!' };
          } catch (error) {
            console.error('Error submitting feedback:', error);
            return { 
              success: false, 
              message: 'Failed to submit feedback. Please try again later.' 
            };
          }
        },
        
        // Custom success/error messages
        messages: {
          success: 'Thank you for your feedback!',
          error: 'Failed to submit feedback. Please try again later.',
          validation: {
            required: 'This field is required',
            email: 'Please enter a valid email address',
            minLength: 'Must be at least {min} characters',
            maxLength: 'Must be at most {max} characters'
          }
        }
      },
      
      // Chat widget
      chat: {
        enabled: false,
        position: 'right',
        buttonText: 'Chat with us',
        
        // Chat configuration
        config: {
          title: 'Chat with our support team',
          subtitle: 'We\'re here to help!',
          primaryColor: '#007bff',
          greeting: 'Hello! How can we help you today?',
          autoOpen: false,
          
          // Available agents
          agents: [
            { id: 'support', name: 'Support Team', status: 'online', avatar: 'https://example.com/avatars/support.png' },
            { id: 'sales', name: 'Sales Team', status: 'online', avatar: 'https://example.com/avatars/sales.png' }
          ],
          
          // Predefined messages
          quickReplies: [
            'How do I reset my password?',
            'What are your business hours?',
            'I need help with billing'
          ],
          
          // File uploads
          fileUpload: {
            enabled: true,
            maxSize: 5 * 1024 * 1024, // 5MB
            allowedTypes: ['image/*', 'application/pdf', 'text/plain'],
            maxFiles: 3
          },
          
          // Typing indicators
          typingIndicator: {
            enabled: true,
            delay: 1000 // Delay before showing "typing..."
          },
          
          // Message history
          history: {
            enabled: true,
            limit: 100,
            storage: 'local' // 'local' or 'session'
          },
          
          // Sound notifications
          sounds: {
            enabled: true,
            newMessage: 'https://example.com/sounds/new-message.mp3',
            notification: 'https://example.com/sounds/notification.mp3'
          },
          
          // Online/offline mode
          offline: {
            enabled: true,
            message: 'Our team is currently offline. Please leave us a message and we\'ll get back to you soon!',
            emailField: true
          },
          
          // Custom CSS
          customCss: `
            .chat-widget {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            }
            .chat-message.user {
              background-color: #007bff;
              color: white;
            }
          `
        },
        
        // WebSocket connection
        websocket: {
          url: 'wss://chat.example.com/ws',
          reconnectInterval: 5000,
          maxReconnectAttempts: 5
        },
        
        // Authentication
        auth: {
          enabled: true,
          type: 'jwt', // 'jwt', 'api-key', 'none'
          token: 'your-jwt-token-here',
          refreshToken: 'your-refresh-token-here',
          tokenExpiry: 3600 // 1 hour in seconds
        },
        
        // Event handlers
        onMessage: (message) => {
          console.log('New message:', message);
        },
        onError: (error) => {
          console.error('Chat error:', error);
        },
        onConnect: () => {
          console.log('Chat connected');
        },
        onDisconnect: () => {
          console.log('Chat disconnected');
        }
      },
      
      // Notification center
      notifications: {
        enabled: true,
        position: 'top-right', // 'top-left', 'top-right', 'bottom-left', 'bottom-right'
        maxNotifications: 5,
        duration: 5000, // ms
        
        // Notification types
        types: {
          info: {
            icon: 'ℹ️',
            className: 'notification-info'
          },
          success: {
            icon: '✅',
            className: 'notification-success'
          },
          warning: {
            icon: '⚠️',
            className: 'notification-warning'
          },
          error: {
            icon: '❌',
            className: 'notification-error'
          }
        },
        
        // Custom templates
        templates: {
          welcome: {
            title: 'Welcome!',
            message: 'Thanks for installing our extension!',
            type: 'success',
            duration: 0 // 0 = don't auto-dismiss
          },
          update: {
            title: 'Update available',
            message: 'A new version is available. Click to update.',
            type: 'info',
            action: {
              label: 'Update now',
              onClick: () => {
                window.location.reload();
              }
            }
          }
        },
        
        // Custom CSS
        customCss: `
          .notification {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            border-radius: 4px;
            padding: 12px 16px;
            margin-bottom: 8px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            max-width: 350px;
          }
          .notification-title {
            font-weight: 600;
            margin-bottom: 4px;
          }
        `
      },
      
      // Tooltip widget
      tooltip: {
        enabled: true,
        position: 'top', // 'top', 'right', 'bottom', 'left'
        trigger: 'hover', // 'hover', 'click', 'focus', 'manual'
        
        // Tooltip content
        content: 'This is a tooltip',
        
        // Custom styling
        className: 'custom-tooltip',
        style: {
          backgroundColor: '#333',
          color: '#fff',
          padding: '8px 12px',
          borderRadius: '4px',
          fontSize: '14px',
          maxWidth: '250px',
          boxShadow: '0 2px 10px rgba(0, 0, 0, 0.1)'
        },
        
        // Arrow
        arrow: true,
        arrowSize: 8,
        
        // Animation
        animation: 'fade', // 'fade', 'slide', 'scale', 'none'
        animationDuration: 200, // ms
        
        // Delay
        delay: 0, // ms
        
        // Events
        onShow: () => {
          console.log('Tooltip shown');
        },
        onHide: () => {
          console.log('Tooltip hidden');
        }
      },
      
      // Tour widget
      tour: {
        enabled: false,
        steps: [
          {
            target: '#feature-1',
            content: 'This is the first feature',
            placement: 'right',
            title: 'Feature 1'
          },
          {
            target: '#feature-2',
            content: 'This is the second feature',
            placement: 'bottom',
            title: 'Feature 2'
          }
        ],
        
        // Tour options
        options: {
          highlight: true,
          showProgress: true,
          showSkipButton: true,
          disableInteraction: true,
          useKeyboardNavigation: true,
          enableScrolling: true,
          scrollToElement: true,
          scrollPadding: 20,
          modal: false,
          modalZIndex: 1000,
          
          // Custom classes
          classes: {
            button: 'tour-button',
            buttonNext: 'tour-button-next',
            buttonBack: 'tour-button-back',
            buttonClose: 'tour-button-close',
            buttonSkip: 'tour-button-skip',
            badge: 'tour-badge',
            close: 'tour-close',
            content: 'tour-content',
            footer: 'tour-footer',
            highlight: 'tour-highlight',
            modal: 'tour-modal',
            overlay: 'tour-overlay',
            popover: 'tour-popover',
            progress: 'tour-progress',
            step: 'tour-step',
            title: 'tour-title',
            tooltip: 'tour-tooltip'
          },
          
          // Custom CSS
          styles: {
            popover: {
              backgroundColor: '#fff',
              borderRadius: '4px',
              boxShadow: '0 2px 15px rgba(0, 0, 0, 0.2)',
              maxWidth: '300px',
              padding: '20px'
            },
            highlight: {
              backgroundColor: 'rgba(0, 123, 255, 0.2)'
            },
            arrow: {
              color: '#fff'
            },
            close: {
              color: '#999',
              fontSize: '20px',
              top: '10px',
              right: '10px'
            },
            title: {
              color: '#333',
              fontSize: '18px',
              fontWeight: 'bold',
              marginBottom: '10px'
            },
            content: {
              color: '#666',
              fontSize: '14px',
              lineHeight: '1.5',
              marginBottom: '15px'
            },
            button: {
              backgroundColor: '#007bff',
              border: 'none',
              borderRadius: '4px',
              color: '#fff',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: '500',
              padding: '8px 16px',
              textAlign: 'center',
              textDecoration: 'none',
              transition: 'background-color 0.2s',
              ':hover': {
                backgroundColor: '#0069d9'
              },
              ':active': {
                backgroundColor: '#0062cc'
              },
              ':focus': {
                outline: 'none',
                boxShadow: '0 0 0 3px rgba(0, 123, 255, 0.25)'
              },
              ':disabled': {
                backgroundColor: '#6c757d',
                cursor: 'not-allowed',
                opacity: '0.65'
              }
            },
            buttonNext: {
              backgroundColor: '#28a745',
              ':hover': {
                backgroundColor: '#218838'
              },
              ':active': {
                backgroundColor: '#1e7e34'
              }
            },
            buttonBack: {
              backgroundColor: '#6c757d',
              marginRight: '8px',
              ':hover': {
                backgroundColor: '#5a6268'
              },
              ':active': {
                backgroundColor: '#545b62'
              }
            },
            buttonSkip: {
              backgroundColor: 'transparent',
              color: '#6c757d',
              ':hover': {
                backgroundColor: 'transparent',
                color: '#333',
                textDecoration: 'underline'
              }
            },
            progress: {
              backgroundColor: '#e9ecef',
              borderRadius: '4px',
              height: '4px',
              marginBottom: '10px',
              overflow: 'hidden',
              width: '100%'
            },
            progressBar: {
              backgroundColor: '#007bff',
              height: '100%',
              transition: 'width 0.3s ease'
            },
            badge: {
              backgroundColor: '#f8f9fa',
              borderRadius: '50%',
              color: '#212529',
              display: 'inline-block',
              fontSize: '12px',
              fontWeight: 'bold',
              height: '20px',
              lineHeight: '20px',
              marginRight: '8px',
              textAlign: 'center',
              width: '20px'
            },
            footer: {
              display: 'flex',
              justifyContent: 'space-between',
              marginTop: '15px',
              alignItems: 'center'
            },
            tooltip: {
              backgroundColor: '#333',
              borderRadius: '4px',
              color: '#fff',
              fontSize: '14px',
              maxWidth: '200px',
              padding: '8px 12px',
              position: 'absolute',
              zIndex: '1070',
              ':after': {
                content: '""',
                position: 'absolute',
                borderStyle: 'solid'
              },
              '&[data-placement^="top"]': {
                marginBottom: '10px',
                ':after': {
                  top: '100%',
                  left: '50%',
                  marginLeft: '-5px',
                  borderWidth: '5px 5px 0',
                  borderColor: '#333 transparent transparent'
                }
              },
              '&[data-placement^="right"]': {
                marginLeft: '10px',
                ':after': {
                  right: '100%',
                  top: '50%',
                  marginTop: '-5px',
                  borderWidth: '5px 5px 5px 0',
                  borderColor: 'transparent #333 transparent transparent'
                }
              },
              '&[data-placement^="bottom"]': {
                marginTop: '10px',
                ':after': {
                  bottom: '100%',
                  left: '50%',
                  marginLeft: '-5px',
                  borderWidth: '0 5px 5px',
                  borderColor: 'transparent transparent #333'
                }
              },
              '&[data-placement^="left"]': {
                marginRight: '10px',
                ':after': {
                  left: '100%',
                  top: '50%',
                  marginTop: '-5px',
                  borderWidth: '5px 0 5px 5px',
                  borderColor: 'transparent transparent transparent #333'
                }
              }
            }
          }
        },
        
        // Callbacks
        onStart: (tour) => {
          console.log('Tour started');
        },
        onEnd: (tour) => {
          console.log('Tour ended');
        },
        onSkip: (tour) => {
          console.log('Tour skipped');
        },
        onBeforeChange: (tour, fromStep, toStep) => {
          console.log(`Moving from step ${fromStep} to ${toStep}`);
        },
        onAfterChange: (tour, step) => {
          console.log(`Now at step ${step}`);
        }
      },
      
      // Hotkey widget
      hotkeys: {
        enabled: true,
        bindings: [
          {
            key: 'ctrl+shift+d',
            description: 'Toggle debug mode',
            handler: (event) => {
              console.log('Debug mode toggled');
              // Toggle debug mode
            }
          },
          {
            key: '?',
            description: 'Show help',
            handler: (event) => {
              console.log('Show help');
              // Show help dialog
            }
          },
          {
            key: 'esc',
            description: 'Close all modals',
            handler: (event) => {
              console.log('Close all modals');
              // Close all open modals
            }
          }
        ],
        
        // Options
        options: {
          filter: (event) => {
            // Don't trigger hotkeys when typing in input fields
            const target = event.target as HTMLElement;
            return !(
              target.tagName === 'INPUT' ||
              target.tagName === 'SELECT' ||
              target.tagName === 'TEXTAREA' ||
              target.isContentEditable
            );
          },
          filterPreventDefault: true,
          enableOnTags: ['INPUT', 'SELECT', 'TEXTAREA'],
          keyup: null,
          keydown: true,
          splitKey: '+',
          scope: 'all', // 'all' or a specific scope name
          element: document, // Element to attach event listeners to
          keyCode: false // Use keyCode instead of key for better browser compatibility
        },
        
        // Custom CSS
        customCss: `
          .hotkeys-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: rgba(0, 0, 0, 0.7);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 9999;
            opacity: 0;
            transition: opacity 0.3s ease;
          }
          .hotkeys-overlay.visible {
            opacity: 1;
          }
          .hotkeys-container {
            background-color: #fff;
            border-radius: 8px;
            padding: 24px;
            max-width: 600px;
            max-height: 80vh;
            overflow-y: auto;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
          }
          .hotkeys-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            padding-bottom: 12px;
            border-bottom: 1px solid #eee;
          }
          .hotkeys-title {
            font-size: 20px;
            font-weight: 600;
            margin: 0;
          }
          .hotkeys-close {
            background: none;
            border: none;
            font-size: 24px;
            cursor: pointer;
            color: #666;
            padding: 0;
            line-height: 1;
          }
          .hotkeys-list {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
            gap: 16px;
          }
          .hotkey-item {
            display: flex;
            align-items: center;
            margin-bottom: 8px;
          }
          .hotkey-keys {
            display: flex;
            margin-right: 12px;
          }
          .hotkey-key {
            background-color: #f5f5f5;
            border: 1px solid #ddd;
            border-radius: 4px;
            box-shadow: 0 1px 1px rgba(0, 0, 0, 0.1);
            color: #333;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            font-size: 12px;
            line-height: 1;
            padding: 4px 8px;
            margin-right: 4px;
          }
          .hotkey-description {
            color: #555;
            font-size: 14px;
          }
          .hotkeys-group {
            margin-bottom: 24px;
          }
          .hotkeys-group-title {
            font-size: 16px;
            font-weight: 600;
            margin: 0 0 12px 0;
            color: #333;
          }
        `
      },
      
      // Theme switcher
      themeSwitcher: {
        enabled: true,
        position: 'bottom-right', // 'top-left', 'top-right', 'bottom-left', 'bottom-right'
        themes: [
          {
            id: 'light',
            name: 'Light',
            icon: '☀️',
            apply: () => {
              document.documentElement.setAttribute('data-theme', 'light');
              localStorage.setItem('theme', 'light');
            }
          },
          {
            id: 'dark',
            name: 'Dark',
            icon: '🌙',
            apply: () => {
              document.documentElement.setAttribute('data-theme', 'dark');
              localStorage.setItem('theme', 'dark');
            }
          },
          {
            id: 'system',
            name: 'System',
            icon: '💻',
            apply: () => {
              const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
              document.documentElement.setAttribute(
                'data-theme',
                isDark ? 'dark' : 'light'
              );
              localStorage.removeItem('theme');
            }
          }
        ],
        
        // Initial theme
        defaultTheme: 'system',
        
        // Show theme name
        showLabels: true,
        
        // Show icons
        showIcons: true,
        
        // Show tooltip on hover
        showTooltip: true,
        
        // Save preference
        savePreference: true,
        
        // Listen for system theme changes
        watchSystem: true,
        
        // Custom CSS
        customCss: `
          .theme-switcher {
            position: fixed;
            bottom: 20px;
            right: 20px;
            z-index: 1000;
            display: flex;
            flex-direction: column;
            gap: 8px;
          }
          .theme-button {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            border: none;
            background: #fff;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            transition: all 0.2s ease;
          }
          .theme-button:hover {
            transform: scale(1.1);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
          }
          .theme-button.active {
            background: #007bff;
            color: #fff;
          }
          .theme-tooltip {
            position: absolute;
            right: 50px;
            background: #333;
            color: #fff;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 12px;
            white-space: nowrap;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.2s ease;
          }
          .theme-button:hover .theme-tooltip {
            opacity: 1;
          }
        `
      }
    },
    
    // Custom CSS
    customCss: `
      :root {
        --primary-color: #007bff;
        --secondary-color: #6c757d;
        --success-color: #28a745;
        --danger-color: #dc3545;
        --warning-color: #ffc107;
        --info-color: #17a2b8;
        --light-color: #f8f9fa;
        --dark-color: #343a40;
        --border-radius: 4px;
        --box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
        --transition: all 0.2s ease;
      }
      
      [data-theme="dark"] {
        --primary-color: #007bff;
        --secondary-color: #6c757d;
        --success-color: #28a745;
        --danger-color: #dc3545;
        --warning-color: #ffc107;
        --info-color: #17a2b8;
        --light-color: #343a40;
        --dark-color: #f8f9fa;
        --box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
      }
    `
  },
  
  // Custom configuration
  custom: {
    // Add any custom configuration here
    // This can be used for application-specific settings
  }
};

// =============================================
// 2. HOW THE EXTENSION LOADS CONFIGURATION
// =============================================

/**
 * The extension loads configuration through the following steps:
 * 1. First, it loads the default configuration (DEFAULT_CONFIG)
 * 2. Then it looks for configuration in the following sources (in order):
 *    - Local storage (browser.storage.local or localStorage)
 *    - Chrome storage (for Chrome extensions)
 *    - A remote configuration URL (if configured)
 *    - Inline configuration passed during initialization
 * 3. The configurations are merged, with later sources taking precedence
 * 4. The merged configuration is validated
 * 5. The configuration is applied to the extension
 */

// Example of how the extension might initialize with configuration
async function initializeExtension(config?: Partial<ExtensionConfig>) {
  // 1. Start with default configuration
  let currentConfig: ExtensionConfig = { ...DEFAULT_CONFIG };
  
  // 2. Apply any provided configuration
  if (config) {
    currentConfig = mergeConfig(currentConfig, config);
  }
  
  // 3. Initialize the source to get dynamic configuration
  const source = createSource(currentConfig.source);
  await source.init(createRuntimeContext());
  
  try {
    // 4. Load initial configuration from the source
    const signedConfig = await source.readInitialConfig();
    
    // 5. Verify the configuration signature
    if (signedConfig) {
      const isValid = await verifyConfigSignature(signedConfig);
      if (isValid) {
        // 6. Merge the remote configuration
        currentConfig = mergeConfig(currentConfig, signedConfig.payload);
      } else {
        console.warn('Configuration signature verification failed');
      }
    }
    
    // 7. Initialize the sink with the final configuration
    const sink = createSink(currentConfig.sink);
    await sink.init(createRuntimeContext(), currentConfig);
    
    // 8. Initialize the pipeline
    const pipeline = createPipeline(currentConfig);
    await pipeline.init(sink);
    
    // 9. Start the monitoring
    if (currentConfig.monitoring.enabled) {
      startMonitoring(currentConfig, pipeline);
    }
    
    // 10. Set up configuration updates
    setupConfigUpdates(source, async (newConfig) => {
      // Update the current configuration
      currentConfig = mergeConfig(currentConfig, newConfig);
      
      // Apply the new configuration
      await applyConfigUpdate(currentConfig, pipeline, sink);
    });
    
    console.log('Extension initialized with config:', currentConfig);
    return { config: currentConfig, pipeline, sink };
  } catch (error) {
    console.error('Failed to initialize extension:', error);
    throw error;
  }
}

// =============================================
// 3. HOW CONFIGURATION UPDATES ARE HANDLED
// =============================================

/**
 * The extension can receive configuration updates in several ways:
 * 1. Polling: The source checks for updates at regular intervals
 * 2. Push: The server pushes updates to the extension (e.g., via WebSocket)
 * 3. Manual: The user changes settings in the UI
 * 
 * When a configuration update is received, the extension:
 * 1. Validates the new configuration
 * 2. Merges it with the current configuration
 * 3. Applies the changes (e.g., updates monitoring settings, changes the sink, etc.)
 * 4. Persists the new configuration (if applicable)
 */

// Example of how to handle configuration updates
async function handleConfigUpdate(
  currentConfig: ExtensionConfig,
  newConfig: Partial<ExtensionConfig>,
  pipeline: Pipeline,
  sink: SinkAdapter
) {
  try {
    // 1. Merge the new configuration with the current one
    const updatedConfig = mergeConfig(currentConfig, newConfig);
    
    // 2. Apply the updated configuration
    await applyConfigUpdate(updatedConfig, pipeline, sink);
    
    // 3. Persist the configuration (if needed)
    await persistConfig(updatedConfig);
    
    console.log('Configuration updated:', updatedConfig);
    return updatedConfig;
  } catch (error) {
    console.error('Failed to update configuration:', error);
    throw error;
  }
}

// =============================================
// 4. EXAMPLE USAGE
// =============================================

// Initialize the extension with some initial configuration
const initialConfig: Partial<ExtensionConfig> = {
  monitoring: {
    enabled: true,
    captureCoreWebVitals: true,
    captureResourceTimings: true
  },
  sink: {
    type: 'http',
    options: {
      endpoint: 'https://ingest['dcmaar'].example.com/v1/metrics',
      batchSize: 10,
      batchTimeoutMs: 5000
    }
  },
  identity: {
    tenantId: 'example-tenant',
    environment: 'production',
    user: {
      id: 'user-123',
      email: 'user@example.com'
    }
  }
};

// Start the extension
initializeExtension(initialConfig)
  .then(({ config, pipeline }) => {
    console.log('Extension started with config:', config);
    
    // Example: Send a custom metric
    pipeline.processMetric({
      type: 'custom_metric',
      timestamp: Date.now(),
      priority: 'medium',
      payload: {
        name: 'button_click',
        buttonId: 'submit-button',
        page: 'homepage',
        value: 1
      }
    });
    
    // Example: Update configuration at runtime
    setTimeout(async () => {
      try {
        const newConfig = await handleConfigUpdate(
          config,
          {
            monitoring: {
              captureResourceTimings: false,
              captureInteractions: true
            }
          },
          pipeline,
          sink
        );
        console.log('Configuration updated:', newConfig);
      } catch (error) {
        console.error('Failed to update configuration:', error);
      }
    }, 10000);
  })
  .catch((error) => {
    console.error('Failed to start extension:', error);
  });

// =============================================
// 5. HELPER FUNCTIONS (simplified examples)
// =============================================

function createRuntimeContext(): RuntimeContext {
  return {
    clock: () => Date.now(),
    random: () => Math.random(),
    logger: {
      info: console.log.bind(console, '[INFO]'),
      warn: console.warn.bind(console, '[WARN]'),
      error: console.error.bind(console, '[ERROR]'),
      debug: console.debug.bind(console, '[DEBUG]')
    }
  };
}

async function verifyConfigSignature(signedConfig: SignedConfig): Promise<boolean> {
  // In a real implementation, this would verify the JWS signature
  // using the public keys from the trusted keys list
  return true; // Simplified for example
}

function createSource(config: ExtensionConfig['source']): SourceAdapter {
  // In a real implementation, this would create the appropriate source
  // based on the configuration (e.g., HTTP, file, etc.)
  return {
    kind: config.type,
    init: async () => {},
    readInitialConfig: async () => ({} as SignedConfig),
    subscribeCommands: async () => () => {},
    close: async () => {}
  };
}

function createSink(config: ExtensionConfig['sink']): SinkAdapter {
  // In a real implementation, this would create the appropriate sink
  // based on the configuration (e.g., HTTP, file, etc.)
  return {
    kind: config.type,
    init: async () => {},
    sendBatch: async () => ({ ok: true, batchId: 'batch-123', receivedAt: Date.now() }),
    close: async () => {}
  };
}

function createPipeline(config: ExtensionConfig): Pipeline {
  // In a real implementation, this would create and configure the pipeline
  return {
    start: async () => {},
    stop: async () => {},
    processMetric: async () => {},
    flushNow: async () => {},
    getStats: async () => ({
      lastAckAt: Date.now(),
      inFlight: 0,
      queued: 0
    }),
    applyCommand: async () => {}
  };
}

function startMonitoring(config: ExtensionConfig, pipeline: Pipeline) {
  // In a real implementation, this would set up the monitoring
  // based on the configuration
  console.log('Starting monitoring with config:', config.monitoring);
}

function setupConfigUpdates(
  source: SourceAdapter,
  onUpdate: (config: Partial<ExtensionConfig>) => Promise<void>
) {
  // In a real implementation, this would set up a subscription
  // to configuration updates from the source
  console.log('Setting up config updates');
}

async function applyConfigUpdate(
  config: ExtensionConfig,
  pipeline: Pipeline,
  sink: SinkAdapter
) {
  // In a real implementation, this would apply the configuration
  // to the relevant components
  console.log('Applying config update:', config);
}

async function persistConfig(config: ExtensionConfig) {
  // In a real implementation, this would save the configuration
  // to persistent storage
  console.log('Persisting config');
}

// Export for use in other modules
export { extensionConfig, initializeExtension };
