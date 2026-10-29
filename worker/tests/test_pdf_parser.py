import io
from reportlab.lib.pagesizes import letter
from reportlab.pdfgen import canvas
from worker.pdf_parser import parse_pdf_document


def create_test_pdf_bytes(text: str) -> bytes:
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer, pagesize=letter)
    c.setTitle("Examination Notice Circular")
    c.drawString(100, 750, text)
    c.save()
    return buffer.getvalue()


def create_empty_scanned_pdf_bytes() -> bytes:
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer, pagesize=letter)
    # Empty page with no text (simulates scanned image PDF)
    c.showPage()
    c.save()
    return buffer.getvalue()


def test_pdf_selectable_text_extraction():
    sample_text = "Controller of Examinations Notice: End semester timetable published."
    pdf_bytes = create_test_pdf_bytes(sample_text)

    parsed = parse_pdf_document(pdf_bytes, "https://college.edu/circular.pdf")
    assert "Controller of Examinations" in parsed.full_text
    assert parsed.page_count == 1
    assert parsed.requires_ocr is False


def test_pdf_scanned_ocr_detection():
    pdf_bytes = create_empty_scanned_pdf_bytes()

    parsed = parse_pdf_document(pdf_bytes, "https://college.edu/scanned.pdf")
    assert parsed.requires_ocr is True
    assert "Requires OCR" in parsed.full_text
