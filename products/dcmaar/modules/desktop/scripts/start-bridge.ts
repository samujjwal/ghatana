#!/usr/bin/env node
/**
 * CLI script to start the Bridge Service
 */

import { BridgeService } from '../src/libs/bridge/bridgeService';
import { program } from 'commander';
import chalk from 'chalk';

// Parse command line arguments
program
  .option('-p, --port <port>', 'Port to run the bridge service on', '3001')
  .option('-h, --host <host>', 'Host to bind the bridge service to', '0.0.0.0')
  .option('--no-compression', 'Disable compression')
  .option('--no-circuit-breaker', 'Disable circuit breaker')
  .parse(process.argv);

const options = program.opts();

// Create and start the bridge service
const service = new BridgeService({
  port: parseInt(options.port, 10),
  host: options.host,
  enableCompression: options.compression,
  enableCircuitBreaker: options.circuitBreaker,
});

// Handle process termination
const shutdown = async () => {
  console.log(chalk.yellow('\nShutting down bridge service...'));
  try {
    await service.stop();
    console.log(chalk.green('✅ Bridge service stopped successfully'));
    process.exit(0);
  } catch (error) {
    console.error(chalk.red('❌ Error stopping bridge service:'), error);
    process.exit(1);
  }
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

// Start the service
service
  .start()
  .then(() => {
    console.log(chalk.green(`✅ Bridge service started on ${options.host}:${options.port}`));
    console.log(chalk.dim('Press Ctrl+C to stop the service'));
  })
  .catch((error) => {
    console.error(chalk.red('❌ Failed to start bridge service:'), error);
    process.exit(1);
  });

// Handle uncaught exceptions
process.on('uncaughtException', (error) => {
  console.error(chalk.red('Uncaught exception:'), error);
  shutdown().catch(() => process.exit(1));
});

process.on('unhandledRejection', (reason) => {
  console.error(chalk.red('Unhandled rejection:'), reason);
  shutdown().catch(() => process.exit(1));
});
