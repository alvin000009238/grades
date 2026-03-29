from flask import Blueprint, current_app, jsonify, request, send_from_directory

from app.services.share_service import (
    generate_share_id,
    is_valid_share_id,
    read_shared_data,
    validate_share_payload,
    write_shared_data,
)
from app.services.turnstile_service import verify_turnstile_token
import logging

logger = logging.getLogger('SchoolGradesServer.Share')

bp = Blueprint('share', __name__)


@bp.route('/api/share', methods=['POST'])
def create_share_link():
    try:
        data = request.json
        if not data or not isinstance(data, dict):
            return jsonify({'error': 'No data provided or invalid format'}), 400

        # Turnstile 人機驗證
        ts_ok, ts_err = verify_turnstile_token(data.get('turnstile_token'))
        if not ts_ok:
            return jsonify({'error': ts_err}), 403

        # Payload 大小與結構校驗
        valid, err, cleaned = validate_share_payload(data)
        if not valid:
            return jsonify({'error': err}), 400

        share_id = generate_share_id()
        write_shared_data(current_app.config['REDIS_CLIENT'], share_id, cleaned, current_app.config['SHARE_TTL'])
        return jsonify({'success': True, 'id': share_id})
    except Exception as exc:
        logger.error(f'Error creating share: {exc}', exc_info = True)
        return jsonify({'error': str(exc)}), 500


@bp.route('/api/share/<share_id>', methods=['GET'])
def get_shared_grades(share_id):
    try:
        if not is_valid_share_id(share_id):
            return jsonify({'error': 'Invalid ID format'}), 400

        data = read_shared_data(current_app.config['REDIS_CLIENT'], share_id)
        if data is None:
            return jsonify({'error': 'Link expired or not found'}), 404

        return jsonify({'success': True, 'data': data})
    except Exception as exc:
        logger.error(f'Error reading share: {exc}', exc_info = True)
        return jsonify({'error': str(exc)}), 500


@bp.route('/share/<share_id>')
def view_shared_page(share_id):
    response = send_from_directory('public', 'index.html')
    response.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    response.headers['Pragma'] = 'no-cache'
    response.headers['Expires'] = '0'
    return response
