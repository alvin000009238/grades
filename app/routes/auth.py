from flask import Blueprint, current_app, jsonify, request, session

from app.services.auth_service import is_logged_in, login_and_build_session_payload
from app.services.rate_limiter import is_rate_limited
from app.services.turnstile_service import verify_turnstile_token
import logging

logger = logging.getLogger('SchoolGradesServer.Auth')

LOGIN_RATE_LIMIT_MAX = 5       # 每視窗最大嘗試次數
LOGIN_RATE_LIMIT_WINDOW = 60   # 視窗長度（秒）

bp = Blueprint('auth', __name__)


@bp.route('/api/check_login')
def check_login():
    if is_logged_in(session):
        return jsonify({'logged_in': True})
    return jsonify({'logged_in': False}), 401


@bp.route('/api/login', methods=['POST'])
def login():
    data = request.json
    if not isinstance(data, (dict, type(None))):
        return jsonify({'success': False, 'message': 'Invalid payload format: expected a JSON object.'}), 400
    data = data or {}
    username = data.get('username')
    password = data.get('password')
    captcha_code = (data.get('captcha_code') or '').strip()

    masked_username = (username[:3] + "***") if username and len(username) > 3 else "***"
    logger.info(f'Login attempt for user: {masked_username}')

    # Turnstile 人機驗證
    ts_ok, ts_err = verify_turnstile_token(
        data.get('turnstile_token'),
        remoteip=request.remote_addr
    )
    if not ts_ok:
        return jsonify({'success': False, 'message': ts_err}), 403

    # 速率限制檢查（在 Turnstile 之後）
    redis_client = current_app.config.get('REDIS_CLIENT')
    if redis_client:
        try:
            limited, remaining, retry_after = is_rate_limited(
                redis_client, request.remote_addr,
                max_attempts=LOGIN_RATE_LIMIT_MAX,
                window_seconds=LOGIN_RATE_LIMIT_WINDOW,
            )
            if limited:
                logger.warning(f'Rate limited login from IP: {request.remote_addr}')
                resp = jsonify({
                    'success': False,
                    'message': f'登入嘗試過於頻繁，請在 {retry_after} 秒後再試',
                })
                resp.headers['Retry-After'] = str(retry_after)
                return resp, 429
        except Exception as exc:
            logger.error(f'Rate limiter error: {exc}', exc_info=True)

    if not username or not password:
        return jsonify({'success': False, 'message': '請輸入帳號密碼'}), 400

    if not captcha_code:
        return jsonify({'success': False, 'message': '請輸入驗證碼'}), 400

    fetcher = current_app.config['GRADE_FETCHER']
    school_login_context = session.get('school_login_context')
    success, message, payload = login_and_build_session_payload(
        fetcher,
        username,
        password,
        captcha_code=captcha_code,
        login_context=school_login_context,
    )

    # 驗證碼一次性使用，避免重放
    session.pop('school_login_context', None)

    if not success:
        need_refresh_captcha = '驗證碼' in (message or '')
        return jsonify({'success': False, 'message': message, 'need_refresh_captcha': need_refresh_captcha}), 401

    session.update(payload)
    logger.info('Login successful')

    return jsonify({'success': True, 'message': message})


@bp.route('/api/school-captcha')
def school_captcha():
    fetcher = current_app.config['GRADE_FETCHER']
    success, message, payload = fetcher.prepare_login_captcha()
    if not success:
        return jsonify({'success': False, 'message': message}), 502

    session['school_login_context'] = payload['context']
    return jsonify({'success': True, 'image_data_url': payload['image_data_url']})


@bp.route('/api/logout', methods=['POST'])
def logout():
    session.clear()
    return jsonify({'success': True, 'message': '已登出'})
