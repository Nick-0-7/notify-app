"""Security module: URL validation, SSRF defense, and redirect verification."""
import ipaddress
import socket
from urllib.parse import urlparse


BLOCKED_HOSTS = {
    "localhost",
    "metadata.google.internal",
    "instance-data",
    "169.254.169.254",
    "169.254.169.253",
}


class SSRFSecurityException(Exception):
    """Raised when a URL targets a private, loopback, or metadata address."""
    pass


def is_ip_allowed(ip_str: str) -> bool:
    """Validate that an IP is a public, globally-routable address."""
    try:
        ip = ipaddress.ip_address(ip_str)
    except ValueError:
        return False

    if ip.is_loopback:
        return False
    if ip.is_private:
        return False
    if ip.is_link_local:
        return False
    if ip.is_multicast:
        return False
    if ip.is_unspecified:
        return False

    # Check IPv4-mapped in IPv6 (::ffff:0:0/96)
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
        return is_ip_allowed(str(ip.ipv4_mapped))

    # Check RFC 6052 / RFC 8215 Well-Known Prefix (64:ff9b::/96) used in DNS64/NAT64 networks
    if isinstance(ip, ipaddress.IPv6Address):
        nat64_prefix = ipaddress.ip_network("64:ff9b::/96")
        if ip in nat64_prefix:
            embedded_ipv4 = ipaddress.IPv4Address(ip.packed[-4:])
            return is_ip_allowed(str(embedded_ipv4))

    if ip.is_reserved:
        return False

    # Explicit check for 169.254.0.0/16
    if isinstance(ip, ipaddress.IPv4Address):
        link_local_net = ipaddress.ip_network("169.254.0.0/16")
        if ip in link_local_net:
            return False

    return True


def validate_safe_url(url: str) -> str:
    """
    Validate that the given URL is safe for worker HTTP fetching:
    1. Scheme must be HTTP or HTTPS
    2. Hostname cannot be a known cloud metadata or localhost alias
    3. Resolves DNS and checks that all resolved IPs are strictly public
    """
    if not url or not isinstance(url, str):
        raise SSRFSecurityException("URL must be a non-empty string.")

    parsed = urlparse(url.strip())
    if parsed.scheme.lower() not in ("http", "https"):
        raise SSRFSecurityException(f"Forbidden scheme '{parsed.scheme}'. Only http and https are allowed.")

    hostname = parsed.hostname
    if not hostname:
        raise SSRFSecurityException("URL must have a valid hostname.")

    hostname_lower = hostname.lower()

    if hostname_lower in BLOCKED_HOSTS:
        raise SSRFSecurityException(f"Hostname '{hostname}' is blocked (metadata/loopback).")

    # If hostname is a raw IP literal
    try:
        ip = ipaddress.ip_address(hostname_lower)
        if not is_ip_allowed(str(ip)):
            raise SSRFSecurityException(f"Target IP '{hostname}' belongs to private/restricted range.")
        return url.strip()
    except ValueError:
        # Not a literal IP, proceed to DNS resolution
        pass

    try:
        addr_info = socket.getaddrinfo(hostname, None, socket.AF_UNSPEC, socket.SOCK_STREAM)
    except socket.gaierror as e:
        raise SSRFSecurityException(f"DNS resolution failed for '{hostname}': {e}")

    resolved_ips = {item[4][0] for item in addr_info}
    if not resolved_ips:
        raise SSRFSecurityException(f"No IP addresses resolved for '{hostname}'.")

    for resolved_ip in resolved_ips:
        if not is_ip_allowed(resolved_ip):
            raise SSRFSecurityException(
                f"Hostname '{hostname}' resolved to prohibited IP '{resolved_ip}'."
            )

    return url.strip()


def validate_redirect(target_url: str) -> str:
    """Revalidate destination on HTTP redirect to prevent DNS rebinding or redirect-based SSRF."""
    return validate_safe_url(target_url)
