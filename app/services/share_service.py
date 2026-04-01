import json
import secrets
import string
from datetime import datetime, timezone

CHARS = string.ascii_letters + string.digits + '-_.~'
SHARE_ID_LENGTH = 15
SHARE_MAX_PAYLOAD_BYTES = 512_000  # 500 KB


def generate_share_id():
    return ''.join(secrets.choice(CHARS) for _ in range(SHARE_ID_LENGTH))


def is_valid_share_id(share_id):
    return len(share_id) == SHARE_ID_LENGTH and all(c in CHARS for c in share_id)


def validate_share_payload(data, max_bytes=SHARE_MAX_PAYLOAD_BYTES):
    """校驗分享 payload 的大小與結構。

    Args:
        data: 解析後的 JSON dict。
        max_bytes: 序列化後允許的最大位元組數。

    Returns:
        tuple: (is_valid, error_message, cleaned_data)
    """
    if not isinstance(data, dict):
        return False, 'Payload 必須為 JSON 物件', None

    # 剝離非必要欄位
    cleaned = {k: v for k, v in data.items() if k not in ('turnstile_token', 'share_expiry')}

    # 大小檢查
    serialized = json.dumps(cleaned, ensure_ascii=False)
    if len(serialized.encode('utf-8')) > max_bytes:
        return False, f'Payload 超過大小限制（{max_bytes // 1000}KB）', None

    # 結構校驗
    result = cleaned.get('Result')
    if not isinstance(result, dict):
        return False, '缺少 Result 資料', None

    if not isinstance(result.get('SubjectExamInfoList'), list):
        return False, '缺少 SubjectExamInfoList 成績清單', None

    return True, None, cleaned


def write_shared_data(redis_client, share_id, data, ttl=7200):
    cache_key = f"share:{share_id}"
    redis_client.setex(cache_key, ttl, json.dumps(data, ensure_ascii=False))


def write_share_metadata(redis_client, share_id, creator_student_no, ttl=7200):
    cache_key = f"share_meta:{share_id}"
    metadata = {
        'creator_student_no': creator_student_no,
        'created_at': datetime.now(timezone.utc).isoformat(),
        'ttl_seconds': ttl,
    }
    redis_client.setex(cache_key, ttl, json.dumps(metadata, ensure_ascii=False))


def read_shared_data(redis_client, share_id):
    cache_key = f"share:{share_id}"
    data_str = redis_client.get(cache_key)
    if not data_str:
        return None
    return json.loads(data_str)


def read_share_metadata(redis_client, share_id):
    cache_key = f"share_meta:{share_id}"
    data_str = redis_client.get(cache_key)
    if not data_str:
        return None
    return json.loads(data_str)


def refresh_share_metadata_ttl(redis_client, share_id, ttl=7200):
    cache_key = f"share_meta:{share_id}"
    return redis_client.expire(cache_key, ttl) == 1
