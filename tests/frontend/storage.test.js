import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { escapeHTML, validateGradesData } from '../../frontend/storage.js';

describe('storage.js', () => {
    describe('escapeHTML', () => {
        it('should escape malicious characters', () => {
            const input = '<script>alert("XSS")</script>&\'';
            const expected = '&lt;script&gt;alert(&quot;XSS&quot;)&lt;/script&gt;&amp;&#039;';
            assert.equal(escapeHTML(input), expected);
        });

        it('should handle null or undefined', () => {
            assert.equal(escapeHTML(null), '');
            assert.equal(escapeHTML(undefined), '');
        });
    });

    describe('validateGradesData', () => {
        it('should throw if Result is missing', () => {
            assert.throws(() => validateGradesData({}), /缺少 Result 資料/);
        });

        it('should throw if SubjectExamInfoList is missing', () => {
            assert.throws(() => validateGradesData({ Result: {} }), /缺少 SubjectExamInfoList 成績清單/);
        });

        it('should not throw if valid', () => {
            const validData = {
                Result: {
                    SubjectExamInfoList: []
                }
            };
            assert.doesNotThrow(() => validateGradesData(validData));
        });
    });
});
