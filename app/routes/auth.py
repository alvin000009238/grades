from flask import Blueprint, current_app, jsonify, request, session

from app.services.auth_service import is_logged_in, login_and_build_session_payload
from app.services.grades_service import get_structure
from app.services.rate_limiter import is_rate_limited
from app.services.turnstile_service import verify_turnstile_token

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
    data = request.json or {}
    username = data.get('username')
    password = data.get('password')
    logger = current_app.config['LOGGER']

    logger.info(f'Login attempt for user: {username}')

    # 速率限制檢查（在 Turnstile 之前）
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

    # Turnstile 人機驗證
    ts_ok, ts_err = verify_turnstile_token(data.get('turnstile_token'))
    if not ts_ok:
        return jsonify({'success': False, 'message': ts_err}), 403

    if not username or not password:
        return jsonify({'success': False, 'message': '請輸入帳號密碼'}), 400

    fetcher = current_app.config['GRADE_FETCHER']
    success, message, payload = login_and_build_session_payload(fetcher, username, password)
    if not success:
        return jsonify({'success': False, 'message': message}), 401

    session.update(payload)
    logger.info(f'Login successful for user: {username}')

    try:
        logger.info('Fetching structure via API...')
        structure = get_structure(
            fetcher,
            session['api_cookies'],
            session['student_no'],
            session['api_token'],
        )
        session['structure'] = structure
        logger.info(f'Structure cached: {len(structure)} semesters')
    except Exception as exc:
        logger.error(f'Failed to fetch structure: {exc}')

    return jsonify({'success': True, 'message': message})


@bp.route('/api/logout', methods=['POST'])
def logout():
    session.clear()
    return jsonify({'success': True, 'message': '已登出'})
