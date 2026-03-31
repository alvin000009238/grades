// ========================================
// 應用程式入口
// ========================================

import './style.css';
import { checkDisclaimer, loadGradesData } from './storage.js';
import { setupThemeToggle } from './theme.js';

// ── Lazy init 狀態 ──────────────────────
let syncInited = false;
let shareInited = false;

/** 確保 sync.js 已載入並初始化，可重複呼叫 */
export async function ensureSyncReady() {
    if (syncInited) return;
    syncInited = true;
    const { setupSyncFeature } = await import('./sync.js');
    setupSyncFeature();
}

/** 確保 share.js 已載入並初始化，可重複呼叫 */
export async function ensureShareReady() {
    if (shareInited) return;
    shareInited = true;
    const { setupShareFeature } = await import('./share.js');
    setupShareFeature();
}

// ── 首屏必要初始化 ──────────────────────
document.addEventListener('DOMContentLoaded', () => {
    setupThemeToggle();
    checkDisclaimer();
    loadGradesData();

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

    // ── Lazy init: 同步功能（點擊登入按鈕時才載入） ──
    const syncBtn = document.getElementById('syncBtn');
    if (syncBtn) {
        syncBtn.addEventListener('click', async () => {
            if (!syncInited) {
                await ensureSyncReady();
                syncBtn.click();
            }
        }, { once: true });
    }

    // ── Lazy init: 分享功能（點擊分享按鈕時才載入） ──
    const shareBtn = document.getElementById('shareBtn');
    if (shareBtn) {
        shareBtn.addEventListener('click', async () => {
            if (!shareInited) {
                await ensureShareReady();
                shareBtn.click();
            }
        }, { once: true });
    }

    // ── Lazy init: 教學引導（idle 時才載入，不阻塞首屏） ──
    const initOnboarding = async () => {
        const { setupOnboardingBootstrap } = await import('./onboarding-bootstrap.js');
        setupOnboardingBootstrap();
    };
    if ('requestIdleCallback' in window) {
        requestIdleCallback(initOnboarding, { timeout: 3000 });
    } else {
        setTimeout(initOnboarding, 1500);
    }
});
