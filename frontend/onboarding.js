import { setDemoModeEnabled } from './demo-mode.js';
import { ONBOARDING_EVENTS, ONBOARDING_COMPLETED_KEY, ONBOARDING_SESSION_ENDED_EVENT } from './onboarding-events.js';
import { loadGradesData } from './storage.js';
import { resetCharts } from './charts.js';


const TOUR_STEPS = [
    {
        title: 'Step 1 / 匯入成績',
        message: '先點「匯入成績」，教學會帶你完成一次示範流程。',
        selector: '[data-tour="import-btn"]',
        context: 'main',
        completion: { type: 'event', eventName: ONBOARDING_EVENTS.LOGIN_MODAL_OPEN }
    },
    {
        title: 'Step 2 / 一鍵填入教學帳密',
        message: '先按一鍵填入，系統會帶入教學帳號與密碼。',
        selector: '[data-tour="demo-fill-login"]',
        context: 'login',
        completion: { type: 'event', eventName: ONBOARDING_EVENTS.DEMO_CREDENTIALS_FILLED }
    },
    {
        title: 'Step 3 / 登入',
        message: '資料已帶入，請按登入。',
        selector: '[data-tour="login-submit"]',
        context: 'login',
        completion: { type: 'event', eventName: ONBOARDING_EVENTS.LOGIN_SUCCESS }
    },
    {
        title: 'Step 4 / 選擇學年度',
        message: '請自行選擇學年度。',
        selector: '[data-tour="year-select"]',
        context: 'select',
        completion: { type: 'interaction', events: ['change'] }
    },
    {
        title: 'Step 5 / 選擇考試',
        message: '請自行選擇考試名稱。',
        selector: '[data-tour="exam-select"]',
        context: 'select',
        completion: { type: 'interaction', events: ['change'] }
    },
    {
        title: 'Step 6 / 查詢',
        message: '按「查詢」載入教學用成績結果。',
        selector: '[data-tour="fetch-btn"]',
        context: 'select',
        completion: { type: 'event', eventName: ONBOARDING_EVENTS.FETCH_SUCCESS }
    },
    {
        title: 'Step 7 / 看看結果',
        message: '先看一下成績分析畫面，準備好後再按「繼續教學」。',
        selector: null,
        context: 'main',
        presentation: 'overview',
        allowInteraction: true,
        completion: { type: 'manual', nextLabel: '繼續教學' }
    },
    {
        title: 'Step 8 / 分享',
        message: '現在點右上角「分享」。',
        selector: '[data-tour="share-btn"]',
        context: 'share-button',
        completion: { type: 'event', eventName: ONBOARDING_EVENTS.SHARE_MODAL_OPEN }
    },
    {
        title: 'Step 9 / 建立分享連結',
        message: '按「建立分享連結」完成示範流程。',
        selector: '[data-tour="create-share-link"]',
        context: 'share-create',
        completion: { type: 'event', eventName: ONBOARDING_EVENTS.SHARE_LINK_CREATED }
    },
    {
        title: '完成',
        message: '你已完成第一次體驗，接下來可以匯入自己的成績。',
        selector: null,
        context: 'main',
        completion: { type: 'finish' }
    }
];

const FLOW_STEP_TOTAL = TOUR_STEPS.filter((step) => step.completion?.type !== 'finish').length;

let tourState = null;
let preOnboardingGradesData = undefined;
let repositionRafId = null;

export function startOnboardingSession(trigger = 'manual') {
    if (window.location.pathname.startsWith('/share/')) return;
    startOnboarding(trigger);
}

function startOnboarding(trigger) {
    if (tourState) {
        stopOnboarding('restart');
    }

    if (preOnboardingGradesData === undefined) {
        preOnboardingGradesData = localStorage.getItem('gradesData');
    }
    setDemoModeEnabled(true);

    const root = document.createElement('div');
    root.className = 'tour-root';
    root.innerHTML = `
        <div class="tour-backdrop"></div>
        <div class="tour-highlight"></div>
        <div class="tour-popover">
            <div class="tour-step"></div>
            <div class="tour-title"></div>
            <div class="tour-body"></div>
            <div class="tour-actions"></div>
        </div>
    `;
    document.body.appendChild(root);

    tourState = {
        trigger,
        root,
        backdrop: root.querySelector('.tour-backdrop'),
        highlight: root.querySelector('.tour-highlight'),
        popover: root.querySelector('.tour-popover'),
        stepLabel: root.querySelector('.tour-step'),
        title: root.querySelector('.tour-title'),
        body: root.querySelector('.tour-body'),
        actions: root.querySelector('.tour-actions'),
        stepIndex: -1,
        stepToken: 0,
        activeTarget: null,
        cleanupWatcher: null,
        cleanupGuards: null,
        cleanupPositioning: null
    };

    tourState.cleanupGuards = attachInteractionGuards();
    tourState.cleanupPositioning = setupPositioningListeners();
    scheduleReposition();
    goToStep(0);
}

function stopOnboarding(reason) {
    if (!tourState) return;

    if (tourState.cleanupWatcher) {
        tourState.cleanupWatcher();
    }
    if (tourState.cleanupGuards) {
        tourState.cleanupGuards();
    }
    if (tourState.cleanupPositioning) {
        tourState.cleanupPositioning();
    }
    if (repositionRafId !== null) {
        cancelAnimationFrame(repositionRafId);
        repositionRafId = null;
    }

    tourState.root.remove();
    tourState = null;
    setDemoModeEnabled(false);

    if (reason !== 'restart') {
        restorePreOnboardingState();
    }

    window.dispatchEvent(new CustomEvent(ONBOARDING_SESSION_ENDED_EVENT, { detail: { reason } }));
}

async function goToStep(index) {
    if (!tourState) return;
    if (index < 0 || index >= TOUR_STEPS.length) return;

    if (tourState.cleanupWatcher) {
        tourState.cleanupWatcher();
        tourState.cleanupWatcher = null;
    }

    tourState.stepIndex = index;
    tourState.stepToken += 1;
    const stepToken = tourState.stepToken;
    const step = TOUR_STEPS[index];

    renderStepContent(step, index);
    renderStepActions(step, index);
    ensureStepContext(step);
    scheduleReposition();

    if (!step.selector) {
        if (step.presentation === 'overview') {
            renderOverviewStep();
        } else {
            renderCenteredStep();
        }
        scheduleReposition();
        return;
    }

    tourState.activeTarget = null;
    renderCenteredStep();
    scheduleReposition();

    const target = await waitForElement(step.selector, 10000);
    if (!tourState || stepToken !== tourState.stepToken) return;

    if (!target) {
        renderMissingTarget(step);
        setTimeout(() => {
            if (tourState && stepToken === tourState.stepToken) {
                goToStep(index + 1);
            }
        }, 900);
        return;
    }

    tourState.activeTarget = target;
    scrollIntoViewIfNeeded(target);
    repositionCurrentStep();
    scheduleReposition();
    tourState.cleanupWatcher = bindCompletionWatcher(step, target, stepToken);
}

function renderStepContent(step, index) {
    if (!tourState) return;
    const isFinish = step.completion?.type === 'finish';
    tourState.stepLabel.textContent = isFinish ? '教學完成' : `步驟 ${index + 1} / ${FLOW_STEP_TOTAL}`;
    tourState.title.textContent = step.title;
    tourState.body.textContent = step.message;
}

function renderStepActions(step, index) {
    if (!tourState) return;

    const actions = [];
    const canGoBack = index > 0;
    const completionType = step.completion?.type;

    if (canGoBack) {
        actions.push({
            role: 'back',
            label: '上一步',
            primary: false,
            onClick: () => goToStep(index - 1)
        });
    }

    if (completionType === 'finish') {
        actions.push({
            role: 'restart',
            label: '重新開始',
            primary: false,
            onClick: () => startOnboarding('manual')
        });
        actions.push({
            role: 'finish',
            label: '開始使用',
            primary: true,
            onClick: () => {
                localStorage.setItem(ONBOARDING_COMPLETED_KEY, 'true');
                stopOnboarding('completed');
            }
        });
    } else if (completionType === 'manual') {
        actions.push({
            role: 'skip',
            label: '跳過教學',
            primary: false,
            onClick: () => stopOnboarding('skipped')
        });
        actions.push({
            role: 'next',
            label: step.completion.nextLabel || '下一步',
            primary: true,
            onClick: () => goToStep(index + 1)
        });
    } else {
        actions.push({
            role: 'skip',
            label: '跳過教學',
            primary: false,
            onClick: () => stopOnboarding('skipped')
        });
    }

    tourState.actions.innerHTML = actions
        .map((item) => `<button type="button" class="tour-btn${item.primary ? ' primary' : ''}" data-role="${item.role}">${item.label}</button>`)
        .join('');

    actions.forEach((item) => {
        tourState.actions.querySelector(`[data-role="${item.role}"]`)?.addEventListener('click', item.onClick);
    });
}

function renderMissingTarget(step) {
    if (!tourState) return;
    tourState.stepLabel.textContent = '提示';
    tourState.title.textContent = step.title;
    tourState.body.textContent = '這一步的目標元件尚未出現，系統將自動跳到下一步。';
    renderCenteredStep();
}

function renderCenteredStep() {
    if (!tourState) return;
    tourState.activeTarget = null;
    tourState.highlight.style.display = 'none';
    tourState.backdrop.style.display = 'block';

    const maxX = Math.max(12, window.innerWidth - tourState.popover.offsetWidth - 12);
    const left = Math.round(Math.min(Math.max((window.innerWidth - tourState.popover.offsetWidth) / 2, 12), maxX));
    const top = Math.round(Math.max((window.innerHeight - tourState.popover.offsetHeight) / 2, 40));
    tourState.popover.style.left = `${left}px`;
    tourState.popover.style.top = `${top}px`;
}

function renderOverviewStep() {
    if (!tourState) return;
    tourState.activeTarget = null;
    tourState.highlight.style.display = 'none';
    tourState.backdrop.style.display = 'none';

    const popoverWidth = tourState.popover.offsetWidth;
    const left = Math.max(12, window.innerWidth - popoverWidth - 12);
    tourState.popover.style.left = `${Math.round(left)}px`;
    tourState.popover.style.top = '12px';
}

function repositionCurrentStep() {
    if (!tourState) return;
    if (!tourState.activeTarget || !document.body.contains(tourState.activeTarget)) return;

    const rect = tourState.activeTarget.getBoundingClientRect();
    if (!isRectVisible(rect)) return;

    const padding = 8;
    const left = Math.max(8, rect.left - padding);
    const top = Math.max(8, rect.top - padding);
    const width = Math.min(window.innerWidth - left - 8, rect.width + padding * 2);
    const height = Math.min(window.innerHeight - top - 8, rect.height + padding * 2);

    tourState.backdrop.style.display = 'none';
    tourState.highlight.style.display = 'block';
    tourState.highlight.style.left = `${Math.round(left)}px`;
    tourState.highlight.style.top = `${Math.round(top)}px`;
    tourState.highlight.style.width = `${Math.round(width)}px`;
    tourState.highlight.style.height = `${Math.round(height)}px`;
    positionPopover(rect);
}

function setupPositioningListeners() {
    const onViewportChanged = () => scheduleReposition();
    window.addEventListener('resize', onViewportChanged, { passive: true });
    window.addEventListener('scroll', onViewportChanged, { passive: true, capture: true });
    document.addEventListener('transitionend', onViewportChanged, true);

    return () => {
        window.removeEventListener('resize', onViewportChanged);
        window.removeEventListener('scroll', onViewportChanged, true);
        document.removeEventListener('transitionend', onViewportChanged, true);
    };
}

function scheduleReposition() {
    if (!tourState) return;
    if (repositionRafId !== null) return;

    repositionRafId = requestAnimationFrame(() => {
        repositionRafId = null;
        repositionCurrentStep();
    });
}

function positionPopover(targetRect) {
    if (!tourState) return;
    const popover = tourState.popover;
    const margin = 12;
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const popoverWidth = popover.offsetWidth;
    const popoverHeight = popover.offsetHeight;

    let top = targetRect.bottom + margin;
    if (top + popoverHeight > viewportHeight - 8) {
        top = targetRect.top - popoverHeight - margin;
    }
    if (top < 8) {
        top = Math.max(8, (viewportHeight - popoverHeight) / 2);
    }

    let left = targetRect.left;
    if (left + popoverWidth > viewportWidth - 8) {
        left = viewportWidth - popoverWidth - 8;
    }
    if (left < 8) {
        left = 8;
    }

    popover.style.left = `${Math.round(left)}px`;
    popover.style.top = `${Math.round(top)}px`;
}

function bindCompletionWatcher(step, target, stepToken) {
    const next = () => {
        if (!tourState || stepToken !== tourState.stepToken) return;
        goToStep(tourState.stepIndex + 1);
    };

    if (step.completion?.type === 'event' && step.completion.eventName) {
        const handler = () => next();
        window.addEventListener(step.completion.eventName, handler, { once: true });
        return () => window.removeEventListener(step.completion.eventName, handler);
    }

    if (step.completion?.type === 'interaction') {
        const listeners = [];
        (step.completion.events || []).forEach((eventName) => {
            const handler = () => next();
            target.addEventListener(eventName, handler, { once: true });
            listeners.push([eventName, handler]);
        });
        return () => {
            listeners.forEach(([eventName, handler]) => {
                target.removeEventListener(eventName, handler);
            });
        };
    }

    return null;
}

function ensureStepContext(step) {
    const loginModal = document.getElementById('loginModal');
    const selectExamModal = document.getElementById('selectExamModal');
    const shareModal = document.getElementById('shareModal');
    const demoFillLoginBtn = document.getElementById('demoFillLoginBtn');
    const shareBtn = document.getElementById('shareBtn');
    const syncBtn = document.getElementById('syncBtn');

    if (!loginModal || !selectExamModal || !shareModal) return;

    const showLogin = () => {
        selectExamModal.classList.remove('active');
        shareModal.classList.remove('active');
        loginModal.classList.add('active');
        if (demoFillLoginBtn) {
            demoFillLoginBtn.style.display = 'inline-flex';
        }
    };

    const showSelect = () => {
        loginModal.classList.remove('active');
        shareModal.classList.remove('active');
        selectExamModal.classList.add('active');
    };

    const showMain = () => {
        loginModal.classList.remove('active');
        selectExamModal.classList.remove('active');
        shareModal.classList.remove('active');
    };

    if (step.context === 'login') {
        if (!loginModal.classList.contains('active')) {
            if (syncBtn) syncBtn.click();
            setTimeout(() => showLogin(), 0);
        } else {
            showLogin();
        }
        return;
    }

    if (step.context === 'select') {
        showSelect();
        return;
    }

    if (step.context === 'share-button') {
        showMain();
        return;
    }

    if (step.context === 'share-create') {
        showMain();
        const createBtn = document.querySelector('[data-tour="create-share-link"]');
        if (!createBtn && shareBtn) {
            shareBtn.click();
        } else {
            shareModal.classList.add('active');
        }
        return;
    }

    showMain();
}

function restorePreOnboardingState() {
    const loginModal = document.getElementById('loginModal');
    const selectExamModal = document.getElementById('selectExamModal');
    const shareModal = document.getElementById('shareModal');
    const turnstileModal = document.getElementById('turnstileModal');
    const demoFillLoginBtn = document.getElementById('demoFillLoginBtn');

    [loginModal, selectExamModal, shareModal, turnstileModal].forEach((modal) => {
        if (modal) modal.classList.remove('active');
    });
    if (demoFillLoginBtn) {
        demoFillLoginBtn.style.display = 'none';
    }

    const snapshot = preOnboardingGradesData;
    preOnboardingGradesData = undefined;

    localStorage.removeItem('gradesData');
    if (snapshot !== null && snapshot !== undefined) {
        localStorage.setItem('gradesData', snapshot);
        void loadGradesData();
        return;
    }

    resetDashboardToEmptyState();
}

function resetDashboardToEmptyState() {
    const setText = (id, value) => {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    };

    setText('examTitle', '--');
    setText('updateTime', '無資料');
    setText('avatarText', '--');
    setText('studentName', '--');
    setText('studentClass', '--');
    setText('studentSeat', '--');
    setText('studentNo', '--');
    setText('avgScore', '--');
    setText('totalSubjects', '--');
    setText('highestScore', '--');
    setText('classRank', '--');
    setText('categoryRank', '--');

    const classRankBox = document.getElementById('classRankBox');
    const categoryRankBox = document.getElementById('categoryRankBox');
    if (classRankBox) classRankBox.style.display = 'none';
    if (categoryRankBox) categoryRankBox.style.display = 'none';

    const scoresGrid = document.getElementById('scoresGrid');
    const standardsBody = document.getElementById('standardsBody');
    const distributionGrid = document.getElementById('distributionGrid');
    if (scoresGrid) scoresGrid.innerHTML = '';
    if (standardsBody) standardsBody.innerHTML = '';
    if (distributionGrid) distributionGrid.innerHTML = '';

    resetCharts();
}

function attachInteractionGuards() {
    const guard = (event) => {
        if (!tourState) return;
        if (isAllowedTarget(event.target)) return;
        event.preventDefault();
        event.stopPropagation();
        if (typeof event.stopImmediatePropagation === 'function') {
            event.stopImmediatePropagation();
        }
    };

    const eventNames = ['pointerdown', 'click', 'focusin'];
    eventNames.forEach((eventName) => {
        document.addEventListener(eventName, guard, true);
    });

    return () => {
        eventNames.forEach((eventName) => {
            document.removeEventListener(eventName, guard, true);
        });
    };
}

function isAllowedTarget(target) {
    if (!tourState) return true;
    const currentStep = TOUR_STEPS[tourState.stepIndex];
    if (currentStep?.allowInteraction) return true;
    if (!(target instanceof Node)) return false;
    if (tourState.popover?.contains(target)) return true;
    if (tourState.activeTarget?.contains(target)) return true;
    return false;
}

function waitForElement(selector, timeoutMs) {
    const existed = document.querySelector(selector);
    if (existed) {
        return Promise.resolve(existed);
    }

    return new Promise((resolve) => {
        let settled = false;
        const finish = (result) => {
            if (settled) return;
            settled = true;
            observer.disconnect();
            clearTimeout(timeoutId);
            resolve(result);
        };

        const findAndResolve = () => {
            const el = document.querySelector(selector);
            if (el) finish(el);
        };

        const observer = new MutationObserver(findAndResolve);
        observer.observe(document.documentElement, { childList: true, subtree: true });
        const timeoutId = setTimeout(() => finish(null), timeoutMs);
        findAndResolve();
    });
}

function scrollIntoViewIfNeeded(el) {
    const rect = el.getBoundingClientRect();
    const inView = rect.top >= 0 && rect.bottom <= window.innerHeight;
    if (!inView) {
        el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
    }
}

function isRectVisible(rect) {
    return rect.width > 0 && rect.height > 0 && rect.bottom >= 0 && rect.right >= 0;
}
