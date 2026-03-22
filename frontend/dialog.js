export function showAlert(title, message) {
    return new Promise((resolve) => {
        const modal = document.getElementById('alertDialog');
        const titleEl = document.getElementById('alertTitle');
        const messageEl = document.getElementById('alertMessage');
        const btn = document.getElementById('confirmAlertDialog');
        const closeBtn = document.getElementById('closeAlertDialog');

        if (!modal || !titleEl || !messageEl || !btn || !closeBtn) {
            alert(message);
            resolve();
            return;
        }

        titleEl.textContent = title;
        messageEl.textContent = message;

        const cleanup = () => {
            modal.classList.remove('active');
            btn.removeEventListener('click', onConfirm);
            closeBtn.removeEventListener('click', onConfirm);
            resolve();
        };

        const onConfirm = () => cleanup();

        btn.addEventListener('click', onConfirm);
        closeBtn.addEventListener('click', onConfirm);

        modal.classList.add('active');
    });
}

export function showConfirm(title, message) {
    return new Promise((resolve) => {
        const modal = document.getElementById('confirmDialog');
        const titleEl = document.getElementById('confirmTitle');
        const messageEl = document.getElementById('confirmMessage');
        const confirmBtn = document.getElementById('acceptConfirmDialog');
        const cancelBtn = document.getElementById('cancelConfirmDialog');
        const closeBtn = document.getElementById('closeConfirmDialog');

        if (!modal || !titleEl || !messageEl || !confirmBtn || !cancelBtn || !closeBtn) {
            resolve(confirm(message));
            return;
        }

        titleEl.textContent = title;
        messageEl.textContent = message;

        const cleanup = (result) => {
            modal.classList.remove('active');
            confirmBtn.removeEventListener('click', onConfirm);
            cancelBtn.removeEventListener('click', onCancel);
            closeBtn.removeEventListener('click', onCancel);
            resolve(result);
        };

        const onConfirm = () => cleanup(true);
        const onCancel = () => cleanup(false);

        confirmBtn.addEventListener('click', onConfirm);
        cancelBtn.addEventListener('click', onCancel);
        closeBtn.addEventListener('click', onCancel);

        modal.classList.add('active');
    });
}
