/**
 * SeededRandom Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test deterministic PRNG for simulation replay
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect } from 'vitest';
import { SeededRandom } from '../physics-kernel';

describe('SeededRandom', () => {
    it('produces deterministic output for the same seed', () => {
        const rng1 = new SeededRandom(42);
        const rng2 = new SeededRandom(42);

        const seq1 = Array.from({ length: 100 }, () => rng1.next());
        const seq2 = Array.from({ length: 100 }, () => rng2.next());

        expect(seq1).toEqual(seq2);
    });

    it('produces different output for different seeds', () => {
        const rng1 = new SeededRandom(42);
        const rng2 = new SeededRandom(99);

        const seq1 = Array.from({ length: 10 }, () => rng1.next());
        const seq2 = Array.from({ length: 10 }, () => rng2.next());

        expect(seq1).not.toEqual(seq2);
    });

    it('next() returns values in [0, 1)', () => {
        const rng = new SeededRandom(123);
        for (let i = 0; i < 1000; i++) {
            const v = rng.next();
            expect(v).toBeGreaterThanOrEqual(0);
            expect(v).toBeLessThan(1);
        }
    });

    it('nextInt() returns integers in [min, max]', () => {
        const rng = new SeededRandom(456);
        for (let i = 0; i < 500; i++) {
            const v = rng.nextInt(3, 7);
            expect(v).toBeGreaterThanOrEqual(3);
            expect(v).toBeLessThanOrEqual(7);
            expect(Number.isInteger(v)).toBe(true);
        }
    });

    it('nextFloat() returns values in [min, max)', () => {
        const rng = new SeededRandom(789);
        for (let i = 0; i < 500; i++) {
            const v = rng.nextFloat(2.5, 5.5);
            expect(v).toBeGreaterThanOrEqual(2.5);
            expect(v).toBeLessThan(5.5);
        }
    });

    it('nextGaussian() produces values with correct mean (within tolerance)', () => {
        const rng = new SeededRandom(321);
        const n = 10000;
        let sum = 0;
        for (let i = 0; i < n; i++) {
            sum += rng.nextGaussian(5, 1);
        }
        const mean = sum / n;
        expect(mean).toBeCloseTo(5, 0); // within 0.5
    });

    it('serialize/deserialize preserves state', () => {
        const rng = new SeededRandom(55);
        // Advance state
        for (let i = 0; i < 50; i++) rng.next();

        const state = rng.serialize();
        const restored = SeededRandom.deserialize(state);

        // Both should produce identical sequences from here
        const seq1 = Array.from({ length: 50 }, () => rng.next());
        const seq2 = Array.from({ length: 50 }, () => restored.next());

        expect(seq1).toEqual(seq2);
    });

    it('handles seed 0 without producing all-zero state', () => {
        const rng = new SeededRandom(0);
        const first = rng.next();
        expect(first).toBeGreaterThanOrEqual(0);
        expect(first).toBeLessThan(1);
        // Should not be stuck at 0
        const second = rng.next();
        expect(first === 0 && second === 0).toBe(false);
    });

    it('has reasonable distribution uniformity', () => {
        const rng = new SeededRandom(777);
        const buckets = new Array(10).fill(0);
        const n = 10000;

        for (let i = 0; i < n; i++) {
            const bucket = Math.floor(rng.next() * 10);
            buckets[bucket]!++;
        }

        // Each bucket should have roughly n/10 = 1000 entries
        // Allow ±20% deviation
        for (const count of buckets) {
            expect(count).toBeGreaterThan(800);
            expect(count).toBeLessThan(1200);
        }
    });
});
