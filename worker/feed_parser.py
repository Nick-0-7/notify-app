"""RSS and Atom feed parsing module."""
from typing import List, Optional
import feedparser
from pydantic import BaseModel


class FeedItem(BaseModel):
    title: str
    link: str
    content: str
    published_at: Optional[str] = None


def parse_feed_content(xml_content: str, base_url: str) -> List[FeedItem]:
    """Parse RSS or Atom XML string and extract feed entries."""
    parsed = feedparser.parse(xml_content)
    items: List[FeedItem] = []

    for entry in parsed.entries:
        title = getattr(entry, "title", "Notice Item").strip()
        link = getattr(entry, "link", base_url).strip()

        # Extract content / summary
        content = ""
        if hasattr(entry, "content") and entry.content:
            content = entry.content[0].value
        elif hasattr(entry, "summary"):
            content = entry.summary
        elif hasattr(entry, "description"):
            content = entry.description

        published = getattr(entry, "published", None) or getattr(entry, "updated", None)

        items.append(
            FeedItem(
                title=title,
                link=link,
                content=content.strip(),
                published_at=published
            )
        )

    return items
