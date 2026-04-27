declare module 'react-native-keychain' {
  export const ACCESSIBLE: {
    WHEN_UNLOCKED_THIS_DEVICE_ONLY: string;
  };

  export type GenericPasswordResult = {
    username: string;
    password: string;
  };

  export function getGenericPassword(options: {
    service: string;
  }): Promise<GenericPasswordResult | false>;

  export function setGenericPassword(
    username: string,
    password: string,
    options: {
      service: string;
      accessible?: string;
    },
  ): Promise<boolean>;

  export function resetGenericPassword(options: {
    service: string;
  }): Promise<boolean>;
}
