const STORAGE_KEY = 'grades-theme';

function getInitialTheme() {
    const storedTheme = localStorage.getItem(STORAGE_KEY);
    if (storedTheme === 'light' || storedTheme === 'dark') return storedTheme;
    return 'dark';
}

function applyTheme(theme) {
    const root = document.documentElement;
    if (theme === 'light') {
        root.setAttribute('data-theme', 'light');
    } else {
        root.removeAttribute('data-theme');
    }

    const toggleBtn = document.getElementById('themeToggleBtn');
    const sunIcon = toggleBtn?.querySelector('.theme-icon-sun');
    const moonIcon = toggleBtn?.querySelector('.theme-icon-moon');

    if (sunIcon && moonIcon) {
        const isLight = theme === 'light';
        sunIcon.classList.toggle('hidden', !isLight);
        moonIcon.classList.toggle('hidden', isLight);
    }

    if (toggleBtn) {
        const nextThemeLabel = theme === 'light' ? '深色' : '淺色';
        toggleBtn.setAttribute('aria-label', `切換至${nextThemeLabel}模式`);
        toggleBtn.setAttribute('title', `切換至${nextThemeLabel}模式`);
    }

    document.dispatchEvent(new CustomEvent('themechange', {
        detail: { theme }
    }));
}

export function setupThemeToggle() {
    const toggleBtn = document.getElementById('themeToggleBtn');
    if (!toggleBtn) return;

    let currentTheme = getInitialTheme();
    applyTheme(currentTheme);

    toggleBtn.addEventListener('click', () => {
        currentTheme = currentTheme === 'light' ? 'dark' : 'light';
        applyTheme(currentTheme);
        localStorage.setItem(STORAGE_KEY, currentTheme);
    });
}
