"""速率限制服務 — 基於 Redis 固定視窗計數器。"""


def is_rate_limited(redis_client, ip, max_attempts=5, window_seconds=60, key_prefix="login"):
    """檢查指定 IP 是否超過速率限制。

    使用 Redis INCR + EXPIRE 的固定視窗計數器策略。

    Args:
        redis_client: Redis 連線實例。
        ip: 客戶端 IP 位址。
        max_attempts: 視窗內最大嘗試次數。
        window_seconds: 視窗長度（秒）。
        key_prefix: Redis key 的前綴

    Returns:
        tuple: (is_limited, remaining, retry_after)
            - is_limited (bool): 是否已被限速。
            - remaining (int): 剩餘可用次數。
            - retry_after (int): 若被限速，需等待的秒數。
    """
    key = f"rate_limit:{key_prefix}:{ip}"

    current = redis_client.incr(key)

    if current == 1:
        redis_client.expire(key, window_seconds)

    if current > max_attempts:
        ttl = redis_client.ttl(key)
        retry_after = ttl if ttl > 0 else window_seconds
        return True, 0, retry_after

    remaining = max_attempts - current
    return False, remaining, 0
