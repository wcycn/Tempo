#!/usr/bin/env python3
"""Read-only deployment smoke check."""
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

def get_json(url: str) -> tuple[int, object]:
    request = Request(url, headers={"Accept": "application/json"})
    with urlopen(request, timeout=8) as response:
        return response.status, json.loads(response.read().decode("utf-8"))

def main() -> int:
    base = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8765").rstrip("/")
    for name, url in (("health", f"{base}/api/health"), ("openapi", f"{base}/openapi.json")):
        try:
            status, payload = get_json(url)
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            print(f"FAIL {name}: {exc}")
            return 1
        if status != 200 or (name == "health" and not isinstance(payload, dict)):
            print(f"FAIL {name}: HTTP {status}")
            return 1
        print(f"OK   {name}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
