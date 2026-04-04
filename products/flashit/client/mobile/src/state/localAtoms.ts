import AsyncStorage from "@react-native-async-storage/async-storage";
import { createFlashitAtoms } from "@flashit/shared";

const storageAdapter = {
  getItem: async (key: string) => {
    try {
      const value = await AsyncStorage.getItem(key);
      if (value === null) {
        return null;
      }

      try {
        return JSON.parse(value) as unknown;
      } catch {
        return value;
      }
    } catch (error) {
      console.error("AsyncStorage getItem error:", error);
      return null;
    }
  },
  setItem: async (key: string, value: unknown) => {
    try {
      const serializedValue =
        typeof value === "string" ? value : JSON.stringify(value);
      await AsyncStorage.setItem(key, serializedValue);
    } catch (error) {
      console.error("AsyncStorage setItem error:", error);
    }
  },
  removeItem: async (key: string) => {
    try {
      await AsyncStorage.removeItem(key);
    } catch (error) {
      console.error("AsyncStorage removeItem error:", error);
    }
  },
};

export const mobileAtoms = createFlashitAtoms(storageAdapter as never);
