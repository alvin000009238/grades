import { describe, it, expect } from 'vitest';
import { escapeHTML, validateGradesData } from '../../frontend/storage.js';

describe('storage.js', () => {
    describe('escapeHTML', () => {
        it('should escape malicious characters', () => {
            const input = '<script>alert("XSS")</script>&\'';
            const expected = '&lt;script&gt;alert(&quot;XSS&quot;)&lt;/script&gt;&amp;&#039;';
            expect(escapeHTML(input)).toBe(expected);
        });

        it('should handle null or undefined', () => {
            expect(escapeHTML(null)).toBe('');
            expect(escapeHTML(undefined)).toBe('');
        });
    });

    describe('validateGradesData', () => {
        it('should throw if Result is missing', () => {
            expect(() => validateGradesData({})).toThrow('缺少 Result 資料');
        });

        it('should throw if SubjectExamInfoList is missing', () => {
            expect(() => validateGradesData({ Result: {} })).toThrow('缺少 SubjectExamInfoList 成績清單');
        });

        it('should not throw if valid', () => {
            const validData = {
                Result: {
                    SubjectExamInfoList: []
                }
            };
            expect(() => validateGradesData(validData)).not.toThrow();
        });
    });
});
