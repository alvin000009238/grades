// ========================================
// 應用程式入口
// ========================================

import { loadTurnstileConfig } from './turnstile.js';
import { checkDisclaimer, loadGradesData } from './storage.js';
import { setupSyncFeature } from './sync.js';
import { setupShareFeature } from './share.js';

document.addEventListener('DOMContentLoaded', () => {
    checkDisclaimer();
    loadTurnstileConfig();
    loadGradesData();
    setupSyncFeature();
    setupShareFeature();

    // 注入 Commit Hash
    const commitHash = import.meta.env.VITE_COMMIT_HASH;
    if (commitHash && commitHash !== 'undefined') {
        const shortHash = commitHash.substring(0, 7);
        const container = document.getElementById('commit-badge-container');
        if (container) {
            container.innerHTML = `
                <span style="color: var(--color-border-subtle); margin: 0 10px;">|</span>
                <a href="https://github.com/alvin000009238/grades/commit/${commitHash}" target="_blank" rel="noopener noreferrer"
                    style="color: var(--color-text-dim); text-decoration: none; transition: color 0.2s ease; font-family: monospace;">
                    ${shortHash}
                </a>
            `;
        }
    }
});
