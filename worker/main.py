"""Notify Notice Worker: Scheduled crawler and rule-based extraction runner."""
import asyncio
import logging
import os
import sys
import uuid
from typing import Optional
from worker.config import settings
from worker.database import db
from worker.extractor import extract_notice_metadata
from worker.feed_parser import parse_feed_content
from worker.hasher import canonicalize_url, normalize_text, calculate_content_hash
from worker.html_parser import parse_html_document
from worker.http_client import safe_fetch_url, SSRFSecurityException
from worker.pdf_parser import parse_pdf_document

logging.basicConfig(
    level=getattr(logging, settings.worker_log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("notify.worker")


async def process_pdf_notice(college_id: str, source_id: str, pdf_url: str):
    """Fetch and process a linked PDF notice document."""
    try:
        res = await safe_fetch_url(pdf_url)
        parsed_pdf = parse_pdf_document(res.content, res.final_url)

        canonical = canonicalize_url(res.final_url)
        norm_text = normalize_text(f"{parsed_pdf.title} {parsed_pdf.full_text}")
        content_hash = calculate_content_hash(norm_text)

        extraction = extract_notice_metadata(parsed_pdf.title, parsed_pdf.full_text)

        db.save_notice(
            college_id=college_id,
            source_id=source_id,
            canonical_url=canonical,
            original_url=res.final_url,
            title=parsed_pdf.title,
            raw_content=parsed_pdf.full_text[:10000],  # bounded raw text
            normalized_content=norm_text[:10000],
            content_hash=content_hash,
            document_type="PDF",
            extraction=extraction
        )
    except Exception as e:
        logger.warning(f"Failed to process PDF notice {pdf_url}: {e}")


async def process_source(source: dict):
    """Process an approved notice source (Notice board, Homepage, or RSS Feed)."""
    source_id = source["id"]
    college_id = source["college_id"]
    url = source["url"]
    source_type = source.get("source_type", "NOTICE_BOARD_PAGE")

    logger.info(f"Processing source '{source.get('label')}' ({source_type}): {url}")

    try:
        fetch_res = await safe_fetch_url(url)
        content_type = fetch_res.content_type

        # 1. RSS / Atom Feed processing
        if "xml" in content_type or "rss" in content_type or source_type == "RSS_FEED":
            xml_text = fetch_res.content.decode("utf-8", errors="replace")
            feed_items = parse_feed_content(xml_text, fetch_res.final_url)
            logger.info(f"Extracted {len(feed_items)} feed items from {url}")

            for item in feed_items[:15]:  # Process up to 15 latest items
                canonical = canonicalize_url(item.link)
                norm_text = normalize_text(f"{item.title} {item.content}")
                content_hash = calculate_content_hash(norm_text)
                extraction = extract_notice_metadata(item.title, item.content)

                db.save_notice(
                    college_id=college_id,
                    source_id=source_id,
                    canonical_url=canonical,
                    original_url=item.link,
                    title=item.title,
                    raw_content=item.content[:5000],
                    normalized_content=norm_text[:5000],
                    content_hash=content_hash,
                    document_type="FEED_ITEM",
                    extraction=extraction
                )

        # 2. PDF Direct Notice
        elif "pdf" in content_type or url.lower().endswith(".pdf"):
            await process_pdf_notice(college_id, source_id, fetch_res.final_url)

        # 3. HTML Notice Board Page or Homepage
        else:
            html_text = fetch_res.content.decode("utf-8", errors="replace")
            parsed_html = parse_html_document(html_text, fetch_res.final_url)

            canonical = canonicalize_url(fetch_res.final_url)
            norm_text = normalize_text(f"{parsed_html.title} {parsed_html.body_text}")
            content_hash = calculate_content_hash(norm_text)

            extraction = extract_notice_metadata(parsed_html.title, parsed_html.body_text)

            # Save the board page notice itself if it has meaningful announcement body
            if len(parsed_html.body_text) > 100:
                db.save_notice(
                    college_id=college_id,
                    source_id=source_id,
                    canonical_url=canonical,
                    original_url=fetch_res.final_url,
                    title=parsed_html.title,
                    raw_content=parsed_html.body_text[:5000],
                    normalized_content=norm_text[:5000],
                    content_hash=content_hash,
                    document_type="HTML",
                    extraction=extraction
                )

            # Process discovered notice links (PDFs and direct notice/application links)
            for link in parsed_html.discovered_links[:20]:
                if link.is_pdf:
                    await process_pdf_notice(college_id, source_id, link.url)
                elif len(link.text) > 15:
                    # Ingest direct notice / Google form / Portal application link
                    link_norm = normalize_text(f"{link.text} {link.url}")
                    link_hash = calculate_content_hash(link_norm)
                    link_extraction = extract_notice_metadata(link.text, link.text)
                    db.save_notice(
                        college_id=college_id,
                        source_id=source_id,
                        canonical_url=canonicalize_url(link.url),
                        original_url=link.url,
                        title=link.text,
                        raw_content=f"{link.text}\nDirect Application/Document Link: {link.url}",
                        normalized_content=link_norm,
                        content_hash=link_hash,
                        document_type="HTML",
                        extraction=link_extraction
                    )

        db.update_source_status(source_id, success=True)
        logger.info(f"Source '{source.get('label')}' processed successfully.")

    except SSRFSecurityException as e:
        logger.error(f"Security violation for source {url}: {e}")
        db.update_source_status(source_id, success=False, error_msg=f"SSRF Security Violation: {e}")
    except Exception as e:
        logger.error(f"Error processing source {url}: {e}", exc_info=True)
        db.update_source_status(source_id, success=False, error_msg=str(e))


async def run_worker(college_id_filter: Optional[str] = None):
    """Main execution loop for scheduled worker."""
    worker_id = f"worker-{uuid.uuid4().hex[:8]}"
    lease_key = "notify_worker_lock"
    logger.info(f"Starting Notify Worker run ({worker_id})...")

    # 1. Acquire Lease
    has_lease = db.acquire_lease(lease_key, worker_id, ttl_seconds=900)
    if not has_lease:
        logger.warning("Another worker run holds the active lease. Exiting cleanly.")
        return 0

    try:
        # 2. Get active sources
        sources = db.get_active_sources(college_id_filter)
        logger.info(f"Found {len(sources)} active sources to check.")

        for source in sources:
            await process_source(source)

        logger.info("Notify Worker completed all source checks successfully.")
        return 0
    finally:
        db.release_lease(lease_key, worker_id)
        logger.info("Worker lease released.")


def main():
    college_filter = sys.argv[1] if len(sys.argv) > 1 else None
    exit_code = asyncio.run(run_worker(college_filter))
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
