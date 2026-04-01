import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { updateActiveShare } from '../../frontend/share.js';

const originalFetch = global.fetch;
const originalLocalStorage = global.localStorage;

function createLocalStorageMock() {
    const store = new Map();
    return {
        getItem: (key) => store.get(key) ?? null,
        setItem: (key, value) => store.set(key, String(value)),
        removeItem: (key) => store.delete(key),
        clear: () => store.clear(),
    };
}

describe('share.js', () => {
    beforeEach(() => {
        global.localStorage = createLocalStorageMock();
        localStorage.clear();
    });

    afterEach(() => {
        global.fetch = originalFetch;
        global.localStorage = originalLocalStorage;
    });

    it('removes activeShareId when update returns 400', async () => {
        localStorage.setItem('activeShareId', 'invalid-id');
        global.fetch = async () => ({ ok: false, status: 400 });

        const result = await updateActiveShare({ Result: { SubjectExamInfoList: [] } });

        assert.equal(result.ok, false);
        assert.equal(localStorage.getItem('activeShareId'), null);
    });

    it('keeps activeShareId when update returns 500', async () => {
        localStorage.setItem('activeShareId', 'validshareid1234');
        global.fetch = async () => ({ ok: false, status: 500 });

        await updateActiveShare({ Result: { SubjectExamInfoList: [] } });

        assert.equal(localStorage.getItem('activeShareId'), 'validshareid1234');
    });
});
