from typing import Optional

from redis import Redis

from app.services.share_store import ShareStore


class RedisShareStore(ShareStore):
    def __init__(self, redis_client: Redis):
        self.redis_client = redis_client

    @staticmethod
    def _key(share_id: str) -> str:
        return f"share:{share_id}"

    def save_share(self, share_id: str, payload: str, ttl: int) -> None:
        self.redis_client.set(self._key(share_id), payload, ex=ttl)

    def get_share(self, share_id: str) -> Optional[str]:
        value = self.redis_client.get(self._key(share_id))
        if value is None:
            return None
        if isinstance(value, bytes):
            return value.decode("utf-8")
        return value
