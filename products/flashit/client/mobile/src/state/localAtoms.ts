import AsyncStorage from "@react-native-async-storage/async-storage";
import { createFlashitAtoms } from "@flashit/shared";
import { monitoring } from "../services/monitoring";

const localAtomsDiagnostic = (message: string, error: unknown): void => {
  monitoring.log("error", `[LocalAtoms] ${message}`, { error });
};

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
      localAtomsDiagnostic("AsyncStorage getItem error", error);
      return null;
    }
  },
  setItem: async (key: string, value: unknown) => {
    try {
      const serializedValue =
        typeof value === "string" ? value : JSON.stringify(value);
      await AsyncStorage.setItem(key, serializedValue);
    } catch (error) {
      localAtomsDiagnostic("AsyncStorage setItem error", error);
    }
  },
  removeItem: async (key: string) => {
    try {
      await AsyncStorage.removeItem(key);
    } catch (error) {
      localAtomsDiagnostic("AsyncStorage removeItem error", error);
    }
  },
};

export const mobileAtoms = createFlashitAtoms(storageAdapter as never);
