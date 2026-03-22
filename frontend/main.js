// ========================================
// 應用程式入口
// ========================================

import './style.css';
import { loadTurnstileConfig } from './turnstile.js';
import { checkDisclaimer, loadGradesData } from './storage.js';
import { setupSyncFeature } from './sync.js';
import { setupShareFeature } from './share.js';
import { setupOnboardingBootstrap } from './onboarding-bootstrap.js';

document.addEventListener('DOMContentLoaded', () => {
    checkDisclaimer();
    loadTurnstileConfig();
    loadGradesData();
    setupSyncFeature();
    setupShareFeature();
    setupOnboardingBootstrap();

    // 注入 Commit Hash
    const commitHash = import.meta.env.VITE_COMMIT_HASH;
    if (commitHash && commitHash !== 'undefined') {
        const shortHash = commitHash.substring(0, 7);
        const container = document.getElementById('commit-badge-container');
        if (container) {
            container.innerHTML = `
                <span class="footer-divider">|</span>
                <a href="https://github.com/alvin000009238/grades/commit/${commitHash}" target="_blank" rel="noopener noreferrer"
                    class="footer-link mono">
                    ${shortHash}
                </a>
            `;
        }
    }
});
