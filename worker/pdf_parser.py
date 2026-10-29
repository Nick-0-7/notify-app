"""PDF extraction module supporting PyMuPDF (fitz) with pure-Python PyPDF2 fallback."""
import io
from typing import List
from pydantic import BaseModel

try:
    import fitz  # PyMuPDF (primary high performance extractor)
except Exception:
    fitz = None

try:
    import PyPDF2  # Pure-Python fallback for environments blocking native DLLs
except Exception:
    PyPDF2 = None


class ParsedPDF(BaseModel):
    title: str
    full_text: str
    page_count: int
    pages: List[str]
    requires_ocr: bool


def parse_pdf_document(pdf_bytes: bytes, original_url: str) -> ParsedPDF:
    """
    Extract selectable text from PDF documents.
    If the document has pages with virtually no selectable characters (e.g. scanned image),
    flags `requires_ocr = True` and does NOT fabricate text.
    """
    if not pdf_bytes:
        return ParsedPDF(
            title="Empty Document",
            full_text="",
            page_count=0,
            pages=[],
            requires_ocr=False
        )

    pages_text: List[str] = []
    page_count = 0
    total_chars = 0
    meta_title = ""

    # 1. Attempt PyMuPDF if available and allowed by host
    if fitz is not None:
        try:
            doc = fitz.open(stream=pdf_bytes, filetype="pdf")
            page_count = len(doc)
            for page_idx in range(page_count):
                page = doc[page_idx]
                text = page.get_text("text").strip()
                pages_text.append(text)
                total_chars += len(text)
            meta_title = doc.metadata.get("title", "")
            doc.close()
        except Exception:
            pages_text = []
            page_count = 0

    # 2. Pure Python Fallback: PyPDF2
    if not pages_text and PyPDF2 is not None:
        try:
            reader = PyPDF2.PdfReader(io.BytesIO(pdf_bytes))
            page_count = len(reader.pages)
            for page in reader.pages:
                text = (page.extract_text() or "").strip()
                pages_text.append(text)
                total_chars += len(text)
            if reader.metadata and reader.metadata.title:
                meta_title = reader.metadata.title
        except Exception:
            pass

    # Determine title
    title = ""
    if meta_title and len(meta_title.strip()) > 3:
        title = meta_title.strip()
    elif pages_text and pages_text[0]:
        first_lines = [l.strip() for l in pages_text[0].split("\n") if l.strip()]
        if first_lines:
            title = first_lines[0][:120]

    if not title:
        title = original_url.split("/")[-1].replace(".pdf", "").replace("-", " ").replace("_", " ").title()

    full_text = "\n\n--- Page Break ---\n\n".join(pages_text)

    # Scanned detection: if document has pages but total characters per page < 25
    requires_ocr = False
    if page_count > 0 and (total_chars / max(1, page_count)) < 25:
        requires_ocr = True
        try:
            from worker.ocr import extract_text_from_scanned_pdf
            ocr_text = extract_text_from_scanned_pdf(pdf_bytes, max_pages=5)
            if ocr_text and len(ocr_text.strip()) > 30:
                full_text = ocr_text
                requires_ocr = False
                pages_text = [p.strip() for p in ocr_text.split("--- Page Break ---") if p.strip()]
                # Check for a clearer title in the OCR header
                for line in ocr_text.splitlines()[:12]:
                    l = line.strip()
                    if any(k in l.lower() for k in ["examination", "revaluation", "photocopy", "schedule", "timetable", "circular", "notice"]) and len(l) > 10:
                        if not any(ign in l.lower() for ign in ["approved by", "autonomous", "trust's", "affiliated"]):
                            title = l[:120]
                            break
        except Exception:
            pass

        if not full_text:
            full_text = "[Notice is a scanned document/image. Requires OCR for full text extraction.]"

    return ParsedPDF(
        title=title,
        full_text=full_text,
        page_count=page_count,
        pages=pages_text,
        requires_ocr=requires_ocr
    )
