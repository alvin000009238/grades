from flask import Blueprint, current_app, jsonify, request, send_from_directory, session

from app.services.rate_limiter import is_rate_limited
from app.services.share_service import (
    generate_share_id,
    is_valid_share_id,
    refresh_share_metadata_ttl,
    read_share_metadata,
    read_shared_data,
    validate_share_payload,
    write_share_metadata,
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

        student_no = session.get('student_no')
        if not student_no:
            return jsonify({'error': 'Unauthorized'}), 401

        redis_client = current_app.config.get('REDIS_CLIENT')
        if redis_client is None:
            return jsonify({'error': 'Share service unavailable'}), 503

        # Turnstile 人機驗證
        ts_ok, ts_err = verify_turnstile_token(
            data.get('turnstile_token'),
            remoteip=request.remote_addr
        )
        if not ts_ok:
            return jsonify({'error': ts_err}), 403

        # 速率限制檢查（在 Turnstile 之後）
        if redis_client:
            try:
                # 較寬鬆的速率限制：每小時 (3600 秒) 10 次
                limited, remaining, retry_after = is_rate_limited(
                    redis_client, request.remote_addr,
                    max_attempts=10,
                    window_seconds=3600,
                    key_prefix="share"
                )
                if limited:
                    logger.warning(f'Rate limited share creation from IP: {request.remote_addr}')
                    resp = jsonify({
                        'error': f'建立分享連結過於頻繁，請在 {retry_after} 秒後再試',
                    })
                    resp.headers['Retry-After'] = str(retry_after)
                    return resp, 429
            except Exception as exc:
                logger.error(f'Rate limiter error: {exc}', exc_info=True)

        # Payload 大小與結構校驗
        valid, err, cleaned = validate_share_payload(data)
        if not valid:
            return jsonify({'error': err}), 400

        share_id = generate_share_id()
        share_ttl = current_app.config['SHARE_TTL']
        write_shared_data(redis_client, share_id, cleaned, share_ttl)
        write_share_metadata(redis_client, share_id, student_no, share_ttl)
        return jsonify({'success': True, 'id': share_id})
    except Exception as exc:
        logger.error(f'Error creating share: {exc}', exc_info = True)
        return jsonify({'error': str(exc)}), 500


@bp.route('/api/share/<share_id>', methods=['PUT'])
def update_share_link(share_id):
    try:
        if not is_valid_share_id(share_id):
            return jsonify({'error': 'Invalid ID format'}), 400

        data = request.json
        if not data or not isinstance(data, dict):
            return jsonify({'error': 'No data provided or invalid format'}), 400

        redis_client = current_app.config.get('REDIS_CLIENT')
        if redis_client is None:
            return jsonify({'error': 'Share service unavailable'}), 503

        requester_student_no = session.get('student_no')
        if requester_student_no is None:
            return jsonify({'error': 'Unauthorized'}), 401

        rate_limit_subject = requester_student_no or request.remote_addr
        if redis_client:
            try:
                limited, remaining, retry_after = is_rate_limited(
                    redis_client,
                    rate_limit_subject,
                    max_attempts=10,
                    window_seconds=60,
                    key_prefix='share_update'
                )
                if limited:
                    resp = jsonify({
                        'error': f'更新分享過於頻繁，請在 {retry_after} 秒後再試',
                    })
                    resp.headers['Retry-After'] = str(retry_after)
                    return resp, 429
            except Exception as exc:
                logger.error(f'Share update rate limiter error: {exc}', exc_info=True)

        metadata = read_share_metadata(redis_client, share_id)
        if metadata is None:
            return jsonify({'error': 'Link expired or not found'}), 404

        creator_student_no = metadata.get('creator_student_no')
        if not creator_student_no:
            return jsonify({'error': 'Forbidden'}), 403

        if requester_student_no != creator_student_no:
            return jsonify({'error': 'Forbidden'}), 403

        valid, err, cleaned = validate_share_payload(data)
        if not valid:
            return jsonify({'error': err}), 400

        share_ttl = current_app.config['SHARE_TTL']
        metadata_ttl_refreshed = refresh_share_metadata_ttl(redis_client, share_id, share_ttl)
        if not metadata_ttl_refreshed:
            return jsonify({'error': 'Share metadata state conflict'}), 409
        write_shared_data(redis_client, share_id, cleaned, share_ttl)
        return jsonify({'success': True, 'id': share_id})
    except Exception as exc:
        logger.error(f'Error updating share: {exc}', exc_info=True)
        return jsonify({'error': str(exc)}), 500


@bp.route('/api/share/<share_id>', methods=['GET'])
def get_shared_grades(share_id):
    try:
        if not is_valid_share_id(share_id):
            return jsonify({'error': 'Invalid ID format'}), 400

        redis_client = current_app.config.get('REDIS_CLIENT')
        if redis_client is None:
            return jsonify({'error': 'Share service unavailable'}), 503

        data = read_shared_data(redis_client, share_id)
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
