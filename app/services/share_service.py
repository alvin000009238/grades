import json
import secrets
import string

CHARS = string.ascii_letters + string.digits + '-_.~'


def generate_share_id(length=15):
    return ''.join(secrets.choice(CHARS) for _ in range(length))


def is_valid_share_id(share_id):
    return all(c in CHARS for c in share_id)


def write_shared_data(redis_client, share_id, data, ttl=7200):
    cache_key = f"share:{share_id}"
    redis_client.setex(cache_key, ttl, json.dumps(data, ensure_ascii=False))


def read_shared_data(redis_client, share_id):
    cache_key = f"share:{share_id}"
    data_str = redis_client.get(cache_key)
    if not data_str:
        return None
    return json.loads(data_str)

