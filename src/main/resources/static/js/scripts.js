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
});
