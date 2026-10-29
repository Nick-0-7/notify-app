"""Safe HTTP client with SSRF verification, redirect tracking, and resource limits."""
import httpx
from typing import Tuple, Optional
from worker.config import settings
from worker.security import validate_safe_url, validate_redirect, SSRFSecurityException


class SafeFetchResult:
    def __init__(self, final_url: str, content: bytes, content_type: str, status_code: int):
        self.final_url = final_url
        self.content = content
        self.content_type = content_type
        self.status_code = status_code

    @property
    def text(self) -> str:
        return self.content.decode("utf-8", errors="replace")


async def safe_fetch_url(url: str) -> SafeFetchResult:
    """
    Fetch URL safely:
    - Re-validates every redirect destination against SSRF
    - Limits response size to prevent memory exhaustion
    - Applies strict timeouts and user-agent
    """
    current_url = validate_safe_url(url)
    redirect_count = 0

    headers = {
        "User-Agent": settings.worker_user_agent,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,application/pdf;q=0.8,*/*;q=0.5",
    }

    async with httpx.AsyncClient(
        timeout=settings.worker_request_timeout_seconds,
        follow_redirects=False,
        headers=headers,
        verify=True
    ) as client:
        while True:
            response = await client.get(current_url)

            # Check redirect status codes (301, 302, 303, 307, 308)
            if response.is_redirect:
                redirect_count += 1
                if redirect_count > settings.worker_max_redirects:
                    raise httpx.TooManyRedirects(f"Exceeded max redirects ({settings.worker_max_redirects})")

                redirect_location = response.headers.get("location")
                if not redirect_location:
                    break

                # Resolve relative redirect URLs against current URL
                redirect_url = str(response.url.join(redirect_location))
                # Security: validate the destination IP
                current_url = validate_redirect(redirect_url)
                continue

            response.raise_for_status()

            # Enforce max content length
            content_length = response.headers.get("content-length")
            if content_length and int(content_length) > settings.worker_max_response_bytes:
                raise ValueError(
                    f"Response size {content_length} bytes exceeds limit of {settings.worker_max_response_bytes} bytes."
                )

            body = response.content
            if len(body) > settings.worker_max_response_bytes:
                raise ValueError(f"Downloaded body exceeds limit of {settings.worker_max_response_bytes} bytes.")

            content_type = response.headers.get("content-type", "").lower()
            return SafeFetchResult(
                final_url=str(response.url),
                content=body,
                content_type=content_type,
                status_code=response.status_code
            )
