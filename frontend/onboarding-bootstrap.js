const ONBOARDING_COMPLETED_KEY = 'onboardingCompleted';
const ONBOARDING_PROMPT_DISMISSED_KEY = 'onboardingPromptDismissed';
const ONBOARDING_REOPEN_TIP_DISABLED_KEY = 'onboardingReopenTipDisabled';
const ONBOARDING_SESSION_ENDED_EVENT = 'onboarding:session-ended';

let startPromptEl = null;
let reopenTipEl = null;
let reopenTipTimer = null;
let onboardingCorePromise = null;
let onboardingRunning = false;

export function setupOnboardingBootstrap() {
    if (window.location.pathname.startsWith('/share/')) return;

    window.addEventListener(ONBOARDING_SESSION_ENDED_EVENT, () => {
        onboardingRunning = false;
        removeStartPrompt();
        showReopenTip();
    });

    waitForDisclaimerClosed().then(() => {
        const shownFirstPrompt = maybeShowFirstVisitPrompt();
        if (!shownFirstPrompt) {
            showReopenTip();
        }
    });
}

function maybeShowFirstVisitPrompt() {
    if (localStorage.getItem(ONBOARDING_COMPLETED_KEY) === 'true') return false;
    if (sessionStorage.getItem(ONBOARDING_PROMPT_DISMISSED_KEY) === 'true') return false;
    if (onboardingRunning || startPromptEl) return false;
    showStartPrompt();
    return true;
}

function waitForDisclaimerClosed() {
    return new Promise((resolve) => {
        const check = () => {
            const disclaimerModal = document.getElementById('disclaimerModal');
            if (disclaimerModal?.classList.contains('active')) {
                setTimeout(check, 250);
                return;
            }
            resolve();
        };
        setTimeout(check, 300);
    });
}

function showStartPrompt() {
    removeReopenTip();
    startPromptEl = document.createElement('div');
    startPromptEl.className = 'tour-start-overlay';
    startPromptEl.innerHTML = `
        <div class="tour-start-dialog">
            <h3>首次使用教學</h3>
            <p>要開始教學嗎？會用內建教學資料示範一次匯入與分享流程。</p>
            <div class="tour-start-actions">
                <button type="button" class="tour-btn" data-role="later">稍後再說</button>
                <button type="button" class="tour-btn primary" data-role="start">開始教學</button>
            </div>
        </div>
    `;

    startPromptEl.querySelector('[data-role="later"]')?.addEventListener('click', () => {
        sessionStorage.setItem(ONBOARDING_PROMPT_DISMISSED_KEY, 'true');
        removeStartPrompt();
        showReopenTip();
    });

    startPromptEl.querySelector('[data-role="start"]')?.addEventListener('click', () => {
        startOnboarding('auto');
    });

    document.body.appendChild(startPromptEl);
}

function removeStartPrompt() {
    if (!startPromptEl) return;
    startPromptEl.remove();
    startPromptEl = null;
}

function showReopenTip() {
    if (localStorage.getItem(ONBOARDING_REOPEN_TIP_DISABLED_KEY) === 'true') return;
    if (onboardingRunning || startPromptEl || reopenTipEl) return;

    reopenTipEl = document.createElement('div');
    reopenTipEl.className = 'onboarding-reopen-tip';
    reopenTipEl.innerHTML = `
        <span class="onboarding-reopen-text">要重新跑一次使用教學嗎？</span>
        <button type="button" class="onboarding-reopen-disable" data-role="disable">不再提示</button>
        <button type="button" class="onboarding-reopen-start" data-role="start">開始教學</button>
        <button type="button" class="onboarding-reopen-close" data-role="close" aria-label="關閉提示">×</button>
    `;

    reopenTipEl.querySelector('[data-role="close"]')?.addEventListener('click', () => {
        removeReopenTip();
    });
    reopenTipEl.querySelector('[data-role="disable"]')?.addEventListener('click', () => {
        localStorage.setItem(ONBOARDING_REOPEN_TIP_DISABLED_KEY, 'true');
        removeReopenTip();
    });
    reopenTipEl.querySelector('[data-role="start"]')?.addEventListener('click', () => {
        startOnboarding('manual');
    });

    document.body.appendChild(reopenTipEl);
    reopenTipTimer = setTimeout(() => {
        removeReopenTip();
    }, 5000);
}

function removeReopenTip() {
    if (!reopenTipEl) return;
    if (reopenTipTimer) {
        clearTimeout(reopenTipTimer);
        reopenTipTimer = null;
    }
    reopenTipEl.remove();
    reopenTipEl = null;
}

function loadOnboardingCore() {
    if (!onboardingCorePromise) {
        onboardingCorePromise = import('./onboarding.js');
    }
    return onboardingCorePromise;
}

function startOnboarding(trigger) {
    removeStartPrompt();
    removeReopenTip();
    onboardingRunning = true;

    loadOnboardingCore()
        .then(({ startOnboardingSession }) => {
            startOnboardingSession(trigger);
        })
        .catch((error) => {
            onboardingRunning = false;
            console.warn('Failed to load onboarding core', error);
            showReopenTip();
        });
}
