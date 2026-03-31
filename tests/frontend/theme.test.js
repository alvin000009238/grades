import { beforeEach, afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { getInitialTheme, applyTheme, setupThemeToggle } from '../../frontend/theme.js';

let dom;
let cleanupGlobals = [];

function installDom(html = '<!doctype html><html><body></body></html>') {
    dom = new JSDOM(html, { url: 'https://example.test' });
    const map = {
        window: dom.window,
        document: dom.window.document,
        localStorage: dom.window.localStorage,
        CustomEvent: dom.window.CustomEvent,
        getComputedStyle: dom.window.getComputedStyle
    };

    cleanupGlobals = Object.entries(map).map(([key, value]) => {
        const previous = globalThis[key];
        globalThis[key] = value;
        return [key, previous];
    });
}

function uninstallDom() {
    for (const [key, previous] of cleanupGlobals) {
        if (previous === undefined) {
            delete globalThis[key];
        } else {
            globalThis[key] = previous;
        }
    }
    cleanupGlobals = [];
    dom?.window?.close();
}

describe('theme.js', () => {
    beforeEach(() => {
        installDom(`<!doctype html><html><body>
            <button id="themeToggleBtn" aria-label="" title="">
                <span class="theme-icon theme-icon-moon"></span>
                <span class="theme-icon theme-icon-sun hidden"></span>
            </button>
        </body></html>`);
    });

    afterEach(() => {
        uninstallDom();
    });

    it('getInitialTheme should handle stored light/dark/invalid/missing values', async () => {
        localStorage.setItem('grades-theme', 'light');
        assert.equal(getInitialTheme(), 'light');

        localStorage.setItem('grades-theme', 'dark');
        assert.equal(getInitialTheme(), 'dark');

        localStorage.setItem('grades-theme', 'unexpected');
        assert.equal(getInitialTheme(), 'dark');

        localStorage.removeItem('grades-theme');
        assert.equal(getInitialTheme(), 'dark');
    });

    it('applyTheme should update data-theme, icon states, aria-label and title', async () => {
        const root = document.documentElement;
        const btn = document.getElementById('themeToggleBtn');
        const moon = btn.querySelector('.theme-icon-moon');
        const sun = btn.querySelector('.theme-icon-sun');

        applyTheme('light');
        assert.equal(root.getAttribute('data-theme'), 'light');
        assert.equal(sun.classList.contains('hidden'), false);
        assert.equal(moon.classList.contains('hidden'), true);
        assert.equal(btn.getAttribute('aria-label'), '切換至深色模式');
        assert.equal(btn.getAttribute('title'), '切換至深色模式');

        applyTheme('dark');
        assert.equal(root.hasAttribute('data-theme'), false);
        assert.equal(sun.classList.contains('hidden'), true);
        assert.equal(moon.classList.contains('hidden'), false);
        assert.equal(btn.getAttribute('aria-label'), '切換至淺色模式');
        assert.equal(btn.getAttribute('title'), '切換至淺色模式');
    });

    it('setupThemeToggle should toggle theme, persist localStorage and dispatch themechange', async () => {
        localStorage.setItem('grades-theme', 'dark');

        const events = [];
        document.addEventListener('themechange', (event) => events.push(event.detail.theme));

        setupThemeToggle();
        const btn = document.getElementById('themeToggleBtn');

        btn.click();
        assert.equal(document.documentElement.getAttribute('data-theme'), 'light');
        assert.equal(localStorage.getItem('grades-theme'), 'light');

        btn.click();
        assert.equal(document.documentElement.hasAttribute('data-theme'), false);
        assert.equal(localStorage.getItem('grades-theme'), 'dark');

        assert.deepEqual(events, ['dark', 'light', 'dark']);
    });

    it('chart integration should re-apply chart palette on themechange', async () => {
        document.documentElement.style.setProperty('--color-text-main', 'rgb(1, 2, 3)');
        document.documentElement.style.setProperty('--color-text-secondary', 'rgb(10, 20, 30)');
        document.documentElement.style.setProperty('--color-text-muted', 'rgb(40, 50, 60)');
        document.documentElement.style.setProperty('--color-surface-elevated', 'rgb(70, 80, 90)');
        document.documentElement.style.setProperty('--color-border-subtle', 'rgba(1,1,1,0.3)');
        document.documentElement.style.setProperty('--color-border-extra-subtle', 'rgba(2,2,2,0.2)');

        const charts = await import('../../frontend/charts.js');

        const radar = {
            options: {
                scales: { r: { ticks: {}, grid: {}, angleLines: {}, pointLabels: {} } },
                plugins: { legend: { labels: {} } }
            },
            data: { datasets: [{}, {}] },
            updateCalls: 0,
            update() { this.updateCalls += 1; }
        };

        const bar = {
            options: {
                scales: { x: { ticks: {}, grid: {} }, y: { ticks: {}, grid: {} } },
                plugins: { legend: { labels: {} } }
            },
            data: { datasets: [] },
            updateCalls: 0,
            update() { this.updateCalls += 1; }
        };

        charts.__setChartInstancesForTest({ radar, bar });
        applyTheme('light');

        assert.equal(radar.options.scales.r.grid.color, 'rgba(1,1,1,0.3)');
        assert.equal(bar.options.scales.x.grid.color, 'rgba(2,2,2,0.2)');
        assert.equal(radar.data.datasets[0].pointBorderColor, 'rgb(70, 80, 90)');
        assert.equal(radar.updateCalls, 1);
        assert.equal(bar.updateCalls, 1);

        document.documentElement.style.setProperty('--color-border-subtle', 'rgba(9,9,9,0.9)');
        document.documentElement.style.setProperty('--color-border-extra-subtle', 'rgba(8,8,8,0.8)');
        document.documentElement.style.setProperty('--color-surface-elevated', 'rgb(7, 8, 9)');

        applyTheme('dark');

        assert.equal(radar.options.scales.r.grid.color, 'rgba(9,9,9,0.9)');
        assert.equal(bar.options.scales.x.grid.color, 'rgba(8,8,8,0.8)');
        assert.equal(radar.data.datasets[0].pointBorderColor, 'rgb(7, 8, 9)');
        assert.equal(radar.updateCalls, 2);
        assert.equal(bar.updateCalls, 2);
    });
});
