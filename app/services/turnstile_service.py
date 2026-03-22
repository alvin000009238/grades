from flask import current_app

from app.services.http_client import get_http_session

VERIFY_URL = 'https://challenges.cloudflare.com/turnstile/v0/siteverify'


def verify_turnstile_token(token):
    """驗證 Cloudflare Turnstile token。

    若 TURNSTILE_SECRET_KEY 未設定，視為本機開發，直接放行。
    回傳 (success, error_message)。
    """
    secret_key = current_app.config.get('TURNSTILE_SECRET_KEY')

    if not secret_key:
        current_app.config['LOGGER'].warning(
            'TURNSTILE_SECRET_KEY not set – skipping Turnstile verification'
        )
        return True, None

    if not token:
        return False, '缺少人機驗證 token'

    try:
        session = get_http_session()
        resp = session.post(
            VERIFY_URL,
            data={'secret': secret_key, 'response': token},
        )
        result = resp.json()

        if result.get('success'):
            return True, None

        error_codes = result.get('error-codes', [])
        current_app.config['LOGGER'].warning(
            f'Turnstile verification failed: {error_codes}'
        )
        return False, '人機驗證失敗，請重試'
    except Exception as exc:
        current_app.config['LOGGER'].error(
            f'Turnstile verification error: {exc}', exc_info=True
        )
        return False, '驗證服務異常，請稍後再試'
