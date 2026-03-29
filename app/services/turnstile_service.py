from flask import current_app

from app.services.http_client import get_http_session

import logging
import os

logger = logging.getLogger('SchoolGradesServer.TurnstileService')

VERIFY_URL = 'https://challenges.cloudflare.com/turnstile/v0/siteverify'


def verify_turnstile_token(token, remoteip=None):
    """驗證 Cloudflare Turnstile token。

    若 TURNSTILE_SECRET_KEY 未設定，僅在開發/測試環境放行，生產環境則阻擋。
    回傳 (success, error_message)。
    """
    secret_key = current_app.config.get('TURNSTILE_SECRET_KEY')

    if not secret_key:
        flask_env = os.environ.get('FLASK_ENV', '').lower()
        app_env = os.environ.get('APP_ENV', '').lower()
        if flask_env in ('development', 'testing') or app_env in ('development', 'testing'):
            logger.warning(
                'TURNSTILE_SECRET_KEY not set in dev/test env – skipping Turnstile verification'
            )
            return True, None
        else:
            logger.error(
                'TURNSTILE_SECRET_KEY not set in production env – blocking request'
            )
            return False, '系統設定錯誤：缺少人機驗證設定'

    if not token:
        return False, '缺少人機驗證 token'

    try:
        session = get_http_session()
        data = {'secret': secret_key, 'response': token}
        if remoteip:
            data['remoteip'] = remoteip

        resp = session.post(
            VERIFY_URL,
            data=data,
        )
        result = resp.json()

        if result.get('success'):
            return True, None

        error_codes = result.get('error-codes', [])
        logger.warning(
            f'Turnstile verification failed: {error_codes}'
        )
        return False, '人機驗證失敗，請重試'
    except Exception as exc:
        logger.error(
            f'Turnstile verification error: {exc}', exc_info=True
        )
        return False, '驗證服務異常，請稍後再試'
