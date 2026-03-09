/**
 * PM2 Ecosystem Configuration for AI Features
 * 
 * This configuration manages the embedding pipeline as a background job.
 * 
 * Usage:
 *   pm2 start ecosystem.ai.config.js
 *   pm2 logs embedding-pipeline
 *   pm2 restart embedding-pipeline
 *   pm2 stop embedding-pipeline
 */

module.exports = {
    apps: [
        {
            name: 'embedding-pipeline',
            script: 'apps/api/src/jobs/embedding-pipeline.ts',
            args: 'schedule',
            interpreter: 'node',
            interpreter_args: '--loader ts-node/esm',
            cwd: './',
            instances: 1,
            exec_mode: 'fork',

            // Auto-restart configuration
            autorestart: true,
            watch: false,
            max_memory_restart: '1G',

            // Restart on schedule (every 6 hours as backup)
            cron_restart: '0 */6 * * *',

            // Environment variables
            env: {
                NODE_ENV: 'production',
                TZ: 'UTC',
            },

            env_development: {
                NODE_ENV: 'development',
            },

            env_staging: {
                NODE_ENV: 'staging',
            },

            // Logging
            error_file: './logs/embedding-pipeline-error.log',
            out_file: './logs/embedding-pipeline-out.log',
            log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
            combine_logs: true,

            // Merge logs from different instances
            merge_logs: true,

            // Time to wait before force killing the process
            kill_timeout: 5000,

            // Wait time before restarting after crash
            restart_delay: 10000,

            // Max restarts within min_uptime before considering unstable
            max_restarts: 10,
            min_uptime: '30s',

            // Additional metadata
            instance_var: 'INSTANCE_ID',

            // Node.js specific options
            node_args: [
                '--max-old-space-size=512',
                '--experimental-specifier-resolution=node',
            ],
        },

        // Optional: Metrics collector for AI observability
        {
            name: 'ai-metrics-collector',
            script: 'apps/api/src/jobs/collect-ai-metrics.ts',
            interpreter: 'node',
            interpreter_args: '--loader ts-node/esm',
            cwd: './',
            instances: 1,
            exec_mode: 'fork',
            autorestart: true,
            watch: false,
            max_memory_restart: '500M',

            // Run every hour
            cron_restart: '0 * * * *',

            env: {
                NODE_ENV: 'production',
                TZ: 'UTC',
            },

            error_file: './logs/ai-metrics-error.log',
            out_file: './logs/ai-metrics-out.log',
            log_date_format: 'YYYY-MM-DD HH:mm:ss Z',

            // Disable this by default (uncomment to enable)
            disabled: true,
        },
    ],

    // Deployment configuration (optional)
    deploy: {
        production: {
            user: 'deploy',
            host: ['production-server'],
            ref: 'origin/main',
            repo: 'git@github.com:your-org/yappc.git',
            path: '/var/www/yappc',
            'post-deploy': 'npm install && npm run build && pm2 reload ecosystem.ai.config.js --env production',
        },

        staging: {
            user: 'deploy',
            host: ['staging-server'],
            ref: 'origin/develop',
            repo: 'git@github.com:your-org/yappc.git',
            path: '/var/www/yappc-staging',
            'post-deploy': 'npm install && npm run build && pm2 reload ecosystem.ai.config.js --env staging',
        },
    },
};
