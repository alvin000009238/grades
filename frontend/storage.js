// ========================================
// localStorage 管理與資料驗證
// ========================================

import { initDashboard } from './dashboard.js';

// HTML 跳脫輔助函數，防範 XSS
export function escapeHTML(str) {
    if (str === null || str === undefined) return '';
    const text = String(str);
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, function (m) { return map[m]; });
}

export function checkDisclaimer() {
    const hasSeenDisclaimer = localStorage.getItem('hasSeenDisclaimer');
    if (!hasSeenDisclaimer) {
        const disclaimerModal = document.getElementById('disclaimerModal');
        const confirmDisclaimer = document.getElementById('confirmDisclaimer');
        if (disclaimerModal && confirmDisclaimer) {
            disclaimerModal.classList.add('active');
            confirmDisclaimer.addEventListener('click', () => {
                localStorage.setItem('hasSeenDisclaimer', 'true');
                disclaimerModal.classList.remove('active');
            }, { once: true });
        }
    }
}

export async function loadGradesData() {
    // 只在 /share/ 路由時才動態載入分享模組
    if (window.location.pathname.startsWith('/share/')) {
        const { checkSharedLink } = await import('./share.js');
        if (await checkSharedLink()) return;
    }

    try {
        const storedData = getStoredGrades();
        if (storedData) {
            initDashboard(storedData);
            return;
        }

        // Default: No data loaded
        document.getElementById('updateTime').textContent = '無資料';
    } catch (error) {
        console.error(error);
        document.getElementById('updateTime').textContent = '無資料';
    }
}

export function normalizeFileContent(content) {
    if (content instanceof ArrayBuffer) {
        const decoder = new TextDecoder('utf-8');
        return decoder.decode(content).replace(/^\uFEFF/, '').trim();
    }

    if (typeof content === 'string') {
        return content.replace(/^\uFEFF/, '').trim();
    }

    return '';
}

export function validateGradesData(gradesData) {
    if (!gradesData?.Result) {
        throw new Error('缺少 Result 資料');
    }

    const subjects = gradesData.Result.SubjectExamInfoList;
    if (!Array.isArray(subjects)) {
        throw new Error('缺少 SubjectExamInfoList 成績清單');
    }
}

export function storeGradesData(gradesData) {
    localStorage.setItem('gradesData', JSON.stringify(gradesData));
}

export function getStoredGrades() {
    const stored = localStorage.getItem('gradesData');
    if (!stored) return null;
    try {
        const parsed = JSON.parse(stored);
        validateGradesData(parsed);
        return parsed;
    } catch (error) {
        console.warn('儲存的成績資料格式不正確，已忽略。', error);
        localStorage.removeItem('gradesData');
        return null;
    }
}
