/**
 * Build Optimization Configuration
 * 
 * This file configures optimization strategies for the YAPPC App Creator.
 */

const path = require('path');
const zlib = require('zlib');

/**
 * Performance budgets
 */
const PERFORMANCE_BUDGETS = {
  maxEntrypointSize: 250000, // 250kb
  maxAssetSize: 450000, // 450kb
  maxJsSize: 250000, // 250kb
  maxCssSize: 50000, // 50kb
  maxFirstContentfulPaint: 2000, // 2s
  maxLargestContentfulPaint: 2500, // 2.5s
  maxTotalBlockingTime: 300, // 300ms
  maxCumulativeLayoutShift: 0.1, // 0.1
};

/**
 * Code splitting configuration
 */
const CODE_SPLITTING = {
  chunks: 'all',
  cacheGroups: {
    // Framework and runtime
    framework: {
      test: /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom)[\\/]/,
      name: 'framework',
      chunks: 'all',
      priority: 40,
      enforce: true,
    },
    // UI library
    ui: {
      test: /[\\/]libs[\\/]ui[\\/]/,
      name: 'ui',
      chunks: 'all',
      priority: 30,
      enforce: true,
    },
    // Third-party libraries
    vendor: {
      test: /[\\/]node_modules[\\/]/,
      name: 'vendor',
      chunks: 'all',
      priority: 20,
    },
    // Common code used in multiple chunks
    common: {
      name: 'common',
      minChunks: 2,
      chunks: 'all',
      priority: 10,
      reuseExistingChunk: true,
      enforce: true,
    },
  },
};

/**
 * Terser configuration for JS minification
 */
const TERSER_CONFIG = {
  compress: {
    ecma: 2020,
    comparisons: false,
    inline: 2,
    module: true,
    drop_console: process.env.NODE_ENV === 'production',
    drop_debugger: process.env.NODE_ENV === 'production',
    pure_funcs: process.env.NODE_ENV === 'production' ? ['console.log', 'console.debug', 'console.info'] : [],
  },
  mangle: {
    safari10: true,
  },
  output: {
    ecma: 2020,
    comments: false,
    ascii_only: true,
  },
  sourceMap: process.env.SOURCE_MAPS === 'true',
};

/**
 * CSS optimization configuration
 */
const CSS_OPTIMIZATION = {
  preset: [
    'default',
    {
      discardComments: { removeAll: true },
      normalizeWhitespace: process.env.NODE_ENV === 'production',
      minifyFontValues: true,
      minifyGradients: true,
      mergeIdents: false,
      reduceIdents: false,
      autoprefixer: { add: true },
    },
  ],
};

/**
 * Image optimization configuration
 */
const IMAGE_OPTIMIZATION = {
  gifsicle: {
    interlaced: true,
    optimizationLevel: 3,
  },
  mozjpeg: {
    quality: 80,
    progressive: true,
  },
  optipng: {
    optimizationLevel: 5,
  },
  pngquant: {
    quality: [0.7, 0.9],
    speed: 4,
  },
  svgo: {
    plugins: [
      {
        name: 'preset-default',
        params: {
          overrides: {
            removeViewBox: false,
            cleanupIDs: false,
          },
        },
      },
    ],
  },
  webp: {
    quality: 85,
  },
};

/**
 * Compression configuration
 */
const COMPRESSION_CONFIG = {
  gzip: {
    filename: '[path][base].gz',
    algorithm: 'gzip',
    test: /\.(js|css|html|svg)$/,
    threshold: 10240, // 10kb
    minRatio: 0.8,
  },
  brotli: {
    filename: '[path][base].br',
    algorithm: 'brotliCompress',
    test: /\.(js|css|html|svg)$/,
    compressionOptions: {
      params: {
        [zlib.constants.BROTLI_PARAM_QUALITY]: 11,
      },
    },
    threshold: 10240, // 10kb
    minRatio: 0.8,
  },
};

/**
 * Source map configuration
 */
const SOURCE_MAP_CONFIG = {
  development: 'eval-cheap-module-source-map',
  production: process.env.SOURCE_MAPS === 'true' ? 'source-map' : false,
};

/**
 * Bundle analyzer configuration
 */
const BUNDLE_ANALYZER_CONFIG = {
  analyzerMode: 'static',
  reportFilename: '../bundle-report.html',
  openAnalyzer: !process.env.CI,
  generateStatsFile: true,
  statsFilename: '../bundle-stats.json',
};

/**
 * Service worker configuration
 */
const SERVICE_WORKER_CONFIG = {
  swSrc: './src/service-worker.js',
  swDest: 'service-worker.js',
  exclude: [/\.map$/, /asset-manifest\.json$/],
};

/**
 * Export optimization configurations
 */
module.exports = {
  PERFORMANCE_BUDGETS,
  CODE_SPLITTING,
  TERSER_CONFIG,
  CSS_OPTIMIZATION,
  IMAGE_OPTIMIZATION,
  COMPRESSION_CONFIG,
  SOURCE_MAP_CONFIG,
  BUNDLE_ANALYZER_CONFIG,
  SERVICE_WORKER_CONFIG,
};
