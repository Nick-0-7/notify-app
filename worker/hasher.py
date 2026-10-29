"""URL canonicalization and content normalization/hashing utilities."""
import hashlib
import re
from urllib.parse import urlparse, urlunparse, parse_qsl, urlencode

TRACKING_QUERY_PARAMS = {
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_term",
    "utm_content",
    "fbclid",
    "gclid",
    "_ga",
    "ref",
}


def canonicalize_url(url: str) -> str:
    """
    Produce a canonical, deterministic representation of a URL to prevent duplicate crawling.
    - Lowercase scheme & host
    - Standardize port (remove 80 for http, 443 for https)
    - Remove tracking query parameters
    - Sort query parameters alphabetically
    - Strip trailing slash from path
    - Discard fragments
    """
    if not url:
        return ""

    parsed = urlparse(url.strip())
    scheme = parsed.scheme.lower()
    netloc = parsed.netloc.lower()

    # Clean port
    if scheme == "http" and netloc.endswith(":80"):
        netloc = netloc[:-3]
    elif scheme == "https" and netloc.endswith(":443"):
        netloc = netloc[:-4]

    # Normalize path
    path = parsed.path or "/"
    if len(path) > 1 and path.endswith("/"):
        path = path.rstrip("/")

    # Filter & sort query parameters
    query_items = parse_qsl(parsed.query, keep_blank_values=False)
    filtered_items = sorted([
        (k, v) for k, v in query_items
        if k.lower() not in TRACKING_QUERY_PARAMS
    ])
    canonical_query = urlencode(filtered_items)

    return urlunparse((scheme, netloc, path, "", canonical_query, ""))


def normalize_text(text: str) -> str:
    """
    Normalize text for robust deduplication:
    - Lowercase
    - Replace all runs of whitespace with a single space
    - Strip punctuation variation
    """
    if not text:
        return ""

    lowered = text.lower()
    # Replace non-alphanumeric whitespace with space
    cleaned = re.sub(r"[\r\n\t]+", " ", lowered)
    cleaned = re.sub(r"\s{2,}", " ", cleaned)
    return cleaned.strip()


def calculate_content_hash(normalized_text: str) -> str:
    """Generate SHA-256 hex digest for normalized content."""
    return hashlib.sha256(normalized_text.encode("utf-8")).hexdigest()
