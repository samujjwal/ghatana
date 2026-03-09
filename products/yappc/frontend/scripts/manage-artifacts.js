#!/usr/bin/env node

/**
 * Build Artifact Management Script
 * 
 * This script manages build artifacts for the YAPPC App Creator.
 * It handles artifact storage, versioning, and promotion.
 */

const path = require('path');
const fs = require('fs-extra');
const chalk = require('chalk');
const archiver = require('archiver');
const { execSync } = require('child_process');
const crypto = require('crypto');

// Parse command line arguments
const argv = process.argv.slice(2);
const command = argv[0];
const buildId = argv[1];
const environment = argv[2] || 'production';

// Configuration
const artifactsDir = path.join(process.cwd(), 'artifacts');
const buildDir = path.join(process.cwd(), 'apps/web/dist');
const manifestPath = path.join(artifactsDir, 'manifest.json');

// Ensure artifacts directory exists
fs.ensureDirSync(artifactsDir);

// Create manifest if it doesn't exist
if (!fs.existsSync(manifestPath)) {
  fs.writeJsonSync(manifestPath, { artifacts: [] }, { spaces: 2 });
}

/**
 * Generate build ID if not provided
 */
function generateBuildId() {
  const timestamp = Date.now();
  const randomString = crypto.randomBytes(4).toString('hex');
  return `build-${timestamp}-${randomString}`;
}

/**
 * Calculate SHA-256 hash of a file
 */
function calculateFileHash(filePath) {
  const fileBuffer = fs.readFileSync(filePath);
  const hashSum = crypto.createHash('sha256');
  hashSum.update(fileBuffer);
  return hashSum.digest('hex');
}

/**
 * Create a zip archive of the build
 */
function createArchive(buildId, outputPath) {
  return new Promise((resolve, reject) => {
    const output = fs.createWriteStream(outputPath);
    const archive = archiver('zip', {
      zlib: { level: 9 }, // Maximum compression
    });

    output.on('close', () => {
      console.log(chalk.green(`✓ Archive created: ${outputPath} (${archive.pointer()} bytes)`));
      resolve(outputPath);
    });

    archive.on('error', (err) => {
      reject(err);
    });

    archive.pipe(output);
    archive.directory(buildDir, false);
    archive.finalize();
  });
}

/**
 * Store build artifact
 */
async function storeArtifact(buildId, environment) {
  console.log(chalk.bold(`Storing build artifact for ${buildId} (${environment})...`));

  // Check if build directory exists
  if (!fs.existsSync(buildDir)) {
    console.error(chalk.red('Build directory not found. Run build script first.'));
    process.exit(1);
  }

  // Create artifact directory
  const artifactDir = path.join(artifactsDir, buildId);
  fs.ensureDirSync(artifactDir);

  // Create artifact metadata
  const buildInfoPath = path.join(buildDir, 'build-info.json');
  let buildInfo = {};
  
  if (fs.existsSync(buildInfoPath)) {
    buildInfo = fs.readJsonSync(buildInfoPath);
  }

  const metadata = {
    id: buildId,
    environment,
    timestamp: new Date().toISOString(),
    version: buildInfo.version || process.env.npm_package_version,
    node: process.version,
    commit: execSync('git rev-parse HEAD').toString().trim(),
    branch: execSync('git rev-parse --abbrev-ref HEAD').toString().trim(),
    buildInfo,
  };

  // Save metadata
  fs.writeJsonSync(path.join(artifactDir, 'metadata.json'), metadata, { spaces: 2 });

  // Create archive
  const archivePath = path.join(artifactDir, `${buildId}.zip`);
  await createArchive(buildId, archivePath);

  // Calculate hash
  const hash = calculateFileHash(archivePath);
  metadata.hash = hash;
  fs.writeJsonSync(path.join(artifactDir, 'metadata.json'), metadata, { spaces: 2 });

  // Update manifest
  const manifest = fs.readJsonSync(manifestPath);
  manifest.artifacts.push({
    id: buildId,
    environment,
    timestamp: metadata.timestamp,
    version: metadata.version,
    hash,
    path: path.relative(artifactsDir, archivePath),
  });
  fs.writeJsonSync(manifestPath, manifest, { spaces: 2 });

  console.log(chalk.green(`✓ Artifact ${buildId} stored successfully`));
  console.log(`  Environment: ${environment}`);
  console.log(`  Version: ${metadata.version}`);
  console.log(`  Commit: ${metadata.commit}`);
  console.log(`  Hash: ${hash}`);
  console.log(`  Path: ${artifactDir}`);

  return buildId;
}

/**
 * List all artifacts
 */
function listArtifacts() {
  console.log(chalk.bold('Available artifacts:'));

  const manifest = fs.readJsonSync(manifestPath);
  
  if (manifest.artifacts.length === 0) {
    console.log('No artifacts found.');
    return;
  }

  // Sort by timestamp (newest first)
  manifest.artifacts.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));

  manifest.artifacts.forEach((artifact) => {
    const artifactPath = path.join(artifactsDir, artifact.id);
    const metadataPath = path.join(artifactPath, 'metadata.json');
    
    if (fs.existsSync(metadataPath)) {
      const metadata = fs.readJsonSync(metadataPath);
      console.log(chalk.cyan(`ID: ${artifact.id}`));
      console.log(`  Environment: ${artifact.environment}`);
      console.log(`  Timestamp: ${artifact.timestamp}`);
      console.log(`  Version: ${artifact.version}`);
      console.log(`  Commit: ${metadata.commit}`);
      console.log(`  Hash: ${artifact.hash}`);
      console.log();
    }
  });
}

/**
 * Retrieve artifact
 */
function retrieveArtifact(buildId) {
  console.log(chalk.bold(`Retrieving artifact ${buildId}...`));

  const artifactDir = path.join(artifactsDir, buildId);
  const archivePath = path.join(artifactDir, `${buildId}.zip`);
  const metadataPath = path.join(artifactDir, 'metadata.json');

  if (!fs.existsSync(artifactDir) || !fs.existsSync(archivePath)) {
    console.error(chalk.red(`Artifact ${buildId} not found.`));
    process.exit(1);
  }

  // Verify hash
  const metadata = fs.readJsonSync(metadataPath);
  const hash = calculateFileHash(archivePath);

  if (hash !== metadata.hash) {
    console.error(chalk.red(`Hash mismatch for artifact ${buildId}.`));
    console.error(`Expected: ${metadata.hash}`);
    console.error(`Actual: ${hash}`);
    process.exit(1);
  }

  console.log(chalk.green(`✓ Artifact ${buildId} verified successfully`));
  console.log(`  Environment: ${metadata.environment}`);
  console.log(`  Version: ${metadata.version}`);
  console.log(`  Commit: ${metadata.commit}`);
  console.log(`  Hash: ${hash}`);

  // Extract archive to temp directory
  const extractDir = path.join(process.cwd(), 'tmp', buildId);
  fs.ensureDirSync(extractDir);
  fs.emptyDirSync(extractDir);

  console.log(`Extracting to ${extractDir}...`);
  execSync(`unzip -q ${archivePath} -d ${extractDir}`);

  console.log(chalk.green(`✓ Artifact extracted to ${extractDir}`));
  return extractDir;
}

/**
 * Promote artifact to another environment
 */
async function promoteArtifact(buildId, targetEnvironment) {
  console.log(chalk.bold(`Promoting artifact ${buildId} to ${targetEnvironment}...`));

  const artifactDir = path.join(artifactsDir, buildId);
  const metadataPath = path.join(artifactDir, 'metadata.json');

  if (!fs.existsSync(artifactDir) || !fs.existsSync(metadataPath)) {
    console.error(chalk.red(`Artifact ${buildId} not found.`));
    process.exit(1);
  }

  // Read metadata
  const metadata = fs.readJsonSync(metadataPath);
  const sourceEnvironment = metadata.environment;

  if (sourceEnvironment === targetEnvironment) {
    console.error(chalk.yellow(`Artifact ${buildId} is already in ${targetEnvironment} environment.`));
    process.exit(0);
  }

  // Create new build ID for promoted artifact
  const promotedBuildId = `${buildId}-${targetEnvironment}`;
  const promotedArtifactDir = path.join(artifactsDir, promotedBuildId);
  fs.ensureDirSync(promotedArtifactDir);

  // Copy artifact
  const sourceArchivePath = path.join(artifactDir, `${buildId}.zip`);
  const targetArchivePath = path.join(promotedArtifactDir, `${promotedBuildId}.zip`);
  fs.copySync(sourceArchivePath, targetArchivePath);

  // Update metadata
  const promotedMetadata = {
    ...metadata,
    id: promotedBuildId,
    environment: targetEnvironment,
    promotedFrom: buildId,
    promotedTimestamp: new Date().toISOString(),
  };

  fs.writeJsonSync(path.join(promotedArtifactDir, 'metadata.json'), promotedMetadata, { spaces: 2 });

  // Calculate hash
  const hash = calculateFileHash(targetArchivePath);
  promotedMetadata.hash = hash;
  fs.writeJsonSync(path.join(promotedArtifactDir, 'metadata.json'), promotedMetadata, { spaces: 2 });

  // Update manifest
  const manifest = fs.readJsonSync(manifestPath);
  manifest.artifacts.push({
    id: promotedBuildId,
    environment: targetEnvironment,
    timestamp: promotedMetadata.promotedTimestamp,
    version: promotedMetadata.version,
    hash,
    path: path.relative(artifactsDir, targetArchivePath),
    promotedFrom: buildId,
  });
  fs.writeJsonSync(manifestPath, manifest, { spaces: 2 });

  console.log(chalk.green(`✓ Artifact ${buildId} promoted to ${targetEnvironment} as ${promotedBuildId}`));
  console.log(`  Version: ${promotedMetadata.version}`);
  console.log(`  Hash: ${hash}`);
  console.log(`  Path: ${promotedArtifactDir}`);

  return promotedBuildId;
}

/**
 * Delete artifact
 */
function deleteArtifact(buildId) {
  console.log(chalk.bold(`Deleting artifact ${buildId}...`));

  const artifactDir = path.join(artifactsDir, buildId);

  if (!fs.existsSync(artifactDir)) {
    console.error(chalk.red(`Artifact ${buildId} not found.`));
    process.exit(1);
  }

  // Remove from manifest
  const manifest = fs.readJsonSync(manifestPath);
  manifest.artifacts = manifest.artifacts.filter((artifact) => artifact.id !== buildId);
  fs.writeJsonSync(manifestPath, manifest, { spaces: 2 });

  // Delete artifact directory
  fs.removeSync(artifactDir);

  console.log(chalk.green(`✓ Artifact ${buildId} deleted successfully`));
}

/**
 * Clean old artifacts
 */
function cleanArtifacts(keepCount = 5) {
  console.log(chalk.bold(`Cleaning artifacts (keeping ${keepCount} most recent per environment)...`));

  const manifest = fs.readJsonSync(manifestPath);
  
  if (manifest.artifacts.length === 0) {
    console.log('No artifacts to clean.');
    return;
  }

  // Group by environment
  const artifactsByEnv = {};
  manifest.artifacts.forEach((artifact) => {
    if (!artifactsByEnv[artifact.environment]) {
      artifactsByEnv[artifact.environment] = [];
    }
    artifactsByEnv[artifact.environment].push(artifact);
  });

  // Sort by timestamp (newest first) and keep only the specified count
  let artifactsToDelete = [];
  Object.keys(artifactsByEnv).forEach((env) => {
    const envArtifacts = artifactsByEnv[env];
    envArtifacts.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
    
    if (envArtifacts.length > keepCount) {
      artifactsToDelete = [...artifactsToDelete, ...envArtifacts.slice(keepCount)];
    }
  });

  // Delete artifacts
  artifactsToDelete.forEach((artifact) => {
    const artifactDir = path.join(artifactsDir, artifact.id);
    
    if (fs.existsSync(artifactDir)) {
      fs.removeSync(artifactDir);
      console.log(`Deleted artifact ${artifact.id} (${artifact.environment})`);
    }
  });

  // Update manifest
  const remainingIds = new Set(
    manifest.artifacts
      .filter((a) => !artifactsToDelete.some((d) => d.id === a.id))
      .map((a) => a.id)
  );
  
  manifest.artifacts = manifest.artifacts.filter((a) => remainingIds.has(a.id));
  fs.writeJsonSync(manifestPath, manifest, { spaces: 2 });

  console.log(chalk.green(`✓ Cleaned ${artifactsToDelete.length} artifacts`));
}

/**
 * Main function
 */
async function main() {
  try {
    switch (command) {
      case 'store':
        await storeArtifact(buildId || generateBuildId(), environment);
        break;
      case 'list':
        listArtifacts();
        break;
      case 'retrieve':
        if (!buildId) {
          console.error(chalk.red('Build ID is required for retrieve command.'));
          process.exit(1);
        }
        retrieveArtifact(buildId);
        break;
      case 'promote':
        if (!buildId || !environment) {
          console.error(chalk.red('Build ID and target environment are required for promote command.'));
          process.exit(1);
        }
        await promoteArtifact(buildId, environment);
        break;
      case 'delete':
        if (!buildId) {
          console.error(chalk.red('Build ID is required for delete command.'));
          process.exit(1);
        }
        deleteArtifact(buildId);
        break;
      case 'clean':
        cleanArtifacts(parseInt(buildId) || 5);
        break;
      default:
        console.error(chalk.red(`Unknown command: ${command}`));
        console.log('Available commands:');
        console.log('  store [buildId] [environment]  - Store build artifact');
        console.log('  list                          - List all artifacts');
        console.log('  retrieve <buildId>            - Retrieve artifact');
        console.log('  promote <buildId> <environment> - Promote artifact to another environment');
        console.log('  delete <buildId>              - Delete artifact');
        console.log('  clean [keepCount]             - Clean old artifacts');
        process.exit(1);
    }
  } catch (error) {
    console.error(chalk.red('Error:'), error);
    process.exit(1);
  }
}

main();
