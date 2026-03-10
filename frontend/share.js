// ========================================
// 分享功能邏輯 (連結分享)
// ========================================

import { requestTurnstileVerification } from './turnstile.js';
import { getStoredGrades } from './storage.js';
import { initDashboard } from './dashboard.js';

export function setupShareFeature() {
    const shareBtn = document.getElementById('shareBtn');
    const shareModal = document.getElementById('shareModal');
    const closeShareModal = document.getElementById('closeShareModal');
    const sharePreview = document.getElementById('sharePreview');

    if (!shareBtn || !shareModal) return;

    // Reset Modal Content for Link Sharing
    const setupModalContent = () => {
        sharePreview.innerHTML = `
            <div style="text-align: center; padding: 8px 0;">
                <p style="color: var(--color-text-secondary); margin-bottom: 20px; font-size: 13px; line-height: 1.6;">
                    建立一個唯讀的分享連結，讓他人查看此成績單。<br>
                    連結將於 <strong style="color: var(--color-warning);">2 小時後</strong> 自動失效。
                </p>
                <div id="linkContainer" style="display: none; margin-bottom: 16px;">
                    <div class="form-group" style="margin-bottom: 12px;">
                        <input type="text" id="shareLinkInput" readonly
                            style="text-align: center; font-size: 13px; color: var(--color-primary); border-color: var(--color-primary); background: var(--color-primary-muted);">
                    </div>
                    <button id="copyLinkBtn" class="import-dropdown-btn" style="width: 100%; justify-content: center;">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                            fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                            stroke-linejoin="round">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                        </svg>
                        複製連結
                    </button>
                </div>
                <button id="createLinkBtn" class="import-dropdown-btn" style="width: 100%; justify-content: center;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                        fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                        stroke-linejoin="round">
                        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
                    </svg>
                    建立分享連結
                </button>
                <div id="shareStatus" class="status-msg" style="margin-top: 12px;"></div>
            </div>
        `;

        const createLinkBtn = document.getElementById('createLinkBtn');
        const linkContainer = document.getElementById('linkContainer');
        const shareLinkInput = document.getElementById('shareLinkInput');
        const copyLinkBtn = document.getElementById('copyLinkBtn');
        const shareStatus = document.getElementById('shareStatus');

        createLinkBtn.addEventListener('click', async () => {
            const gradesData = getStoredGrades();
            if (!gradesData) {
                shareStatus.textContent = '無成績資料可分享';
                shareStatus.className = 'status-msg error';
                return;
            }

            // Turnstile 人機驗證
            let turnstileToken = '';
            try {
                turnstileToken = await requestTurnstileVerification();
            } catch (e) {
                if (e.message === 'cancelled') return;
                shareStatus.textContent = e.message;
                shareStatus.className = 'status-msg error';
                return;
            }

            createLinkBtn.disabled = true;
            createLinkBtn.textContent = '建立中...';
            shareStatus.textContent = '';

            try {
                const payload = { ...gradesData, turnstile_token: turnstileToken };
                const res = await fetch('/api/share', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();

                if (data.success) {
                    const link = `${window.location.origin}/share/${data.id}`;
                    shareLinkInput.value = link;
                    linkContainer.style.display = 'block';
                    createLinkBtn.style.display = 'none';
                    shareStatus.textContent = '連結建立成功！';
                    shareStatus.className = 'status-msg success';
                } else {
                    throw new Error(data.error || '建立失敗');
                }
            } catch (error) {
                console.error(error);
                shareStatus.textContent = '建立失敗: ' + error.message;
                shareStatus.className = 'status-msg error';
                createLinkBtn.disabled = false;
                createLinkBtn.textContent = '建立分享連結';
            }
        });

        copyLinkBtn.addEventListener('click', () => {
            shareLinkInput.select();
            navigator.clipboard.writeText(shareLinkInput.value).then(() => {
                const originalText = copyLinkBtn.textContent;
                copyLinkBtn.textContent = '已複製！';
                setTimeout(() => {
                    copyLinkBtn.textContent = originalText;
                }, 2000);
            });
        });
    };

    // Open Modal
    shareBtn.addEventListener('click', () => {
        shareModal.classList.add('active');
        setupModalContent();
    });

    // Close Modal
    const closeModalFn = () => {
        shareModal.classList.remove('active');
    };
    closeShareModal.addEventListener('click', closeModalFn);
}

// Check for shared link on load
export async function checkSharedLink() {
    const path = window.location.pathname;
    const match = path.match(/^\/share\/([A-Za-z0-9\-_.~]+)$/);

    if (match) {
        const shareId = match[1];
        console.log("Viewing shared grade:", shareId);

        // Enter Read-Only Mode
        document.body.classList.add('read-only-mode');

        // Show loading state
        const updateTime = document.getElementById('updateTime');
        if (updateTime) updateTime.textContent = '載入分享資料中...';

        try {
            const res = await fetch(`/api/share/${shareId}`);
            if (res.status === 404) {
                alert('此分享連結已過期或不存在。');
                window.location.href = '/';
                return;
            }

            const data = await res.json();
            if (data.success) {
                initDashboard(data.data);

                // Add header indicator
                const header = document.querySelector('.header-content');
                const indicator = document.createElement('div');
                indicator.className = 'share-indicator';
                indicator.innerHTML = '<span style="color: var(--color-warning); font-weight: bold; padding: 4px 8px; border: 1px solid var(--color-warning); border-radius: 4px; font-size: 12px;">僅供檢視 (唯讀)</span>';
                // Insert before data-time-box
                const timeBox = document.querySelector('.data-time-box');
                header.insertBefore(indicator, timeBox);

                if (updateTime) updateTime.textContent = '分享存檔';
            } else {
                throw new Error(data.error);
            }
        } catch (error) {
            console.error(error);
            alert('載入分享資料失敗: ' + error.message);
            window.location.href = '/';
        }
        return true; // Handled shared link
    }
    return false;
}
