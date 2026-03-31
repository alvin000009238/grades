(() => {
    try {
        const storedTheme = localStorage.getItem('grades-theme');
        if (storedTheme === 'light') {
            document.documentElement.setAttribute('data-theme', 'light');
        }
    } catch {
        // Ignore storage access failures (privacy mode / blocked storage)
        // and keep default theme.
    }
})();
