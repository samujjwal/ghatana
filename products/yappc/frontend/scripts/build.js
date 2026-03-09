#!/usr/bin/env node

/**
 * Production Build Script
 * 
 * This script handles the production build process for the YAPPC App Creator.
 * It includes build optimization, validation, and artifact generation.
 */

const path = require('path');
const fs = require('fs-extra');
const chalk = require('chalk');
const webpack = require('webpack');
const { execSync } = require('child_process');
const { gzipSync } = require('zlib');
const { compress } = require('brotli');
const filesize = require('filesize');
const { checkBrowsers } = require('react-dev-utils/browsersHelper');
const formatWebpackMessages = require('react-dev-utils/formatWebpackMessages');
const FileSizeReporter = require('react-dev-utils/FileSizeReporter');
const printBuildError = require('react-dev-utils/printBuildError');

const measureFileSizesBeforeBuild = FileSizeReporter.measureFileSizesBeforeBuild;
const printFileSizesAfterBuild = FileSizeReporter.printFileSizesAfterBuild;

// These sizes are pretty large. We'll warn for bundles exceeding them.
const WARN_AFTER_BUNDLE_GZIP_SIZE = 512 * 1024; // 512kb
const WARN_AFTER_CHUNK_GZIP_SIZE = 1024 * 1024; // 1mb

// Print environment info
console.log(chalk.bold('Build environment:'));
console.log(`Node version: ${process.version}`);
console.log(`Build mode: ${process.env.NODE_ENV}`);
console.log(`CI: ${process.env.CI ? 'true' : 'false'}`);
console.log();

// Make sure the build script is executed from the project root
const appDirectory = fs.realpathSync(process.cwd());
process.chdir(appDirectory);

// Set environment variables
process.env.BABEL_ENV = 'production';
process.env.NODE_ENV = 'production';

// Makes the script crash on unhandled rejections instead of silently
// ignoring them. In the future, promise rejections that are not handled will
// terminate the Node.js process with a non-zero exit code.
process.on('unhandledRejection', err => {
  throw err;
});

// Load environment variables
require('dotenv').config();

// Generate build ID
const buildId = Date.now().toString();
process.env.BUILD_ID = buildId;

// Parse command line arguments
const argv = process.argv.slice(2);
const writeStatsJson = argv.indexOf('--stats') !== -1;
const analyze = argv.indexOf('--analyze') !== -1;
const skipTypeCheck = argv.indexOf('--skip-typecheck') !== -1;

if (analyze) {
  process.env.ANALYZE = 'true';
}

// Start the build process
checkBrowsers(appDirectory)
  .then(() => {
    // First, read the current file sizes in build directory.
    // This lets us display how much they changed later.
    return measureFileSizesBeforeBuild('apps/web/dist');
  })
  .then(previousFileSizes => {
    // Clean the build directory
    fs.emptyDirSync('apps/web/dist');
    
    // Run type checking if not skipped
    if (!skipTypeCheck) {
      console.log(chalk.bold('Running type checking...'));
      try {
        execSync('pnpm typecheck', { stdio: 'inherit' });
        console.log(chalk.green('✓ Type checking passed'));
      } catch (error) {
        console.log(chalk.red('✗ Type checking failed'));
        process.exit(1);
      }
    }
    
    // Run ESLint if not in CI
    if (!process.env.CI) {
      console.log(chalk.bold('Running ESLint...'));
      try {
        execSync('pnpm lint', { stdio: 'inherit' });
        console.log(chalk.green('✓ ESLint passed'));
      } catch (error) {
        console.log(chalk.yellow('⚠ ESLint found issues'));
        // Continue with build despite ESLint issues
      }
    }
    
    // Copy public files
    fs.copySync('apps/web/public', 'apps/web/dist', {
      dereference: true,
      filter: file => file !== 'apps/web/public/index.html',
    });
    
    // Start the webpack build
    console.log(chalk.bold('Creating an optimized production build...'));
    console.time('Build time');
    
    // Get webpack config
    const configFactory = require('../apps/web/build.config');
    const viteConfig = require('../vite.config');
    const config = configFactory(viteConfig);
    
    // Create compiler
    const compiler = webpack(config);
    
    return new Promise((resolve, reject) => {
      compiler.run((err, stats) => {
        let messages;
        
        if (err) {
          if (!err.message) {
            return reject(err);
          }
          
          messages = formatWebpackMessages({
            errors: [err.message],
            warnings: [],
          });
        } else {
          messages = formatWebpackMessages(
            stats.toJson({ all: false, warnings: true, errors: true })
          );
        }
        
        if (messages.errors.length) {
          // Only keep the first error. Others are often indicative
          // of the same problem, but confuse the reader with noise.
          if (messages.errors.length > 1) {
            messages.errors.length = 1;
          }
          return reject(new Error(messages.errors.join('\n\n')));
        }
        
        // Write the webpack stats json if requested
        if (writeStatsJson) {
          fs.writeFileSync(
            path.join('apps/web/dist', 'webpack-stats.json'),
            JSON.stringify(stats.toJson(), null, 2)
          );
        }
        
        return resolve({
          stats,
          previousFileSizes,
          warnings: messages.warnings,
        });
      });
    });
  })
  .then(
    ({ stats, previousFileSizes, warnings }) => {
      console.timeEnd('Build time');
      
      if (warnings.length) {
        console.log(chalk.yellow('Compiled with warnings.\n'));
        console.log(warnings.join('\n\n'));
        console.log(
          `\nSearch for the ${ 
            chalk.underline(chalk.yellow('keywords')) 
            } to learn more about each warning.`
        );
        console.log(
          `To ignore, add ${ 
            chalk.cyan('// eslint-disable-next-line') 
            } to the line before.\n`
        );
      } else {
        console.log(chalk.green('Compiled successfully.\n'));
      }
      
      console.log('File sizes after gzip:\n');
      printFileSizesAfterBuild(
        stats,
        previousFileSizes,
        'apps/web/dist',
        WARN_AFTER_BUNDLE_GZIP_SIZE,
        WARN_AFTER_CHUNK_GZIP_SIZE
      );
      console.log();
      
      // Generate build info file
      const buildInfo = {
        version: require('../package.json').version,
        buildId: process.env.BUILD_ID,
        buildTime: new Date().toISOString(),
        nodeVersion: process.version,
        environment: process.env.NODE_ENV,
        ci: !!process.env.CI,
      };
      
      fs.writeFileSync(
        path.join('apps/web/dist', 'build-info.json'),
        JSON.stringify(buildInfo, null, 2)
      );
      
      // Calculate and report Brotli sizes
      const assets = stats.toJson().assets || [];
      const brotliSizes = {};
      let hasLargeAssets = false;
      
      assets
        .filter(asset => /\.(js|css)$/.test(asset.name))
        .forEach(asset => {
          const filePath = path.join('apps/web/dist', asset.name);
          const fileContent = fs.readFileSync(filePath);
          const size = fileContent.length;
          const gzipSize = gzipSync(fileContent).length;
          const brotliSize = compress(fileContent).length;
          
          brotliSizes[asset.name] = brotliSize;
          
          if (brotliSize > WARN_AFTER_BUNDLE_GZIP_SIZE) {
            hasLargeAssets = true;
          }
        });
      
      if (Object.keys(brotliSizes).length > 0) {
        console.log('File sizes after Brotli:\n');
        Object.keys(brotliSizes).forEach(file => {
          const size = brotliSizes[file];
          const sizeLabel = filesize(size);
          const isLarge = size > WARN_AFTER_BUNDLE_GZIP_SIZE;
          
          console.log(
            `  ${isLarge ? chalk.yellow(sizeLabel) : chalk.green(sizeLabel)}\t${
              isLarge ? chalk.yellow(file) : chalk.cyan(file)
            }`
          );
        });
        console.log();
      }
      
      if (hasLargeAssets) {
        console.log(
          chalk.yellow(
            '\nWarning: Some assets exceed the recommended size limit.\n' +
              'Consider code splitting or lazy loading to improve performance.\n'
          )
        );
      }
      
      // Run bundle analysis if requested
      if (analyze) {
        console.log(chalk.bold('Bundle analysis report generated.'));
        console.log('See bundle-report.html in the project root.\n');
      }
      
      console.log(chalk.green('✓ Build completed successfully!'));
      console.log(
        `The ${chalk.cyan('dist')} folder is ready to be deployed.\n`
      );
    },
    err => {
      console.log(chalk.red('Failed to compile.\n'));
      printBuildError(err);
      process.exit(1);
    }
  )
  .catch(err => {
    if (err && err.message) {
      console.log(err.message);
    }
    process.exit(1);
  });
