(function () {
    var validPreferences = ['light', 'dark', 'system'];
    var root = document.documentElement;
    var controls = document.querySelectorAll('input[name="theme-preference"]');
    if (!controls.length) return;
    var icons = document.querySelectorAll('[data-theme-icon]');
    var settings = document.querySelector('.theme-settings');
    var settingsButton = settings && settings.querySelector('summary');

    function updateThemeIcon(preference) {
        icons.forEach(function (icon) {
            icon.style.display = icon.getAttribute('data-theme-icon') === preference
                ? 'inline-block'
                : 'none';
        });
    }

    function resolveTheme(preference) {
        if (preference !== 'system') return preference;
        return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
            ? 'dark'
            : 'light';
    }

    function applyPreference(preference, persist) {
        if (validPreferences.indexOf(preference) < 0) preference = 'light';
        root.setAttribute('data-theme-preference', preference);
        root.setAttribute('data-theme', resolveTheme(preference));
        controls.forEach(function (control) {
            control.checked = control.value === preference;
        });
        updateThemeIcon(preference);
        if (settingsButton) {
            var label = 'Theme: ' + preference.charAt(0).toUpperCase() + preference.slice(1);
            settingsButton.setAttribute('aria-label', label);
            settingsButton.setAttribute('title', label);
        }
        if (persist) {
            try {
                window.localStorage.setItem('jagaq-theme', preference);
            } catch (error) {
                // The current page still uses the selected theme if storage is unavailable.
            }
        }
    }

    var initialPreference = root.getAttribute('data-theme-preference')
        || (root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');
    applyPreference(initialPreference, false);

    function closeAfterSelection() {
        if (settings) settings.open = false;

        var navigationMenu = document.getElementById('navbarNav');
        var menuButton = document.querySelector('.navbar-toggler');
        var isMobile = window.matchMedia && window.matchMedia('(max-width: 991.98px)').matches;
        if (isMobile && navigationMenu && navigationMenu.classList.contains('show')) {
            navigationMenu.classList.remove('show');
            if (menuButton) {
                menuButton.setAttribute('aria-expanded', 'false');
                menuButton.focus();
            }
        } else if (settingsButton) {
            settingsButton.focus();
        }
    }

    controls.forEach(function (control) {
        control.addEventListener('change', function () {
            if (control.checked) {
                applyPreference(control.value, true);
                closeAfterSelection();
            }
        });
    });

    if (window.matchMedia) {
        var systemPreference = window.matchMedia('(prefers-color-scheme: dark)');
        var updateSystemTheme = function () {
            if (root.getAttribute('data-theme-preference') === 'system') {
                root.setAttribute('data-theme', resolveTheme('system'));
            }
        };
        if (systemPreference.addEventListener) systemPreference.addEventListener('change', updateSystemTheme);
        else if (systemPreference.addListener) systemPreference.addListener(updateSystemTheme);
    }
})();
