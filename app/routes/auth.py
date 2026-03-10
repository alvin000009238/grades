from flask import Blueprint, current_app, jsonify, request, session

from app.services.auth_service import is_logged_in, login_and_build_session_payload
from app.services.grades_service import get_structure
from app.services.turnstile_service import verify_turnstile_token

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
