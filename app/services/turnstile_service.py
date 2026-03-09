from app.services.http_client import get_http_session
from flask import jsonify


def verify_turnstile(token, remote_ip, secret_key, logger, context=''):
    """共用 Turnstile 驗證函式。回傳 (success: bool, error_response or None)。"""
    if not secret_key:
        return True, None

    if not token:
        return False, (jsonify({'success': False, 'message': '請完成人機驗證'}), 400)

    try:
        session = get_http_session()
        verify_res = session.post(
            'https://challenges.cloudflare.com/turnstile/v0/siteverify',
            data={'secret': secret_key, 'response': token, 'remoteip': remote_ip},
            timeout=10,
        )
        verify_res.raise_for_status()
        verify_data = verify_res.json()
        if not verify_data.get('success'):
            logger.warning(f"Turnstile verification failed ({context}): {verify_data}")
            return False, (jsonify({'success': False, 'message': '人機驗證失敗，請重試'}), 403)
    except Exception as exc:
        logger.error(f"Turnstile verification error ({context}): {exc}", exc_info=True)
        return False, (jsonify({'success': False, 'message': '驗證服務暫時無法使用，請稍後再試'}), 500)

    return True, None
