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
    r"\b(deadline|last date|due date|submit before|submit on or before|apply by|submit by|latest by|closing date|\bby\b|\bbefore\b)\b",
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
