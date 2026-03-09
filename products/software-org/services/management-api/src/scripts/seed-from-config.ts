
import { prisma } from '../db/client.js';
import { ConfigSyncService } from '../services/config-sync.service.js';

async function seedFromConfig() {
    console.log('🚀 Starting seed from config...');

    try {
        const syncService = ConfigSyncService.getInstance();
        await syncService.syncFromConfig();
        console.log('✅ Seeding from config complete!');
    } catch (error) {
        console.error('❌ Error seeding from config:', error);
        process.exit(1);
    } finally {
        await prisma.$disconnect();
    }
}

seedFromConfig();
