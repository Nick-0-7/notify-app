from worker.extractor import extract_notice_metadata


def test_extractor_examination_category():
    title = "Schedule for Fall 2026 Midterm Examination"
    content = (
        "All candidates must download their hall ticket before 10/11/2026. "
        "Minimum 75% attendance is required to appear in the examination hall. "
        "The deadline for fees clearance is 05/11/2026."
    )
    meta = extract_notice_metadata(title, content)
    assert meta.category == "examination"
    assert any("10/11/2026" in d for d in meta.dates_extracted)
    assert any("05/11/2026" in dl for dl in meta.deadlines_extracted)
    assert any("attendance" in el.lower() for el in meta.eligibility_criteria)
    assert any("Hall Ticket" in doc for doc in meta.required_documents)
    assert meta.confidence_score >= 0.70
    assert len(meta.supporting_passages["category"]) > 0


def test_extractor_placement_category():
    title = "Campus Placement Drive: Software Development Engineer Intern"
    content = (
        "Google Cloud recruitment for graduating students. "
        "Eligible branches: Computer Science and Information Technology. "
        "Submit your updated resume by November 15, 2026."
    )
    meta = extract_notice_metadata(title, content)
    assert meta.category == "placement"
    assert any("Resume" in doc for doc in meta.required_documents)
    assert any("November 15, 2026" in dl for dl in meta.deadlines_extracted)


def test_extractor_no_hallucinated_dates():
    title = "General Library Maintenance Advisory"
    content = "The central library reading rooms will remain closed for electrical maintenance."
    meta = extract_notice_metadata(title, content)
    assert len(meta.dates_extracted) == 0
    assert len(meta.deadlines_extracted) == 0
    assert meta.category == "general"
