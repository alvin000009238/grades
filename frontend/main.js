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
});
