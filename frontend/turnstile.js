// ========================================
// Turnstile 人機驗證
// ========================================

let turnstileSiteKey = '';
let configLoaded = false;

export async function loadTurnstileConfig() {
    if (configLoaded) return;
    configLoaded = true;
    try {
        const res = await fetch('/api/turnstile-config');
        const data = await res.json();
        turnstileSiteKey = data.siteKey || '';
    } catch (e) {
        configLoaded = false; // 允許重試
        console.warn('Failed to load Turnstile config', e);
    }
}

export function requestTurnstileVerification() {
    return new Promise(async (resolve, reject) => {
        // Lazy load config on first verification
        if (!configLoaded) {
            await loadTurnstileConfig();
        }

        // 若未設定 site key，直接放行
        if (!turnstileSiteKey) {
            resolve('');
            return;
        }

        const modal = document.getElementById('turnstileModal');
        const container = document.getElementById('turnstileWidgetContainer');
        const cancelBtn = document.getElementById('cancelTurnstile');

        // 清空舊 widget
        container.innerHTML = '';
        modal.classList.add('active');

        let widgetId = null;
        let settled = false;

        const cleanup = () => {
            if (!settled) settled = true;
            modal.classList.remove('active');
            if (widgetId !== null && typeof turnstile !== 'undefined') {
                try { turnstile.remove(widgetId); } catch (_) { /* ignore */ }
            }
            container.innerHTML = '';
        };

        // 等待 Turnstile SDK 載入
        const renderWidget = () => {
            if (typeof turnstile === 'undefined') {
                setTimeout(renderWidget, 200);
                return;
            }
            widgetId = turnstile.render(container, {
                sitekey: turnstileSiteKey,
                callback: (token) => {
                    cleanup();
                    resolve(token);
                },
                'error-callback': () => {
                    cleanup();
                    reject(new Error('人機驗證失敗'));
                },
            });
        };

        renderWidget();

        cancelBtn.onclick = () => {
            cleanup();
            reject(new Error('cancelled'));
        };
    });
}
