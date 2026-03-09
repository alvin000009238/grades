import json
import secrets
import string
from typing import Any, Dict, Optional

from app.services.share_store import ShareStore

SHARE_ID_CHARS = string.ascii_letters + string.digits + "-_.~"
SHARE_ID_LENGTH = 15


class ShareService:
    def __init__(self, store: ShareStore, ttl_seconds: int):
        self.store = store
        self.ttl_seconds = ttl_seconds

    def create_share(self, data: Dict[str, Any]) -> str:
        share_id = ''.join(secrets.choice(SHARE_ID_CHARS) for _ in range(SHARE_ID_LENGTH))
        payload = json.dumps(data, ensure_ascii=False)
        self.store.save_share(share_id, payload, self.ttl_seconds)
        return share_id

    def get_share(self, share_id: str) -> Optional[Dict[str, Any]]:
        payload = self.store.get_share(share_id)
        if payload is None:
            return None
        return json.loads(payload)


def is_valid_share_id(share_id: str) -> bool:
    return all(c in SHARE_ID_CHARS for c in share_id)
