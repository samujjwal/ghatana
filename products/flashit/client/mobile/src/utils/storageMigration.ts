import AsyncStorage from '@react-native-async-storage/async-storage';
import { monitoring } from '../services/monitoring';

const migrationDiagnostic = (
    level: 'debug' | 'info' | 'warn' | 'error',
    message: string,
    context?: Record<string, unknown>,
): void => {
    monitoring.log(level, `[Migration] ${message}`, context);
};

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

        migrationDiagnostic('info', 'Starting boolean settings migration');

        for (const key of keysToMigrate) {
            const value = await AsyncStorage.getItem(key);
            if (value !== null) {
                // Check if it's an old string value
                if (value === 'true' || value === 'false') {
                    const migratedValue = JSON.parse(value) as boolean;
                    migrationDiagnostic('info', 'Migrating boolean setting', {
                        key,
                        value,
                        migratedValue,
                    });
                    // Convert to proper JSON boolean
                    await AsyncStorage.setItem(key, JSON.stringify(migratedValue));
                } else {
                    try {
                        // Test if it's already valid JSON
                        JSON.parse(value);
                        migrationDiagnostic('debug', 'Setting already in correct format', {
                            key,
                            value,
                        });
                    } catch {
                        migrationDiagnostic('warn', 'Setting has unexpected value, removing', {
                            key,
                            value,
                        });
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
                    migrationDiagnostic('debug', 'Jotai key already in correct format', {
                        key,
                    });
                } catch {
                    migrationDiagnostic('warn', 'Jotai key has invalid format, removing', {
                        key,
                    });
                    await AsyncStorage.removeItem(key);
                }
            }
        }

        migrationDiagnostic('info', 'Migration completed');
    } catch (error) {
        migrationDiagnostic('error', 'Migration failed', { error });
    }
};
