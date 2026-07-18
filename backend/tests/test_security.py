from app.security import (
    hash_password,
    new_ai_access_token,
    new_session_token,
    verify_ai_access_token,
    verify_password,
)


def test_password_hash_is_salted_and_verifiable():
    first = hash_password("correct horse battery staple")
    second = hash_password("correct horse battery staple")
    assert first != second
    assert verify_password("correct horse battery staple", first)
    assert not verify_password("wrong password", first)


def test_session_token_is_random_and_not_raw_hash():
    raw, digest = new_session_token()
    assert raw
    assert len(digest) == 64
    assert raw not in digest


def test_ai_access_token_is_bound_to_user():
    token = new_ai_access_token(100001, "test-secret", ttl_seconds=60)
    assert verify_ai_access_token(token, 100001, "test-secret")
    assert not verify_ai_access_token(token, 100002, "test-secret")
    assert not verify_ai_access_token(token, 100001, "wrong-secret")
