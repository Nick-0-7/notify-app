from worker.html_parser import parse_html_document

SAMPLE_HTML = """
<!DOCTYPE html>
<html>
<head>
    <title>Office of the Dean - Academic Notices</title>
</head>
<body>
    <nav>
        <a href="/home">Home</a>
        <a href="/about">About Us</a>
    </nav>
    <main>
        <h1>Annual Merit Scholarship Scheme Announced</h1>
        <p>All enrolled undergraduate students with CGPA above 8.5 are eligible to apply.</p>
        <p>The last date for application submission is 15th November 2026.</p>
        <ul>
            <li><a href="/downloads/scholarship_form_2026.pdf">Download Scholarship Circular (PDF)</a></li>
            <li><a href="/archive/notice-101.html">Archived Examination Schedule</a></li>
        </ul>
    </main>
    <footer>
        <p>Copyright 2026 Apex College</p>
    </footer>
</body>
</html>
"""


def test_html_parsing_strips_nav_and_footer():
    parsed = parse_html_document(SAMPLE_HTML, "https://apex.edu/notices")
    assert "Annual Merit Scholarship Scheme" in parsed.title or "Academic Notices" in parsed.title
    assert "Copyright" not in parsed.body_text
    assert "About Us" not in parsed.body_text
    assert "CGPA above 8.5" in parsed.body_text


def test_html_notice_link_discovery():
    parsed = parse_html_document(SAMPLE_HTML, "https://apex.edu/notices")
    urls = [l.url for l in parsed.discovered_links]
    assert "https://apex.edu/downloads/scholarship_form_2026.pdf" in urls
    pdf_link = next(l for l in parsed.discovered_links if l.is_pdf)
    assert pdf_link.is_pdf is True
