import AsyncStorage from '@react-native-async-storage/async-storage';
import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// Storage adapter for React Native
interface StorageAdapter {
    getItem: (key: string) => Promise<string | null> | string | null;
    setItem: (key: string, value: any) => Promise<void> | void;
    removeItem: (key: string) => Promise<void> | void;
}

// Create atoms with AsyncStorage adapter
export const mobileAtoms = {
    // Auth atoms
    authTokenAtom: atomWithStorage<string | null>('flashit_token', null, {
        getItem: async (key: string) => {
            try {
                const value = await AsyncStorage.getItem(key);
                if (value === null) return null;

                // Try to parse as JSON to handle different types
                try {
                    return JSON.parse(value);
                } catch {
                    // If parsing fails, return as string
                    return value;
                }
            } catch (error) {
                console.error('AsyncStorage getItem error:', error);
                return null;
            }
        },
        setItem: async (key: string, value: any) => {
            try {
                // Store as JSON to preserve type information
                const serializedValue = typeof value === 'string' ? value : JSON.stringify(value);
                await AsyncStorage.setItem(key, serializedValue);
            } catch (error) {
                console.error('AsyncStorage setItem error:', error);
            }
        },
        removeItem: async (key: string) => {
            try {
                await AsyncStorage.removeItem(key);
            } catch (error) {
                console.error('AsyncStorage removeItem error:', error);
            }
        },
    }),
    currentUserAtom: atom<any | null>(null),
    isAuthenticatedAtom: atom((get) => get(mobileAtoms.authTokenAtom) !== null),

    // Spheres atoms
    spheresAtom: atom<any[]>([]),
    selectedSphereIdAtom: atomWithStorage<string | null>('flashit_selected_sphere', null, {
        getItem: async (key: string) => {
            try {
                const value = await AsyncStorage.getItem(key);
                if (value === null) return null;

                // Try to parse as JSON to handle different types
                try {
                    return JSON.parse(value);
                } catch {
                    // If parsing fails, return as string
                    return value;
                }
            } catch (error) {
                console.error('AsyncStorage getItem error:', error);
                return null;
            }
        },
        setItem: async (key: string, value: any) => {
            try {
                // Store as JSON to preserve type information
                const serializedValue = typeof value === 'string' ? value : JSON.stringify(value);
                await AsyncStorage.setItem(key, serializedValue);
            } catch (error) {
                console.error('AsyncStorage setItem error:', error);
            }
        },
        removeItem: async (key: string) => {
            try {
                await AsyncStorage.removeItem(key);
            } catch (error) {
                console.error('AsyncStorage removeItem error:', error);
            }
        },
    }),
    selectedSphereAtom: atom((get) => {
        const spheres = get(mobileAtoms.spheresAtom);
        const selectedId = get(mobileAtoms.selectedSphereIdAtom);
        return spheres.find((s) => s.id === selectedId) || null;
    }),

    // Moments atoms
    momentsAtom: atom<any[]>([]),
    selectedMomentIdAtom: atom<string | null>(null),
    selectedMomentAtom: atom((get) => {
        const moments = get(mobileAtoms.momentsAtom);
        const selectedId = get(mobileAtoms.selectedMomentIdAtom);
        return moments.find((m) => m.id === selectedId) || null;
    }),

    // UI state atoms
    isCaptureModalOpenAtom: atom(false),
    isCreateSphereModalOpenAtom: atom(false),
    searchQueryAtom: atom(''),
    selectedTagsAtom: atom<string[]>([]),
    selectedEmotionsAtom: atom<string[]>([]),
};
