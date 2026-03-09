import logging
import os
from typing import Optional, Tuple

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logger = logging.getLogger("http_client")

HTTP_RETRY_TOTAL = int(os.environ.get("HTTP_RETRY_TOTAL", "3"))
HTTP_RETRY_BACKOFF_FACTOR = float(os.environ.get("HTTP_RETRY_BACKOFF_FACTOR", "0.5"))
HTTP_TIMEOUT_CONNECT = float(os.environ.get("HTTP_TIMEOUT_CONNECT", "5"))
HTTP_TIMEOUT_READ = float(os.environ.get("HTTP_TIMEOUT_READ", "20"))
DEFAULT_TIMEOUT: Tuple[float, float] = (HTTP_TIMEOUT_CONNECT, HTTP_TIMEOUT_READ)
STATUS_FORCELIST = (429, 500, 502, 503, 504)
ALLOWED_METHODS = frozenset(["GET", "POST"])


class LoggingRetry(Retry):
    """Retry class with observability logging while preserving urllib3 behavior."""

    def increment(self, method=None, url=None, response=None, error=None, *_args, **kwargs):
        next_retry = super().increment(method=method, url=url, response=response, error=error, *_args, **kwargs)

        status_code = response.status if response is not None else None
        error_type = type(error).__name__ if error is not None else None
        attempt = len(next_retry.history) + 1
        logger.warning(
            "HTTP retry scheduled method=%s endpoint=%s attempt=%s status=%s error_type=%s",
            method,
            url,
            attempt,
            status_code,
            error_type,
        )
        return next_retry


class HttpClient:
    def __init__(self):
        self.session = _build_session()

    def request(self, method: str, url: str, **kwargs):
        timeout = kwargs.pop("timeout", DEFAULT_TIMEOUT)
        response = self.session.request(method=method, url=url, timeout=timeout, **kwargs)
        retries = getattr(getattr(response, "raw", None), "retries", None)
        retry_count = len(getattr(retries, "history", [])) if retries else 0
        if retry_count:
            logger.info(
                "HTTP request completed after retries method=%s endpoint=%s retries=%s status=%s",
                method,
                url,
                retry_count,
                response.status_code,
            )
        return response

    def get(self, url: str, **kwargs):
        return self.request("GET", url, **kwargs)

    def post(self, url: str, **kwargs):
        return self.request("POST", url, **kwargs)


def _build_session() -> requests.Session:
    session = requests.Session()
    retry = LoggingRetry(
        total=HTTP_RETRY_TOTAL,
        backoff_factor=HTTP_RETRY_BACKOFF_FACTOR,
        status_forcelist=STATUS_FORCELIST,
        allowed_methods=ALLOWED_METHODS,
        respect_retry_after_header=True,
    )
    adapter = HTTPAdapter(max_retries=retry)
    session.mount("https://", adapter)
    session.mount("http://", adapter)
    return session


def create_http_client() -> HttpClient:
    return HttpClient()


_shared_http_client: Optional[HttpClient] = None


def get_http_client() -> HttpClient:
    global _shared_http_client
    if _shared_http_client is None:
        _shared_http_client = HttpClient()
    return _shared_http_client
