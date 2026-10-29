"""Deterministic rule-based metadata extraction for college notices."""
import re
from typing import List, Dict, Tuple, Optional
from pydantic import BaseModel

CATEGORY_KEYWORDS = {
    "examination": ["exam", "examination", "midterm", "endsem", "semester exam", "hall ticket", "admit card", "invigilation"],
    "result": ["result", "grade sheet", "marks", "cgpa", "sgpa", "scorecard", "revaluation", "passed", "provisional degree"],
    "placement": ["placement", "interview", "recruitment", "internship", "package", "ctc", "company", "campus drive", "hiring"],
    "scholarship": ["scholarship", "financial assistance", "stipend", "grant", "fee concession", "merit-cum-means", "fellowship"],
    "admission": ["admission", "counseling", "seat allotment", "entrance test", "intake", "merit list", "enrollment", "quota"],
    "timetable": ["timetable", "schedule", "routine", "time table", "class timing", "slot schedule"],
    "event": ["conference", "seminar", "workshop", "hackathon", "symposium", "fest", "annual day", "sports meet", "guest lecture"],
    "general": ["circular", "holiday", "maintenance", "hostel", "transport", "library", "notice", "canteen", "advisory"]
}

# Regex for common date representations
DATE_PATTERNS = [
    re.compile(r"\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}\b"),
    re.compile(r"\b(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{1,2}(?:st|nd|rd|th)?,?\s+\d{4}\b", re.IGNORECASE),
    re.compile(r"\b\d{1,2}(?:st|nd|rd|th)?\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{4}\b", re.IGNORECASE),
]

DEADLINE_INDICATORS = re.compile(
    r"\b(deadline|last date|due date|submit before|submit on or before|apply by|submit by|latest by|closing date|\bby\b|\bbefore\b|upto|up to|till|without late fee|with late fee|late fee|super late fee)\b",
    re.IGNORECASE
)

DATE_RANGE_PATTERN = re.compile(
    r"(\b\d{1,2}(?:st|nd|rd|th)?\s+[A-Za-z]+\s+\d{4}|\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})\s+(?:to|till|-)\s+(\b\d{1,2}(?:st|nd|rd|th)?\s+[A-Za-z]+\s+\d{4}|\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})",
    re.IGNORECASE
)

FEE_MILESTONE_PATTERN = re.compile(
    r"\b(without late fee|super late fee|late fee)\b",
    re.IGNORECASE
)

ELIGIBILITY_INDICATORS = re.compile(
    r"\b(eligible|eligibility|cgpa|minimum|attendance|prerequisite|criteria|qualifying|qualified)\b",
    re.IGNORECASE
)

DOCUMENT_INDICATORS = re.compile(
    r"\b(hall ticket|admit card|id card|identity card|marksheet|certificate|resume|cv|photograph|undertaking|receipt|passbook)\b",
    re.IGNORECASE
)

ACTION_INDICATORS = re.compile(
    r"\b(download|apply online|register|submit|report to|contact|verify|collect form|deposit)\b",
    re.IGNORECASE
)


class ExtractedMetadata(BaseModel):
    category: str
    dates_extracted: List[str]
    deadlines_extracted: List[str]
    eligibility_criteria: List[str]
    required_documents: List[str]
    action_items: List[str]
    supporting_passages: Dict[str, List[str]]
    confidence_score: float


def split_sentences(text: str) -> List[str]:
    """Split text into distinct sentences without losing context."""
    raw = re.split(r"(?<=[.!?\n])\s+", text)
    cleaned = [s.strip() for s in raw if len(s.strip()) > 5]
    return cleaned


def extract_notice_metadata(title: str, content: str) -> ExtractedMetadata:
    """
    Deterministic rule-based extraction:
    - Never fabricates or hallucinates dates or criteria
    - Keeps exact supporting passages from original text
    - Calculates confidence score
    """
    full_text = f"{title}\n{content}"
    sentences = split_sentences(full_text)
    lowered_title = title.lower()
    lowered_content = content.lower()

    # 1. Determine Category
    scores: Dict[str, int] = {cat: 0 for cat in CATEGORY_KEYWORDS}
    for cat, keywords in CATEGORY_KEYWORDS.items():
        for kw in keywords:
            if kw in lowered_title:
                scores[cat] += 4  # Higher weight for title match
            if kw in lowered_content:
                scores[cat] += 1

    best_category = max(scores, key=lambda k: scores[k])
    if scores[best_category] == 0:
        best_category = "general"

    # 2. Extract Dates & Supporting Passages
    dates_found = set()
    deadlines_found = set()
    eligibility_found = set()
    documents_found = set()
    action_found = set()

    supporting_passages: Dict[str, List[str]] = {
        "category": [],
        "dates": [],
        "deadlines": [],
        "eligibility": [],
        "documents": [],
        "action_items": []
    }

    # Record category supporting passage
    for sentence in sentences:
        for kw in CATEGORY_KEYWORDS[best_category]:
            if kw in sentence.lower():
                supporting_passages["category"].append(sentence[:250])
                break
        if len(supporting_passages["category"]) >= 2:
            break

    # Identify range start dates so we don't accidentally mark opening dates as deadlines
    range_starts = set()
    for match in DATE_RANGE_PATTERN.finditer(full_text):
        range_starts.add(match.group(1))

    # Analyze each sentence for dates, deadlines, criteria, documents, actions
    for sentence in sentences:
        sentence_lower = sentence.lower()

        # Check for dates
        sentence_dates = []
        for pat in DATE_PATTERNS:
            for match in pat.finditer(sentence):
                sentence_dates.append(match.group(0))

        if sentence_dates:
            for d in sentence_dates:
                dates_found.add(d)
            supporting_passages["dates"].append(sentence[:250])

            # If sentence mentions deadline keywords, record as deadline
            if DEADLINE_INDICATORS.search(sentence_lower):
                for d in sentence_dates:
                    if d not in range_starts:
                        deadlines_found.add(f"{d} ({sentence[:120]})")
                supporting_passages["deadlines"].append(sentence[:250])

        # Check for eligibility
        if ELIGIBILITY_INDICATORS.search(sentence_lower):
            eligibility_found.add(sentence[:180])
            supporting_passages["eligibility"].append(sentence[:250])

        # Check for required documents
        doc_matches = DOCUMENT_INDICATORS.findall(sentence_lower)
        if doc_matches:
            for dm in doc_matches:
                documents_found.add(dm.title())
            supporting_passages["documents"].append(sentence[:250])

        # Check for action items
        if ACTION_INDICATORS.search(sentence_lower) and len(sentence) < 150:
            action_found.add(sentence[:150])
            supporting_passages["action_items"].append(sentence[:250])

    # Check date ranges across full text (e.g. 12th October 2026 to 14th October 2026)
    for match in DATE_RANGE_PATTERN.finditer(full_text):
        start_d, end_d = match.group(1), match.group(2)
        dates_found.add(start_d)
        dates_found.add(end_d)

        start_idx = max(0, match.start() - 60)
        end_idx = min(len(full_text), match.end() + 80)
        nearby_snippet = full_text[start_idx:end_idx]
        fee_match = FEE_MILESTONE_PATTERN.search(nearby_snippet)
        label = f" ({fee_match.group(1).title()})" if fee_match else ""
        deadlines_found.add(f"{end_d}{label}")
        supporting_passages["deadlines"].append(f"Submission window: {start_d} to {end_d}{label}")

    # Check for fee milestones with single dates (e.g. 17th October 2026 ... Super Late Fee)
    fee_window_pat = re.compile(
        r"(\b\d{1,2}(?:st|nd|rd|th)?\s+[A-Za-z]+\s+\d{4}|\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})(.{1,80}?)\b(without late fee|super late fee|late fee)\b",
        re.IGNORECASE | re.DOTALL
    )
    for match in fee_window_pat.finditer(full_text):
        d_val = match.group(1)
        fee_type = match.group(3).title()
        dates_found.add(d_val)
        deadlines_found.add(f"{d_val} ({fee_type})")

    # 3. Calculate Confidence Score
    confidence = 0.50
    if scores[best_category] >= 4:
        confidence += 0.20
    elif scores[best_category] >= 2:
        confidence += 0.10

    if dates_found:
        confidence += 0.15
    if deadlines_found:
        confidence += 0.08
    if eligibility_found or documents_found:
        confidence += 0.05

    confidence = min(0.98, max(0.40, confidence))

    return ExtractedMetadata(
        category=best_category,
        dates_extracted=sorted(list(dates_found)),
        deadlines_extracted=sorted(list(deadlines_found)),
        eligibility_criteria=sorted(list(eligibility_found)),
        required_documents=sorted(list(documents_found)),
        action_items=sorted(list(action_found)),
        supporting_passages=supporting_passages,
        confidence_score=round(confidence, 2)
    )


def generate_notification_body(title: str, raw_content: str, extraction: ExtractedMetadata) -> str:
    """
    Format a clear, high-signal push notification body tailored to student priorities:
    1. Highlights the deadline milestone (Without Late Fee / Final / Due date)
    2. Summarizes key actionable instructions without boilerplate text
    3. Fits cleanly into Android notification shade without truncation
    """
    parts = []
    if extraction.deadlines_extracted:
        # Prefer deadlines with explicit fee or schedule labels
        labeled = [c for c in extraction.deadlines_extracted if any(k in c.lower() for k in ["without late fee", "super late fee", "late fee", "deadline", "last date"])]
        chosen = labeled[0] if labeled else extraction.deadlines_extracted[0]
        clean_dl = re.sub(r"\(Submission window:.*?\)", "", chosen).strip()
        clean_dl = re.sub(r"\([A-Z][a-z0-9\s\n,.'/-]{30,}\)", "", clean_dl).strip()
        clean_dl = re.sub(r"\s+", " ", clean_dl)
        parts.append(f"Deadline: {clean_dl}")
    elif extraction.dates_extracted:
        parts.append(f"Date: {extraction.dates_extracted[0]}")

    # Clean excerpt: extract the meaningful announcement, removing header boilerplate
    announcement_match = re.search(
        r"\b(all\s+students|all\s+candidates|all\s+[a-z0-9.]+\s+students|this\s+is\s+to\s+inform|hereby\s+informed|applications\s+are\s+invited|it\s+is\s+notified|attention\s+is\s+drawn)\b.*",
        raw_content,
        re.IGNORECASE | re.DOTALL
    )
    if announcement_match:
        clean_text = re.sub(r"\s+", " ", announcement_match.group(0)).strip()
        parts.append(clean_text[:75])
    elif extraction.action_items:
        parts.append(extraction.action_items[0])
    else:
        # Fallback: remove college header lines
        clean_text = raw_content
        clean_text = re.sub(r"^(Shri\s+Shamrao|Sharad\s+Institute|An\s+Autonomous|Approved\s+by|Dist:\s+Kolhapur|Notice\s+Date|SITCOE).*?\n+", "", clean_text, flags=re.I)
        clean_text = re.sub(r"\s+", " ", clean_text).strip()
        if clean_text and not clean_text.startswith("[Notice is a scanned") and not clean_text.startswith("[Notice is a"):
            parts.append(clean_text[:70])
        else:
            parts.append(title[:70])

    body = " • ".join(p for p in parts if p)
    return body[:140] if len(body) > 140 else body
