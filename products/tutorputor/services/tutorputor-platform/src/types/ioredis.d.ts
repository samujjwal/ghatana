declare module 'ioredis' {
  class Redis {
    constructor(url?: string);
    get(key: string): Promise<string | null>;
    set(
      key: string,
      value: string,
      mode: 'EX',
      seconds: number,
    ): Promise<'OK' | null>;
    del(key: string): Promise<number>;
    keys(pattern: string): Promise<string[]>;
    publish(channel: string, message: string): Promise<number>;
  }

  export { Redis };
  export default Redis;
}
