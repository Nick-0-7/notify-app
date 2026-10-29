"""HTML parsing, meaningful text extraction, and notice link discovery."""
import re
from typing import List, Dict, Tuple
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from pydantic import BaseModel

NOTICE_KEYWORD_PATTERNS = re.compile(
    r"\b(notice|circular|announcement|examination|exam|timetable|result|placement|scholarship|admission|recruitment|seminar|order|revaluation|photocopy|backlog|re-examination|remedial|hallticket)\b",
    re.IGNORECASE
)


class DiscoveredLink(BaseModel):
    url: str
    text: str
    is_pdf: bool


class ParsedHTML(BaseModel):
    title: str
    body_text: str
    discovered_links: List[DiscoveredLink]


def parse_html_document(html_content: str, base_url: str) -> ParsedHTML:
    """
    Extract meaningful document text, headings, and notice links from HTML.
    Strips noise elements (navigation, footers, scripts, styles).
    """
    soup = BeautifulSoup(html_content, "html.parser")

    # 1. Remove non-content and noise tags
    for noise_tag in soup(["script", "style", "nav", "footer", "header", "aside", "noscript", "iframe"]):
        noise_tag.decompose()

    # 2. Extract Document Title
    title = ""
    # Try OpenGraph title
    og_title = soup.find("meta", property="og:title")
    if og_title and og_title.get("content"):
        title = og_title["content"].strip()
    elif soup.title and soup.title.string:
        title = soup.title.string.strip()
    elif soup.h1:
        title = soup.h1.get_text().strip()

    if not title:
        title = "College Notice Announcement"

    # 3. Discover relevant links
    discovered_links: List[DiscoveredLink] = []
    seen_urls = set()

    for a_tag in soup.find_all("a", href=True):
        href = a_tag["href"].strip()
        link_text = a_tag.get_text(separator=" ").strip()

        if not href or href.startswith("javascript:") or href.startswith("mailto:") or href.startswith("#"):
            continue

        abs_url = urljoin(base_url, href)
        if abs_url in seen_urls:
            continue

        is_pdf = abs_url.lower().endswith(".pdf")
        has_notice_keyword = bool(
            NOTICE_KEYWORD_PATTERNS.search(link_text) or
            NOTICE_KEYWORD_PATTERNS.search(href)
        )

        if is_pdf or has_notice_keyword:
            seen_urls.add(abs_url)
            discovered_links.append(
                DiscoveredLink(
                    url=abs_url,
                    text=link_text or (title if is_pdf else "Notice Document"),
                    is_pdf=is_pdf
                )
            )

    # 4. Extract meaningful body text
    body = soup.body if soup.body else soup
    text_chunks = []
    for elem in body.find_all(["h1", "h2", "h3", "h4", "p", "li", "td", "th"]):
        elem_text = elem.get_text(separator=" ").strip()
        if elem_text and len(elem_text) > 2:
            text_chunks.append(elem_text)

    full_body = "\n".join(text_chunks) if text_chunks else body.get_text(separator=" ").strip()

    return ParsedHTML(
        title=title,
        body_text=full_body,
        discovered_links=discovered_links
    )
