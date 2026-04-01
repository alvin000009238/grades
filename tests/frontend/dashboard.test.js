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
        it('should keep only the text before hyphen', () => {
            assert.equal(shortenName('選修化學-物質構造與反應速率'), '選修化學');
            assert.equal(shortenName('選修物理-力學二與熱學'), '選修物理');
        });

        it('should return original name when no hyphen exists', () => {
            assert.equal(shortenName('數學A'), '數學A');
            assert.equal(shortenName('英語文'), '英語文');
        });
    });
});
