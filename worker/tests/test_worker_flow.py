from worker.database import DatabaseClient
from worker.extractor import ExtractedMetadata


def test_idempotent_duplicate_detection():
    db = DatabaseClient(force_mock=True)
    college_id = "col-alpha-1"
    source_id = "src-001"
    canonical_url = "https://college.edu/notices/1"
    content_hash = "abc123hash999"

    meta = ExtractedMetadata(
        category="examination",
        dates_extracted=["15/11/2026"],
        deadlines_extracted=[],
        eligibility_criteria=[],
        required_documents=[],
        action_items=[],
        supporting_passages={},
        confidence_score=0.85
    )

    # First save -> Inserted new
    res1 = db.save_notice(
        college_id=college_id,
        source_id=source_id,
        canonical_url=canonical_url,
        original_url=canonical_url,
        title="Exam Notice",
        raw_content="Exam notice body",
        normalized_content="exam notice body",
        content_hash=content_hash,
        document_type="HTML",
        extraction=meta
    )
    assert res1["status"] == "INSERTED_NEW"

    # Second save with exact same hash -> Skipped duplicate
    res2 = db.save_notice(
        college_id=college_id,
        source_id=source_id,
        canonical_url=canonical_url,
        original_url=canonical_url,
        title="Exam Notice",
        raw_content="Exam notice body",
        normalized_content="exam notice body",
        content_hash=content_hash,
        document_type="HTML",
        extraction=meta
    )
    assert res2["status"] == "SKIPPED_DUPLICATE"
    assert res2["notice_id"] == res1["notice_id"]
