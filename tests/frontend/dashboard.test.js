import { describe, it, expect } from 'vitest';
import { getNumericScore, shortenName } from '../../frontend/dashboard.js';

describe('dashboard.js', () => {
    describe('getNumericScore', () => {
        it('should return number if displayValue is valid', () => {
            expect(getNumericScore('95.5', 0)).toBe(95.5);
            expect(getNumericScore(80, null)).toBe(80);
        });

        it('should return fallback if displayValue is invalid', () => {
            expect(getNumericScore('N/A', 60)).toBe(60);
            expect(getNumericScore('', -1)).toBe(-1);
            expect(getNumericScore(null, 0)).toBe(0);
        });
    });

    describe('shortenName', () => {
        it('should shorten known subjects', () => {
            expect(shortenName('英語文')).toBe('英文');
            expect(shortenName('選修化學-物質與能量')).toBe('化學');
        });

        it('should return original name if unknown', () => {
            expect(shortenName('數學A')).toBe('數學A');
        });
    });
});
