/**
 * Production Build Configuration
 * 
 * This file configures the production build process for the YAPPC App Creator.
 */

const path = require('path');
const zlib = require('zlib');
const CompressionPlugin = require('compression-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const { BundleAnalyzerPlugin } = require('webpack-bundle-analyzer');
const { SubresourceIntegrityPlugin } = require('webpack-subresource-integrity');
const { WebpackManifestPlugin } = require('webpack-manifest-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');
const { InjectManifest } = require('workbox-webpack-plugin');
const CopyPlugin = require('copy-webpack-plugin');
const ImageMinimizerPlugin = require('image-minimizer-webpack-plugin');
const { DefinePlugin } = require('webpack');

/**
 * Build version from package.json or environment
 */
const packageJson = require('../../package.json');
const buildVersion = process.env.BUILD_VERSION || packageJson.version;
const buildId = process.env.BUILD_ID || Date.now().toString();

/**
 * Environment configuration
 */
const isProd = process.env.NODE_ENV === 'production';
const isAnalyze = process.env.ANALYZE === 'true';
const isCI = process.env.CI === 'true';
const enableSourceMaps = process.env.SOURCE_MAPS === 'true';
const compressionEnabled = process.env.COMPRESSION !== 'false';
const enableSRI = process.env.SRI !== 'false';

/**
 * Performance budgets
 */
const PERFORMANCE_BUDGETS = {
  maxEntrypointSize: 250000, // 250kb
  maxAssetSize: 450000, // 450kb
};

/**
 * Build configuration factory
 */
module.exports = function createBuildConfig(viteConfig) {
  return {
    /**
     * Build mode
     */
    mode: isProd ? 'production' : 'development',

    /**
     * Build target
     */
    target: ['web', 'es2020'],

    /**
     * Output configuration
     */
    output: {
      path: path.resolve(__dirname, 'dist'),
      publicPath: '/',
      filename: isProd ? 'assets/[name].[contenthash:8].js' : 'assets/[name].js',
      chunkFilename: isProd ? 'assets/[name].[contenthash:8].chunk.js' : 'assets/[name].chunk.js',
      assetModuleFilename: 'assets/[name].[hash:8][ext]',
      crossOriginLoading: enableSRI ? 'anonymous' : false,
      clean: true,
    },

    /**
     * Optimization configuration
     */
    optimization: {
      minimize: isProd,
      minimizer: [
        new TerserPlugin({
          terserOptions: {
            compress: {
              ecma: 2020,
              comparisons: false,
              inline: 2,
              drop_console: isProd && !isCI,
            },
            mangle: {
              safari10: true,
            },
            output: {
              ecma: 2020,
              comments: false,
              ascii_only: true,
            },
          },
          parallel: true,
        }),
        new CssMinimizerPlugin({
          minimizerOptions: {
            preset: [
              'default',
              {
                discardComments: { removeAll: true },
                normalizeWhitespace: isProd,
              },
            ],
          },
        }),
        new ImageMinimizerPlugin({
          minimizer: {
            implementation: ImageMinimizerPlugin.imageminMinify,
            options: {
              plugins: [
                ['gifsicle', { interlaced: true }],
                ['jpegtran', { progressive: true }],
                ['optipng', { optimizationLevel: 5 }],
                ['svgo', { plugins: [{ name: 'preset-default' }] }],
              ],
            },
          },
        }),
      ],
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          vendors: {
            test: /[\\/]node_modules[\\/]/,
            name: 'vendors',
            chunks: 'all',
            priority: 10,
          },
          common: {
            name: 'common',
            minChunks: 2,
            chunks: 'all',
            priority: 5,
            reuseExistingChunk: true,
            enforce: true,
          },
        },
      },
      runtimeChunk: 'single',
    },

    /**
     * Performance hints
     */
    performance: {
      maxEntrypointSize: PERFORMANCE_BUDGETS.maxEntrypointSize,
      maxAssetSize: PERFORMANCE_BUDGETS.maxAssetSize,
      hints: isProd ? 'warning' : false,
    },

    /**
     * Source maps
     */
    devtool: isProd
      ? enableSourceMaps
        ? 'source-map'
        : false
      : 'eval-cheap-module-source-map',

    /**
     * Stats configuration
     */
    stats: {
      children: false,
      entrypoints: false,
      modules: false,
    },

    /**
     * Plugins
     */
    plugins: [
      // Define environment variables
      new DefinePlugin({
        'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'development'),
        'process.env.BUILD_VERSION': JSON.stringify(buildVersion),
        'process.env.BUILD_ID': JSON.stringify(buildId),
        'process.env.BUILD_TIME': JSON.stringify(new Date().toISOString()),
      }),

      // Generate asset manifest
      new WebpackManifestPlugin({
        fileName: 'asset-manifest.json',
        publicPath: '/',
        generate: (seed, files, entrypoints) => {
          const manifestFiles = files.reduce((manifest, file) => {
            manifest[file.name] = file.path;
            return manifest;
          }, seed);

          const entrypointFiles = entrypoints.main.filter(
            fileName => !fileName.endsWith('.map')
          );

          return {
            files: manifestFiles,
            entrypoints: entrypointFiles,
            version: buildVersion,
            buildId,
          };
        },
      }),

      // Copy static files
      new CopyPlugin({
        patterns: [
          {
            from: 'public',
            to: '.',
            globOptions: {
              ignore: ['**/index.html'],
            },
          },
        ],
      }),

      // Enable SRI
      ...(enableSRI
        ? [
            new SubresourceIntegrityPlugin({
              hashFuncNames: ['sha384'],
            }),
          ]
        : []),

      // Enable compression
      ...(isProd && compressionEnabled
        ? [
            new CompressionPlugin({
              filename: '[path][base].gz',
              algorithm: 'gzip',
              test: /\.(js|css|html|svg)$/,
              threshold: 10240,
              minRatio: 0.8,
            }),
            new CompressionPlugin({
              filename: '[path][base].br',
              algorithm: 'brotliCompress',
              test: /\.(js|css|html|svg)$/,
              compressionOptions: {
                params: {
                  [zlib.constants.BROTLI_PARAM_QUALITY]: 11,
                },
              },
              threshold: 10240,
              minRatio: 0.8,
            }),
          ]
        : []),

      // Service worker
      ...(isProd
        ? [
            new InjectManifest({
              swSrc: './src/service-worker.js',
              swDest: 'service-worker.js',
              exclude: [/\.map$/, /asset-manifest\.json$/],
            }),
          ]
        : []),

      // Bundle analyzer
      ...(isAnalyze
        ? [
            new BundleAnalyzerPlugin({
              analyzerMode: 'static',
              reportFilename: '../bundle-report.html',
              openAnalyzer: !isCI,
            }),
          ]
        : []),
    ],

    /**
     * Resolve configuration
     */
    resolve: {
      extensions: ['.js', '.jsx', '.ts', '.tsx', '.json'],
      alias: viteConfig.resolve.alias,
    },
  };
};
