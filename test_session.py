import time
from app.services.http_client import get_http_session

start = time.time()
for _ in range(10):
    s = get_http_session()
print(f"Time to create 10 sessions: {time.time() - start:.4f}s")
