"""Database persistence layer: Supabase integration, deduplication, and versioning."""
import logging
import uuid
from typing import List, Dict, Any, Optional
from worker.config import settings
from worker.extractor import ExtractedMetadata
from worker.notifier import send_notice_push_notification

logger = logging.getLogger("notify.database")


class DatabaseClient:
    def __init__(self, force_mock: bool = False):
        self._force_mock = force_mock
        self._supabase = None
        self._is_connected = False
        self._in_memory_notices: Dict[str, Dict[str, Any]] = {}
        self._in_memory_sources: List[Dict[str, Any]] = [
            {
                "id": "src-001",
                "college_id": "col-alpha-1",
                "label": "Official Circulars",
                "url": "https://apex.edu/circulars",
                "source_type": "NOTICE_BOARD_PAGE",
                "status": "approved_active",
                "check_interval_minutes": 15
            }
        ]

    def _get_client(self):
        if self._force_mock:
            return None
        if self._supabase is None and settings.supabase_service_role_key and "your-project-ref" not in settings.supabase_url:
            try:
                from supabase import create_client
                self._supabase = create_client(settings.supabase_url, settings.supabase_service_role_key)
                self._is_connected = True
                logger.info("Connected to Supabase via Service Role Key.")
            except Exception as e:
                logger.warning(f"Could not connect to Supabase: {e}. Running with mock store.")
        return self._supabase

    def acquire_lease(self, lease_key: str, worker_id: str, ttl_seconds: int = 900) -> bool:
        """Acquire concurrency lease to prevent overlapping worker runs."""
        client = self._get_client()
        if client:
            try:
                res = client.rpc("acquire_worker_lease", {
                    "p_lease_key": lease_key,
                    "p_holder_id": worker_id,
                    "p_ttl_seconds": ttl_seconds
                }).execute()
                return bool(res.data)
            except Exception as e:
                logger.warning(f"RPC acquire_worker_lease failed: {e}. Defaulting to true.")
                return True
        return True

    def release_lease(self, lease_key: str, worker_id: str) -> bool:
        """Release worker concurrency lease upon clean completion."""
        client = self._get_client()
        if client:
            try:
                res = client.rpc("release_worker_lease", {
                    "p_lease_key": lease_key,
                    "p_holder_id": worker_id
                }).execute()
                return bool(res.data)
            except Exception as e:
                logger.warning(f"RPC release_worker_lease failed: {e}")
                return True
        return True

    def get_active_sources(self, college_id_filter: Optional[str] = None) -> List[Dict[str, Any]]:
        """Retrieve approved sources ready for routine monitoring."""
        client = self._get_client()
        if client:
            try:
                query = client.table("notice_sources").select("*").eq("status", "approved_active")
                if college_id_filter:
                    query = query.eq("college_id", college_id_filter)
                res = query.execute()
                return res.data or []
            except Exception as e:
                logger.error(f"Error fetching active sources: {e}")
                return []
        # Return fallback sample sources
        if college_id_filter:
            return [s for s in self._in_memory_sources if s["college_id"] == college_id_filter]
        return self._in_memory_sources

    def update_source_status(self, source_id: str, success: bool, error_msg: Optional[str] = None):
        """Update last_checked_at, last_success_at, and error message for a source."""
        client = self._get_client()
        if client:
            try:
                payload = {"last_checked_at": "now()"}
                if success:
                    payload["last_success_at"] = "now()"
                    payload["last_error_message"] = None
                else:
                    payload["last_error_message"] = error_msg[:500] if error_msg else "Unknown crawl error"

                client.table("notice_sources").update(payload).eq("id", source_id).execute()
            except Exception as e:
                logger.warning(f"Failed to update source status for {source_id}: {e}")

    def save_notice(
        self,
        college_id: str,
        source_id: str,
        canonical_url: str,
        original_url: str,
        title: str,
        raw_content: str,
        normalized_content: str,
        content_hash: str,
        document_type: str,
        extraction: ExtractedMetadata
    ) -> Dict[str, Any]:
        """
        Deduplication and versioning pipeline:
        1. Check if (source_id, content_hash) already exists -> skip insert, update last_checked_at.
        2. Check if canonical_url exists with differing content_hash -> create version, update notice.
        3. Completely new notice -> insert notice, version 1, extraction, user notifications, and FCM push.
        """
        # 0. Outdated notice filter: reject notices referencing obsolete years (older than 6 months)
        import re
        obsolete_year = re.search(r"\b(202[0-4]|201\d)\b", title + " " + raw_content[:300])
        if obsolete_year and not re.search(r"\b(2025|2026|2027)\b", title):
            logger.info(f"Skipping outdated notice ({obsolete_year.group(0)}): {title[:40]}")
            return {"status": "SKIPPED_OUTDATED", "reason": f"Older than 6 months ({obsolete_year.group(0)})"}

        client = self._get_client()
        if client:
            try:
                # 1. Deduplication check: existing identical content
                existing = client.table("notices").select("id, content_hash, version_count").eq("source_id", source_id).eq("content_hash", content_hash).execute()
                if existing.data and len(existing.data) > 0:
                    notice_id = existing.data[0]["id"]
                    client.table("notices").update({"last_checked_at": "now()"}).eq("id", notice_id).execute()
                    logger.info(f"Duplicate notice skipped (unchanged hash {content_hash[:8]}): {title[:40]}")
                    return {"status": "SKIPPED_DUPLICATE", "notice_id": notice_id}

                # 2. Changed document check: same URL, new content
                url_match = client.table("notices").select("id, version_count, content_hash").eq("source_id", source_id).eq("canonical_url", canonical_url).execute()
                if url_match.data and len(url_match.data) > 0:
                    row = url_match.data[0]
                    notice_id = row["id"]
                    new_version = row.get("version_count", 1) + 1

                    # Save historical version
                    client.table("notice_versions").insert({
                        "notice_id": notice_id,
                        "version_number": new_version,
                        "title": title,
                        "raw_content": raw_content,
                        "normalized_content": normalized_content,
                        "content_hash": content_hash,
                        "change_summary": f"Document updated on {document_type} crawl"
                    }).execute()

                    # Update main notice record
                    client.table("notices").update({
                        "title": title,
                        "raw_content": raw_content,
                        "normalized_content": normalized_content,
                        "content_hash": content_hash,
                        "version_count": new_version,
                        "last_checked_at": "now()"
                    }).eq("id", notice_id).execute()

                    # Update extraction
                    client.table("notice_extractions").upsert({
                        "notice_id": notice_id,
                        "category": extraction.category,
                        "dates_extracted": extraction.dates_extracted,
                        "deadlines_extracted": extraction.deadlines_extracted,
                        "eligibility_criteria": extraction.eligibility_criteria,
                        "required_documents": extraction.required_documents,
                        "action_items": extraction.action_items,
                        "supporting_passages": extraction.supporting_passages,
                        "confidence_score": extraction.confidence_score
                    }).execute()

                    logger.info(f"Updated notice version {new_version} for: {title[:40]}")
                    return {"status": "UPDATED_VERSION", "notice_id": notice_id, "version": new_version}

                # 3. New Notice Insert
                notice_res = client.table("notices").insert({
                    "college_id": college_id,
                    "source_id": source_id,
                    "canonical_url": canonical_url,
                    "original_url": original_url,
                    "title": title,
                    "raw_content": raw_content,
                    "normalized_content": normalized_content,
                    "content_hash": content_hash,
                    "document_type": document_type,
                    "is_published": True,
                    "version_count": 1
                }).execute()

                new_notice = notice_res.data[0]
                notice_id = new_notice["id"]

                # Insert Version 1
                client.table("notice_versions").insert({
                    "notice_id": notice_id,
                    "version_number": 1,
                    "title": title,
                    "raw_content": raw_content,
                    "normalized_content": normalized_content,
                    "content_hash": content_hash
                }).execute()

                # Insert Extractions
                client.table("notice_extractions").insert({
                    "notice_id": notice_id,
                    "category": extraction.category,
                    "dates_extracted": extraction.dates_extracted,
                    "deadlines_extracted": extraction.deadlines_extracted,
                    "eligibility_criteria": extraction.eligibility_criteria,
                    "required_documents": extraction.required_documents,
                    "action_items": extraction.action_items,
                    "supporting_passages": extraction.supporting_passages,
                    "confidence_score": extraction.confidence_score
                }).execute()

                # Dispatch FCM Push Notifications to active college students
                tokens_res = client.table("device_tokens").select("token").eq("is_active", True).execute()
                tokens = [r["token"] for r in (tokens_res.data or []) if r.get("token")]

                is_high_priority = extraction.category in ("examination", "placement") or bool(extraction.deadlines_extracted)
                send_notice_push_notification(
                    device_tokens=tokens,
                    notice_id=notice_id,
                    title=f"[{extraction.category.upper()}] {title[:60]}",
                    body=raw_content[:140] + "...",
                    category=extraction.category,
                    is_high_priority=is_high_priority
                )

                logger.info(f"Published new notice '{notice_id}': {title[:40]}")
                return {"status": "INSERTED_NEW", "notice_id": notice_id}

            except Exception as e:
                logger.error(f"Error persisting notice: {e}")
                return {"status": "ERROR", "error": str(e)}

        # In-memory deduplication fallback
        dedup_key = f"{source_id}:{content_hash}"
        if dedup_key in self._in_memory_notices:
            logger.info(f"[In-Memory] Duplicate notice skipped: {title[:40]}")
            return {"status": "SKIPPED_DUPLICATE", "notice_id": self._in_memory_notices[dedup_key]["id"]}

        mock_id = str(uuid.uuid4())
        self._in_memory_notices[dedup_key] = {
            "id": mock_id,
            "title": title,
            "hash": content_hash,
            "category": extraction.category
        }
        logger.info(f"[In-Memory] Inserted new notice '{mock_id}': {title[:40]}")
        return {"status": "INSERTED_NEW", "notice_id": mock_id}

    def cleanup_expired_community_posts(self, max_days: int = 7) -> int:
        """
        Deletes student community posts older than max_days (default 7 days / 1 week).
        """
        client = self._get_client()
        if not client:
            return 0
        try:
            from datetime import datetime, timezone, timedelta
            cutoff = (datetime.now(timezone.utc) - timedelta(days=max_days)).isoformat()
            res = client.table("student_posts").delete().lt("created_at", cutoff).execute()
            deleted_count = len(res.data) if res.data else 0
            if deleted_count > 0:
                logger.info(f"Cleaned up {deleted_count} expired student community posts (> {max_days} days old)")
            return deleted_count
        except Exception as e:
            logger.error(f"Error cleaning expired community posts: {e}")
            return 0


db = DatabaseClient()

