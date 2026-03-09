import json

from flask import Blueprint, current_app, jsonify, request, session

from app.services.grades_service import fetch_grades, get_structure

bp = Blueprint('grades', __name__)


@bp.route('/api/fetch', methods=['POST'])
def fetch_grades_route():
    payload = request.json or {}
    year_value = payload.get('year_value')
    exam_value = payload.get('exam_value')

    cookies = session.get('api_cookies')
    token = session.get('api_token')
    student_no = session.get('student_no')

    if not cookies or not token:
        return jsonify({'error': '未登入'}), 401

    try:
        data = fetch_grades(
            current_app.config['GRADE_FETCHER'],
            cookies,
            student_no,
            token,
            year_value,
            exam_value,
        )
        return jsonify({'success': True, 'message': '成績已更新', 'data': data})
    except Exception as exc:
        current_app.config['LOGGER'].error(f'Error fetching grades (API): {exc}', exc_info=True)
        return jsonify({'success': False, 'error': str(exc)}), 500


@bp.route('/api/structure', methods=['GET'])
def get_structure_route():
    if not session.get('api_token'):
        return jsonify({'error': '未登入'}), 401

    force_reload = request.args.get('reload') == 'true'
    cached = session.get('structure')

    if cached and not force_reload:
        return jsonify({'structure': cached})

    try:
        if not session.get('api_cookies') or not session.get('student_no'):
            return jsonify({'error': '連線過期，請重新登入'}), 401

        structure = get_structure(
            current_app.config['GRADE_FETCHER'],
            session['api_cookies'],
            session['student_no'],
            session['api_token'],
        )
        session['structure'] = structure
        return jsonify({'structure': structure})
    except Exception as exc:
        return jsonify({'error': str(exc)}), 500


@bp.route('/api/upload', methods=['POST'])
def upload_grades():
    logger = current_app.config['LOGGER']
    logger.info('Uploading grades file')

    try:
        if 'file' in request.files:
            file = request.files['file']
            content = file.read().decode('utf-8')
            data = json.loads(content)
        elif request.is_json:
            data = request.get_json()
        else:
            return jsonify({'error': '請提供 JSON 檔案或資料'}), 400

        if 'Result' not in data:
            return jsonify({'error': '無效的成績資料格式'}), 400

        return jsonify({'success': True, 'data': data})
    except Exception as exc:
        logger.error(f'Error during upload: {exc}', exc_info=True)
        return jsonify({'error': str(exc)}), 500
