// ========================================
// 分享功能邏輯 (連結分享)
// ========================================

import { requestTurnstileVerification } from './turnstile.js';
import { getStoredGrades } from './storage.js';
import { initDashboard } from './dashboard.js';
import { emitOnboardingEvent, ONBOARDING_EVENTS } from './onboarding-events.js';
import { showAlert } from './dialog.js';

const ACTIVE_SHARE_ID_KEY = 'activeShareId';

export function getActiveShareId() {
    return localStorage.getItem(ACTIVE_SHARE_ID_KEY);
}

export async function updateActiveShare(gradesData) {
    const shareId = getActiveShareId();
    if (!shareId || !gradesData) return;

    try {
        const res = await fetch(`/api/share/${shareId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(gradesData)
        });

        if (!res.ok) {
            if (res.status === 404 || res.status === 403) {
                localStorage.removeItem(ACTIVE_SHARE_ID_KEY);
            }
            return;
        }
    } catch (error) {
        console.error('更新分享失敗:', error);
    }
}

export function setupShareFeature() {
    const shareBtn = document.getElementById('shareBtn');
    const shareModal = document.getElementById('shareModal');
    const closeShareModal = document.getElementById('closeShareModal');
    const sharePreview = document.getElementById('sharePreview');

    if (!shareBtn || !shareModal) return;

    // Reset Modal Content for Link Sharing
    const setupModalContent = () => {
        sharePreview.innerHTML = `
            <div class="share-modal-container">
                <p class="share-modal-text">
                    建立一個唯讀的分享連結，讓他人查看此成績單。<br>
                    連結將於 <strong class="text-warning">2 小時後</strong> 自動失效。
                </p>
                <div id="linkContainer" class="share-link-container hidden">
                    <div class="form-group mb-12">
                        <input type="text" id="shareLinkInput" readonly
                            class="share-input-readonly">
                    </div>
                    <button id="copyLinkBtn" class="import-dropdown-btn w-full-center">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                            fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                            stroke-linejoin="round">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                        </svg>
                        複製連結
                    </button>
                </div>
                <button id="createLinkBtn" data-tour="create-share-link" class="import-dropdown-btn w-full-center">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                        fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                        stroke-linejoin="round">
                        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
                    </svg>
                    建立分享連結
                </button>
                <div id="shareStatus" class="status-msg mt-12"></div>
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
                    localStorage.setItem(ACTIVE_SHARE_ID_KEY, data.id);
                    shareLinkInput.value = link;
                    linkContainer.style.display = 'block';
                    createLinkBtn.style.display = 'none';
                    shareStatus.textContent = '連結建立成功！';
                    shareStatus.className = 'status-msg success';
                    emitOnboardingEvent(ONBOARDING_EVENTS.SHARE_LINK_CREATED, { link });
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
        emitOnboardingEvent(ONBOARDING_EVENTS.SHARE_MODAL_OPEN);
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
                await showAlert('錯誤', '此分享連結已過期或不存在。');
                window.location.href = '/';
                return;
            }

            const data = await res.json();
            if (data.success) {
                initDashboard(data.data);

                const headerStatusBadge = document.getElementById('headerStatusBadge');
                if (headerStatusBadge) headerStatusBadge.textContent = '僅供檢視';

                if (updateTime) updateTime.textContent = '分享存檔';
            } else {
                throw new Error(data.error);
            }
        } catch (error) {
            console.error(error);
            await showAlert('錯誤', '載入分享資料失敗: ' + error.message);
            window.location.href = '/';
        }
        return true; // Handled shared link
    }
    return false;
}
