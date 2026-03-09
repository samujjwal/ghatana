// Type definitions for environment variables
declare namespace NodeJS {
  interface ProcessEnv {
    // Node Environment
    NODE_ENV: 'development' | 'production' | 'test';
    
    // Build Configuration
    BROWSER?: 'chrome' | 'firefox' | 'edge';
    PORT?: string;
    
    // Playwright Test Environment
    PW_DEBUG?: 'true' | 'false';
    PW_TEST_SERVER_PORT?: string;
    PW_TEST_SERVER_HOST?: string;
    PW_TEST_BASE_URL?: string;
    PW_EXTRA_LAUNCH_OPTIONS?: string;
    PW_PROFILE_DIR?: string;
    HEADLESS?: 'true' | 'false' | '1' | '0';
    PRESERVE_PW_PROFILE?: 'true' | 'false';
    
    // Feature Flags
    VITE_ENABLE_PERF_MONITORING?: 'true' | 'false';
    PW_ENABLE_COVERAGE?: 'true' | 'false';
    __DCMAAR_TEST_HELPERS?: 'true' | 'false';
    
    // API and Endpoints
    VITE_INGEST_ENDPOINT?: string;
    
    // Development/Testing
    DEBUG?: string;
    NODE_OPTIONS?: string;
    
    // Add other environment variables as needed
    [key: string]: string | undefined;
  }
}
