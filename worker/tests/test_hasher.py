from worker.hasher import canonicalize_url, normalize_text, calculate_content_hash


def test_canonicalize_url_tracking_params():
    url = "https://college.edu/notices/exam.html?utm_source=twitter&id=101&utm_medium=social"
    canonical = canonicalize_url(url)
    assert canonical == "https://college.edu/notices/exam.html?id=101"
    assert "utm_source" not in canonical


def test_canonicalize_url_trailing_slash_and_port():
    url = "HTTP://COLLEGE.EDU:80/notices/"
    canonical = canonicalize_url(url)
    assert canonical == "http://college.edu/notices"


def test_canonicalize_url_sorted_params():
    url1 = "https://college.edu/view?b=2&a=1"
    url2 = "https://college.edu/view?a=1&b=2"
    assert canonicalize_url(url1) == canonicalize_url(url2)


def test_normalize_text():
    raw = "  MIDTERM Examination\n\nSchedule   For   FALL 2026!  "
    norm = normalize_text(raw)
    assert norm == "midterm examination schedule for fall 2026!"


def test_calculate_content_hash():
    h1 = calculate_content_hash("sample notice text")
    h2 = calculate_content_hash("sample notice text")
    h3 = calculate_content_hash("different notice text")
    assert h1 == h2
    assert len(h1) == 64
    assert h1 != h3
