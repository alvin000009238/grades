export const ONBOARDING_EVENTS = {
    LOGIN_MODAL_OPEN: 'onboarding:login-modal-open',
    DEMO_CREDENTIALS_FILLED: 'onboarding:demo-credentials-filled',
    LOGIN_SUCCESS: 'onboarding:login-success',
    SELECT_MODAL_OPEN: 'onboarding:select-modal-open',
    FETCH_SUCCESS: 'onboarding:fetch-success',
    SHARE_MODAL_OPEN: 'onboarding:share-modal-open',
    SHARE_LINK_CREATED: 'onboarding:share-link-created'
};

export function emitOnboardingEvent(type, detail = {}) {
    window.dispatchEvent(new CustomEvent(type, { detail }));
}
