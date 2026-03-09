/**
 * Vite Performance Optimization Configuration
 * Extends base config with production optimizations
 */

import { defineConfig, mergeConfig } from 'vite';

import baseConfig from './vite.config';

export default mergeConfig(
  baseConfig,
  defineConfig({
    build: {
      // Enable minification
      minify: 'terser',
      
      // Terser options for better compression
      terserOptions: {
        compress: {
          drop_console: true, // Remove console.log in production
          drop_debugger: true,
          pure_funcs: ['console.log', 'console.debug'], // Remove specific functions
          passes: 2, // Multiple passes for better compression
        },
        mangle: {
          safari10: true, // Safari 10 compatibility
        },
        format: {
          comments: false, // Remove comments
        },
      },
      
      // Code splitting configuration
      rollupOptions: {
        output: {
          // Manual chunks for better caching
          manualChunks: (id) => {
            // Vendor chunks
            if (id.includes('node_modules')) {
              if (id.includes('webextension-polyfill')) {
                return 'vendor-polyfill';
              }
              if (id.includes('@tensorflow')) {
                return 'vendor-tf';
              }
              if (id.includes('pako')) {
                return 'vendor-compression';
              }
              return 'vendor';
            }
            
            // Feature-based chunks
            if (id.includes('/background/')) {
              return 'background';
            }
            if (id.includes('/content/')) {
              return 'content';
            }
            if (id.includes('/ui/')) {
              return 'ui';
            }
          },
          
          // Optimize chunk file names
          chunkFileNames: 'chunks/[name]-[hash].js',
          entryFileNames: '[name].js',
          assetFileNames: 'assets/[name]-[hash][extname]',
        },
        
        // Tree-shaking configuration
        treeshake: {
          moduleSideEffects: false,
          propertyReadSideEffects: false,
          tryCatchDeoptimization: false,
        },
      },
      
      // Source map configuration
      sourcemap: false, // Disable in production for smaller bundle
      
      // Target modern browsers for better optimization
      target: ['chrome100', 'firefox100', 'edge100'],
      
      // CSS code splitting
      cssCodeSplit: true,
      
      // Optimize dependencies
      commonjsOptions: {
        include: [/node_modules/],
        transformMixedEsModules: true,
      },
      
      // Report compressed size
      reportCompressedSize: true,
      
      // Chunk size warning limit
      chunkSizeWarningLimit: 1000, // 1MB
    },
    
    // Optimize dependencies
    optimizeDeps: {
      include: [
        'webextension-polyfill',
        'eventemitter3',
        'zod',
      ],
      exclude: [
        '@tensorflow/tfjs', // Lazy load TensorFlow
        '@tensorflow/tfjs-backend-webgl',
      ],
    },
    
    // Enable esbuild optimizations
    esbuild: {
      legalComments: 'none',
      treeShaking: true,
      minifyIdentifiers: true,
      minifySyntax: true,
      minifyWhitespace: true,
    },
  })
);
