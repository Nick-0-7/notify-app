import pytest
from worker.security import validate_safe_url, is_ip_allowed, SSRFSecurityException


def test_public_ip_allowed():
    assert is_ip_allowed("8.8.8.8") is True
    assert is_ip_allowed("1.1.1.1") is True


def test_private_and_loopback_ips_blocked():
    assert is_ip_allowed("127.0.0.1") is False
    assert is_ip_allowed("10.0.0.1") is False
    assert is_ip_allowed("192.168.1.1") is False
    assert is_ip_allowed("172.16.0.1") is False
    assert is_ip_allowed("169.254.169.254") is False  # Cloud metadata IP
    assert is_ip_allowed("::1") is False


def test_blocked_schemes():
    with pytest.raises(SSRFSecurityException):
        validate_safe_url("file:///etc/passwd")

    with pytest.raises(SSRFSecurityException):
        validate_safe_url("ftp://example.com/notice.pdf")

    with pytest.raises(SSRFSecurityException):
        validate_safe_url("gopher://127.0.0.1:70")


def test_blocked_metadata_hostnames():
    with pytest.raises(SSRFSecurityException):
        validate_safe_url("http://metadata.google.internal/computeMetadata/v1/")

    with pytest.raises(SSRFSecurityException):
        validate_safe_url("http://169.254.169.254/latest/meta-data/")

    with pytest.raises(SSRFSecurityException):
        validate_safe_url("http://localhost:8080/admin")
