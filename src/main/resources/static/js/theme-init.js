(function () {
    var preference = 'light';
    try {
        var saved = window.localStorage.getItem('jagaq-theme');
        if (saved === 'light' || saved === 'dark' || saved === 'system') preference = saved;
    } catch (error) {
        // Light is the safe default when browser storage is unavailable.
    }

    var systemDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    var theme = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference;
    document.documentElement.setAttribute('data-theme-preference', preference);
    document.documentElement.setAttribute('data-theme', theme);
})();
