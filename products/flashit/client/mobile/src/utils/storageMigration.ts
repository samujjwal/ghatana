import AsyncStorage from '@react-native-async-storage/async-storage';

// Clear old boolean values that were stored as strings
export const migrateBooleanSettings = async () => {
    try {
        const keysToMigrate = [
            'settings:backgroundUploads',
            'settings:wifiOnly',
            'settings:autoCompress',
            'bandwidth:wifiOnlyUpload',
            // Also clear any old keys with different naming patterns
            '@settings_backgroundUploads',
            '@settings_wifiOnly',
            '@settings_autoCompress',
            '@settings_uploadQuality'
        ];

        console.log('[Migration] Starting boolean settings migration...');

        for (const key of keysToMigrate) {
            const value = await AsyncStorage.getItem(key);
            if (value !== null) {
                // Check if it's an old string value
                if (value === 'true' || value === 'false') {
                    console.log(`[Migration] Migrating ${key}: ${value} -> ${JSON.parse(value)}`);
                    // Convert to proper JSON boolean
                    await AsyncStorage.setItem(key, JSON.parse(value));
                } else {
                    try {
                        // Test if it's already valid JSON
                        JSON.parse(value);
                        console.log(`[Migration] ${key} already in correct format: ${value}`);
                    } catch {
                        console.log(`[Migration] ${key} has unexpected value: ${value}, removing...`);
                        await AsyncStorage.removeItem(key);
                    }
                }
            }
        }

        // Also clear any Jotai atoms that might have boolean values
        const jotaiKeys = [
            'flashit_token',
            'flashit_selected_sphere'
        ];

        for (const key of jotaiKeys) {
            const value = await AsyncStorage.getItem(key);
            if (value !== null) {
                try {
                    JSON.parse(value);
                    console.log(`[Migration] Jotai key ${key} already in correct format`);
                } catch {
                    console.log(`[Migration] Jotai key ${key} has invalid format, removing...`);
                    await AsyncStorage.removeItem(key);
                }
            }
        }

        console.log('[Migration] Migration completed');
    } catch (error) {
        console.error('[Migration] Migration failed:', error);
    }
};
