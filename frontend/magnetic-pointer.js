const DEFAULT_SELECTOR = 'button, a, .import-dropdown-btn, .modal-btn, .tooltip-icon';

export function setupMagneticPointer(options = {}) {
    if (typeof window === 'undefined' || typeof document === 'undefined') return;

    const media = window.matchMedia?.('(hover: hover) and (pointer: fine)');
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)');

    if ((media && !media.matches) || (reduceMotion && reduceMotion.matches)) {
        return;
    }

    const selector = options.selector || DEFAULT_SELECTOR;
    const targets = [...document.querySelectorAll(selector)].filter((element) => !element.closest('.pointer-overlay'));
    if (!targets.length) return;

    const pointer = document.createElement('div');
    pointer.className = 'pointer-overlay';
    pointer.innerHTML = '<div></div><div></div><div></div><div></div>';
    document.body.appendChild(pointer);

    let currentTarget = null;

    const move = (event) => {
        let x = event.clientX;
        let y = event.clientY;

        if (currentTarget) {
            const rect = currentTarget.getBoundingClientRect();
            const centerX = rect.left + rect.width / 2;
            const centerY = rect.top + rect.height / 2;
            x = centerX + (x - centerX) * 0.12;
            y = centerY + (y - centerY) * 0.12;
        }

        pointer.style.transform = `translate(${x}px, ${y}px)`;
    };

    const resetPointerSize = () => {
        pointer.style.setProperty('--pointer-width', '2.5rem');
        pointer.style.setProperty('--pointer-height', '2.5rem');
    };

    const updatePointerSize = (target) => {
        const rect = target.getBoundingClientRect();
        pointer.style.setProperty('--pointer-width', `${rect.width + 10}px`);
        pointer.style.setProperty('--pointer-height', `${rect.height + 10}px`);
    };

    targets.forEach((target) => {
        target.classList.add('magnetic-target');
        target.addEventListener('mouseenter', () => {
            currentTarget = target;
            updatePointerSize(target);
        });
        target.addEventListener('mouseleave', () => {
            currentTarget = null;
            resetPointerSize();
        });
    });

    resetPointerSize();
    window.addEventListener('mousemove', move, { passive: true });
}
