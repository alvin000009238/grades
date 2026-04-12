// ========================================
// 同步功能邏輯
// ========================================

import { requestTurnstileVerification } from './turnstile.js';
import { validateGradesData, storeGradesData } from './storage.js';
import { initDashboard } from './dashboard.js';
import { getDemoCredentials, getDemoResultData, getDemoStructure, isDemoModeEnabled } from './demo-mode.js';
import { updateActiveShare } from './share.js';
import { emitOnboardingEvent, ONBOARDING_EVENTS } from './onboarding-events.js';
import { showConfirm, showAlert } from './dialog.js';

export function setupSyncFeature() {
    const syncBtn = document.getElementById('syncBtn');
    if (!syncBtn) return;

    // Modals
    const loginModal = document.getElementById('loginModal');
    const selectExamModal = document.getElementById('selectExamModal');

    // Login Form
    const closeLoginModal = document.getElementById('closeLoginModal');
    const cancelLogin = document.getElementById('cancelLogin');
    const confirmLogin = document.getElementById('confirmLogin');
    const usernameInput = document.getElementById('usernameInput');
    const passwordInput = document.getElementById('passwordInput');
    const demoFillLoginBtn = document.getElementById('demoFillLoginBtn');
    const loginStatus = document.getElementById('loginStatus');
    const captchaInput = document.getElementById('captchaInput');
    const schoolCaptchaImage = document.getElementById('schoolCaptchaImage');
    const refreshSchoolCaptcha = document.getElementById('refreshSchoolCaptcha');

    // Select Exam Form
    const closeSelectModal = document.getElementById('closeSelectModal');
    const cancelSelect = document.getElementById('cancelSelect');
    const confirmFetch = document.getElementById('confirmFetch');
    const yearSelect = document.getElementById('yearSelect');
    const examSelect = document.getElementById('examSelect');
    const fetchStatus = document.getElementById('fetchStatus');
    const logoutBtn = document.getElementById('logoutBtn');

    const API_BASE = '/api';
    let availableStructure = {}; // Store the loaded structure

    // Helper to toggle modal
    const toggleModal = (modal, show) => {
        if (show) modal.classList.add('active');
        else modal.classList.remove('active');
    };

    // Helper to show status
    const showStatus = (el, msg, type = 'normal') => {
        el.textContent = msg;
        el.className = `status-msg ${type}`;
    };

    const fillDemoCredentials = () => {
        const credentials = getDemoCredentials();
        usernameInput.value = credentials.username;
        passwordInput.value = credentials.password;
        showStatus(loginStatus, '已填入教學帳密，可直接登入。', 'normal');
        emitOnboardingEvent(ONBOARDING_EVENTS.DEMO_CREDENTIALS_FILLED);
    };

    const loadSchoolCaptcha = async () => {
        if (!schoolCaptchaImage) return;
        schoolCaptchaImage.src = '';
        schoolCaptchaImage.alt = '學校系統驗證碼載入中';
        showStatus(loginStatus, '正在載入學校驗證碼...', 'normal');
        try {
            const res = await fetch(`${API_BASE}/school-captcha`, {
                credentials: 'include'
            });
            const contentType = res.headers.get('content-type') || '';
            let data;
            if (contentType.includes('application/json')) {
                data = await res.json();
            } else {
                const raw = await res.text();
                throw new Error(`伺服器回傳非 JSON 內容（HTTP ${res.status}）: ${raw.slice(0, 80)}`);
            }
            if (!res.ok || !data.success || !data.image_data_url) {
                throw new Error(data.message || '驗證碼載入失敗');
            }
            schoolCaptchaImage.src = data.image_data_url;
            schoolCaptchaImage.alt = '學校系統驗證碼';
            showStatus(loginStatus, '請輸入圖中的驗證碼。', 'normal');
        } catch (error) {
            showStatus(loginStatus, `驗證碼載入失敗：${error.message}`, 'error');
        }
    };

    const openLoginModal = () => {
        toggleModal(loginModal, true);
        if (demoFillLoginBtn) {
            demoFillLoginBtn.style.display = isDemoModeEnabled() ? 'inline-flex' : 'none';
        }
        if (isDemoModeEnabled()) {
            showStatus(loginStatus, '教學模式已啟用，先點「一鍵填入教學帳密」。', 'normal');
        } else {
            captchaInput.value = '';
            loadSchoolCaptcha();
        }
        usernameInput.focus();
        emitOnboardingEvent(ONBOARDING_EVENTS.LOGIN_MODAL_OPEN);
    };

    const populateExamSelect = (year, preferredExam = '') => {
        if (!year) {
            examSelect.innerHTML = '<option value="">請選擇考試</option>';
            examSelect.disabled = true;
            confirmFetch.disabled = true;
            return;
        }

        const yearData = availableStructure[year];
        const exams = yearData?.exams || [];

        if (!exams.length) {
            examSelect.innerHTML = '<option>無考試資料</option>';
            examSelect.disabled = true;
            confirmFetch.disabled = true;
            return;
        }

        examSelect.innerHTML = '<option value="">請選擇考試</option>';
        exams.forEach(exam => {
            const opt = document.createElement('option');
            opt.value = exam.value;
            opt.textContent = exam.text;
            examSelect.appendChild(opt);
        });

        if (preferredExam && exams.some(exam => exam.value === preferredExam)) {
            examSelect.value = preferredExam;
        }

        examSelect.disabled = false;
        confirmFetch.disabled = !examSelect.value;
    };

    // Logout Logic
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            if (!await showConfirm('確認登出', '確定要登出嗎？')) return;
            try {
                await fetch(`${API_BASE}/logout`, { method: 'POST' });
                location.reload();
            } catch (e) {
                await showAlert('錯誤', '登出失敗');
            }
        });
    }

    // 1. Click Sync Button (Optimistic UI)
    syncBtn.addEventListener('click', async () => {
        if (isDemoModeEnabled()) {
            openLoginModal();
            return;
        }
        openSelectExamModal();
    });

    // 2. Login Logic
    const handleLogin = async () => {
        const username = usernameInput.value.trim();
        const password = passwordInput.value.trim();
        const captchaCode = captchaInput.value.trim();

        if (!username || !password) {
            showStatus(loginStatus, '請輸入帳號密碼', 'error');
            return;
        }

        if (!isDemoModeEnabled() && !captchaCode) {
            showStatus(loginStatus, '請輸入驗證碼', 'error');
            return;
        }

        if (isDemoModeEnabled()) {
            showStatus(loginStatus, '登入中...', 'normal');
            confirmLogin.disabled = true;
            setTimeout(() => {
                showStatus(loginStatus, '教學帳號登入成功', 'success');
                emitOnboardingEvent(ONBOARDING_EVENTS.LOGIN_SUCCESS);
                setTimeout(() => {
                    toggleModal(loginModal, false);
                    openSelectExamModal();
                    loginStatus.textContent = '';
                    confirmLogin.disabled = false;
                }, 250);
            }, 350);
            return;
        }

        // Turnstile 人機驗證
        let turnstileToken = '';
        try {
            turnstileToken = await requestTurnstileVerification();
        } catch (e) {
            if (e.message === 'cancelled') return;
            showStatus(loginStatus, e.message, 'error');
            return;
        }

        showStatus(loginStatus, '登入中...', 'normal');
        confirmLogin.disabled = true;

        try {
            const res = await fetch(`${API_BASE}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ username, password, captcha_code: captchaCode, turnstile_token: turnstileToken })
            });
            const data = await res.json();

            if (data.success) {
                showStatus(loginStatus, '登入成功', 'success');
                emitOnboardingEvent(ONBOARDING_EVENTS.LOGIN_SUCCESS);
                setTimeout(() => {
                    toggleModal(loginModal, false);
                    openSelectExamModal();
                    passwordInput.value = '';
                    captchaInput.value = '';
                    loginStatus.textContent = '';
                }, 500);
            } else {
                showStatus(loginStatus, data.message || '登入失敗', 'error');
                if (data.need_refresh_captcha) {
                    captchaInput.value = '';
                    await loadSchoolCaptcha();
                }

            }
        } catch (error) {
            showStatus(loginStatus, '連線錯誤: ' + error.message, 'error');

        } finally {
            confirmLogin.disabled = false;
        }
    };

    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', (e) => {
            e.preventDefault();
            handleLogin();
        });
    } else {
        confirmLogin.addEventListener('click', handleLogin);
    }

    if (demoFillLoginBtn) {
        demoFillLoginBtn.addEventListener('click', fillDemoCredentials);
    }

    if (refreshSchoolCaptcha) {
        refreshSchoolCaptcha.addEventListener('click', async () => {
            captchaInput.value = '';
            await loadSchoolCaptcha();
        });
    }

    // Close Login Modal
    closeLoginModal.addEventListener('click', () => toggleModal(loginModal, false));
    cancelLogin.addEventListener('click', () => toggleModal(loginModal, false));

    // 3. Select Exam Logic
    const openSelectExamModal = async (forceReload = false) => {
        toggleModal(selectExamModal, true);

        yearSelect.innerHTML = '<option value="">載入中...</option>';
        examSelect.innerHTML = '<option value="">請選擇考試</option>';
        examSelect.disabled = true;
        confirmFetch.disabled = true;
        fetchStatus.textContent = '';
        availableStructure = {}; // Reset

        if (isDemoModeEnabled()) {
            availableStructure = getDemoStructure();
            yearSelect.innerHTML = '<option value="">請選擇學年度</option>';
            Object.keys(availableStructure).forEach(year => {
                const opt = document.createElement('option');
                opt.value = year;
                opt.textContent = year;
                yearSelect.appendChild(opt);
            });
            examSelect.innerHTML = '<option value="">請選擇考試</option>';
            showStatus(fetchStatus, '請先選擇學年度與考試，再按查詢。', 'normal');
            emitOnboardingEvent(ONBOARDING_EVENTS.SELECT_MODAL_OPEN);
            return;
        }

        try {
            // Fetch ALL structure at once
            const url = forceReload ? `${API_BASE}/structure?reload=true` : `${API_BASE}/structure`;
            const res = await fetch(url, {
                credentials: 'include'
            });

            // Handle Unauthorized (401) -> Redirect to Login
            if (res.status === 401) {
                toggleModal(selectExamModal, false);
                openLoginModal();
                return;
            }

            const data = await res.json();

            if (data.structure && Object.keys(data.structure).length > 0) {
                availableStructure = data.structure;

                yearSelect.innerHTML = '<option value="">請選擇學年度</option>';
                Object.keys(availableStructure).forEach(year => {
                    const opt = document.createElement('option');
                    opt.value = year;
                    opt.textContent = year;
                    yearSelect.appendChild(opt);
                });
            } else {
                yearSelect.innerHTML = '<option>無資料</option>';
            }

            emitOnboardingEvent(ONBOARDING_EVENTS.SELECT_MODAL_OPEN);
        } catch (error) {
            yearSelect.innerHTML = '<option>連線錯誤</option>';
        }
    };

    // Year change -> Load Exams from Local Structure
    yearSelect.addEventListener('change', () => {
        const year = yearSelect.value;
        populateExamSelect(year);
    });

    // Exam change -> Enable button
    examSelect.addEventListener('change', () => {
        confirmFetch.disabled = !examSelect.value;
    });

    // 4. Fetch Grades
    confirmFetch.addEventListener('click', async () => {
        const year = yearSelect.value;
        const exam = examSelect.value;

        if (!year || !exam) return;

        showStatus(fetchStatus, '正在載入成績，請稍候...', 'normal');
        confirmFetch.disabled = true;

        if (isDemoModeEnabled()) {
            try {
                const demoData = getDemoResultData();
                validateGradesData(demoData);
                storeGradesData(demoData);
                initDashboard(demoData);
                showStatus(fetchStatus, '教學資料載入完成。', 'success');
                setTimeout(() => {
                    toggleModal(selectExamModal, false);
                    emitOnboardingEvent(ONBOARDING_EVENTS.FETCH_SUCCESS);
                    confirmFetch.disabled = false;
                }, 700);
            } catch (error) {
                showStatus(fetchStatus, '教學資料載入失敗: ' + error.message, 'error');
                confirmFetch.disabled = false;
            }
            return;
        }

        try {
            const yearData = availableStructure[year];
            const res = await fetch(`${API_BASE}/fetch`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    year_value: yearData?.year_value,
                    exam_value: exam
                })
            });
            const data = await res.json();

            if (data.success) {
                showStatus(fetchStatus, '載入成功！正在更新畫面...', 'success');
                // Update Dashboard
                validateGradesData(data.data);
                storeGradesData(data.data);
                initDashboard(data.data);
                updateActiveShare(data.data).catch((err) => {
                    console.error('Failed to update share', err);
                });

                setTimeout(() => {
                    toggleModal(selectExamModal, false);
                    emitOnboardingEvent(ONBOARDING_EVENTS.FETCH_SUCCESS);
                }, 1000);
            } else {
                showStatus(fetchStatus, data.error || '載入失敗', 'error');
            }
        } catch (error) {
            showStatus(fetchStatus, '發生錯誤: ' + error.message, 'error');
        } finally {
            confirmFetch.disabled = false;
        }
    });

    // Close Select Modal
    closeSelectModal.addEventListener('click', () => toggleModal(selectExamModal, false));
    cancelSelect.addEventListener('click', () => toggleModal(selectExamModal, false));

    // Password Toggle Logic
    const togglePasswordBtn = document.getElementById('togglePasswordBtn');
    if (togglePasswordBtn && passwordInput) {
        togglePasswordBtn.addEventListener('click', () => {
            const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
            passwordInput.setAttribute('type', type);

            // Toggle icons
            const eyeIcon = togglePasswordBtn.querySelector('.eye-icon');
            const eyeOffIcon = togglePasswordBtn.querySelector('.eye-off-icon');

            if (type === 'text') {
                eyeIcon.style.display = 'none';
                eyeOffIcon.style.display = 'block';
                togglePasswordBtn.setAttribute('aria-label', '隱藏密碼');
                togglePasswordBtn.setAttribute('title', '隱藏密碼');
            } else {
                eyeIcon.style.display = 'block';
                eyeOffIcon.style.display = 'none';
                togglePasswordBtn.setAttribute('aria-label', '顯示密碼');
                togglePasswordBtn.setAttribute('title', '顯示密碼');
            }
        });
    }
}
