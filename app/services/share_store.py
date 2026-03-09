from abc import ABC, abstractmethod
from typing import Optional


class ShareStore(ABC):
    @abstractmethod
    def save_share(self, share_id: str, payload: str, ttl: int) -> None:
        """Persist a serialized share payload with TTL in seconds."""

    @abstractmethod
    def get_share(self, share_id: str) -> Optional[str]:
        """Get serialized share payload, or None if missing/expired."""
