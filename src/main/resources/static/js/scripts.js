document.addEventListener('DOMContentLoaded', function () {
    const scrollLinks = document.querySelectorAll('a[data-scroll]');

    scrollLinks.forEach(link => {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            const targetId = this.getAttribute('href');
            const targetElement = document.querySelector(targetId);
            if (targetElement) {
                targetElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    });

    document.querySelectorAll('[data-oauth-login]').forEach(link => {
        link.addEventListener('click', function () {
            this.setAttribute('aria-busy', 'true');
            const label = this.querySelector('span');
            if (label) {
                label.textContent = 'Redirecting...';
            }
        });
    });

    document.querySelectorAll('[data-password-toggle]').forEach(button => {
        const inputId = button.getAttribute('aria-controls');
        const input = inputId ? document.getElementById(inputId) : null;
        if (!(input instanceof HTMLInputElement)) {
            console.error('Password visibility control must reference an input element.');
            return;
        }

        button.addEventListener('click', function () {
            const showPassword = input.type === 'password';
            input.type = showPassword ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(showPassword));
            button.setAttribute('aria-label', showPassword ? 'Hide password' : 'Show password');

            const icon = button.querySelector('i');
            if (icon) {
                icon.className = showPassword ? 'fa-solid fa-eye-slash' : 'fa-regular fa-eye';
            }
        });
    });
});
