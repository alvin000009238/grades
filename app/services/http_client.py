import os
import requests
import logging
import certifi
import tempfile
from urllib3.util.retry import Retry
from requests.adapters import HTTPAdapter

logger = logging.getLogger(__name__)

class LoggingRetry(Retry):
    def increment(self, method=None, url=None, response=None, error=None, _pool=None, _stacktrace=None):
        new_retry = super().increment(
            method=method, url=url, response=response, error=error, _pool=_pool, _stacktrace=_stacktrace
        )
        
        attempt = len(self.history) + 1 if self.history else 1
        status = response.status if response else "N/A"
        
        # 不要紀錄整個 response body 避免洩漏個資，只記錄 endpoint 與錯誤
        logger.warning(
            f"HTTP Retry invoked (Attempt {attempt}). "
            f"Method: {method}, URL: {url}, "
            f"Status: {status}, Error: {error}"
        )
        return new_retry

class TimeoutSession(requests.Session):
    def __init__(self, timeout=None):
        super().__init__()
        self.default_timeout = timeout

    def request(self, method, url, **kwargs):
        # 如果呼叫方沒有指定 timeout，則填入預設 timeout
        if 'timeout' not in kwargs:
            kwargs['timeout'] = self.default_timeout
        return super().request(method, url, **kwargs)

def get_http_session() -> TimeoutSession:
    """
    Returns a requests.Session customized with default timeouts and automatic retries.
    Configurable via environment variables.
    """
    try:
        total_retries = int(os.environ.get("HTTP_RETRY_TOTAL", 3))
    except ValueError:
        total_retries = 3

    try:
        connect_timeout = float(os.environ.get("HTTP_TIMEOUT_CONNECT", 5.0))
    except ValueError:
        connect_timeout = 5.0

    try:
        read_timeout = float(os.environ.get("HTTP_TIMEOUT_READ", 20.0))
    except ValueError:
        read_timeout = 20.0

    try:
        backoff_factor = float(os.environ.get("HTTP_BACKOFF_FACTOR", 0.5))
    except ValueError:
        backoff_factor = 0.5

    session = TimeoutSession(timeout=(connect_timeout, read_timeout))
    
    cert_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "certs", "TWCA Secure SSL Certification Authority.crt")
    if os.path.exists(cert_path):
        combined_path = os.path.join(tempfile.gettempdir(), "twca_combined_ca_bundle.pem")
        try:
            # Overwrite if missing or too small
            if not os.path.exists(combined_path) or os.path.getsize(combined_path) < 100:
                with open(certifi.where(), "r", encoding="utf-8") as f_ca:
                    base_certs = f_ca.read()
                with open(cert_path, "r", encoding="utf-8") as f_custom:
                    custom_cert = f_custom.read()
                with open(combined_path, "w", encoding="utf-8") as f_out:
                    f_out.write(base_certs + "\n\n" + custom_cert + "\n")
            session.verify = combined_path
        except Exception as e:
            logger.error(f"Failed to create combined CA bundle: {e}")
            session.verify = certifi.where() # fallback
    else:
        session.verify = certifi.where()
    
    retry_strategy = LoggingRetry(
        total=total_retries,
        backoff_factor=backoff_factor,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["HEAD", "GET", "OPTIONS", "POST", "PUT", "DELETE", "PATCH"],
        respect_retry_after_header=True
    )
    
    adapter = HTTPAdapter(max_retries=retry_strategy)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    
    return session
