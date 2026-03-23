import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { getNumericScore, shortenName } from '../../frontend/dashboard.js';

describe('dashboard.js', () => {
    describe('getNumericScore', () => {
        it('should return number if displayValue is valid', () => {
            assert.equal(getNumericScore('95.5', 0), 95.5);
            assert.equal(getNumericScore(80, null), 80);
        });

        it('should return fallback if displayValue is invalid', () => {
            assert.equal(getNumericScore('N/A', 60), 60);
            assert.equal(getNumericScore('', -1), -1);
            assert.equal(getNumericScore(null, 0), 0);
        });
    });

    describe('shortenName', () => {
        it('should shorten known subjects', () => {
            assert.equal(shortenName('英語文'), '英文');
            assert.equal(shortenName('選修化學-物質與能量'), '化學');
        });

        it('should return original name if unknown', () => {
            assert.equal(shortenName('數學A'), '數學A');
        });
    });
});
