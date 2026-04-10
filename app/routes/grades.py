from flask import Blueprint, current_app, jsonify, request, session

from app.services.grades_service import fetch_grades, get_structure
import logging

logger = logging.getLogger('SchoolGradesServer.Grades')

bp = Blueprint('grades', __name__)


@bp.route('/api/fetch', methods=['POST'])
def fetch_grades_route():
    payload = request.json
    if not isinstance(payload, (dict, type(None))):
        return jsonify({'success': False, 'error': 'Invalid payload format: expected a JSON object.'}), 400
    payload = payload or {}
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
        logger.error(f'Error fetching grades (API): {exc}', exc_info=True)
        return jsonify({'success': False, 'error': '伺服器內部錯誤'}), 500


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
        logger.error(f'Error getting structure (API): {exc}', exc_info=True)
        return jsonify({'error': '伺服器內部錯誤'}), 500



