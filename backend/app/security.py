import hashlib
import hmac
import secrets
import base64
import time


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 240_000)
    return f"pbkdf2_sha256$240000${salt.hex()}${digest.hex()}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, rounds, salt_hex, digest_hex = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt_hex), int(rounds))
        return hmac.compare_digest(digest.hex(), digest_hex)
    except (ValueError, TypeError):
        return False


def new_session_token() -> tuple[str, str]:
    raw = secrets.token_urlsafe(48)
    return raw, hashlib.sha256(raw.encode()).hexdigest()


def new_ai_access_token(user_id: int, secret: str, ttl_seconds: int = 86400) -> str:
    expires = int(time.time()) + ttl_seconds
    nonce = secrets.token_urlsafe(18)
    payload = f"{user_id}.{expires}.{nonce}"
    signature = hmac.new(secret.encode(), payload.encode(), hashlib.sha256).hexdigest()
    raw = f"{payload}.{signature}".encode()
    return base64.urlsafe_b64encode(raw).decode()


def verify_ai_access_token(token: str, user_id: int, secret: str) -> bool:
    try:
        raw = base64.urlsafe_b64decode(token.encode()).decode()
        token_user, expires, nonce, signature = raw.split(".", 3)
        payload = f"{token_user}.{expires}.{nonce}"
        expected = hmac.new(secret.encode(), payload.encode(), hashlib.sha256).hexdigest()
        return token_user == str(user_id) and int(expires) > int(time.time()) and hmac.compare_digest(signature, expected)
    except (ValueError, TypeError, UnicodeError):
        return False
