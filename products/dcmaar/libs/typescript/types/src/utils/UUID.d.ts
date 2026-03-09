/**
 * UUID utility type definition
 * Provides UUID generation and validation
 */
export type UUID = string & {
    readonly __brand: 'UUID';
};
export declare function isValidUUID(value: string): value is UUID;
export declare function createUUID(): UUID;
export declare function asUUID(value: string): UUID;
export interface UUIDProvider {
    generate(): UUID;
    validate(value: string): value is UUID;
}
//# sourceMappingURL=UUID.d.ts.map