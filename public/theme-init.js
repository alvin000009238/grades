(() => {
    const storedTheme = localStorage.getItem('grades-theme');
    if (storedTheme === 'light') {
        document.documentElement.setAttribute('data-theme', 'light');
    }
})();
