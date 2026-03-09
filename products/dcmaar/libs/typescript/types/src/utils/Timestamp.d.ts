/**
 * Timestamp utility type definition
 * Provides timestamp management utilities
 */
export type Timestamp = number & {
    readonly __brand: 'Timestamp';
};
export declare function createTimestamp(): Timestamp;
export declare function isValidTimestamp(value: unknown): value is Timestamp;
export declare function asTimestamp(value: number | Date): Timestamp;
export declare function timestampToDate(timestamp: Timestamp): Date;
export declare function dateToTimestamp(date: Date): Timestamp;
export interface TimestampProvider {
    now(): Timestamp;
    fromDate(date: Date): Timestamp;
    toDate(timestamp: Timestamp): Date;
}
//# sourceMappingURL=Timestamp.d.ts.map