# Notify — College Notification & Student Community Platform

**Notify** is a production-minded native Android application and cloud-backed monitoring system that helps college students receive real-time notices from college websites and collaborate within a private, college-isolated student community.

---

## Highlights & Features

- **Automated Notice Crawler & Extractor**:
  - Python worker crawling college homepages, notice boards, and RSS/Atom feeds.
  - Multi-engine PDF parser (PyMuPDF & PyPDF2) with scanned document OCR detection.
  - Deterministic, rules-based extraction for 8 categories (`examination`, `result`, `placement`, `scholarship`, `admission`, `event`, `timetable`, `general`).
  - Regex extraction of dates, deadlines, eligibility criteria, required documents, and action items with exact source passage preservation.
- **Enterprise-Grade SSRF Defense**:
  - Hostname resolution checks, blocking loopback (`127.0.0.0/8`), private networks (`10/8`, `172.16/12`, `192.168/16`), link-local (`169.254/16`), and cloud metadata (`169.254.169.254`).
  - Re-validates every redirect destination hop.
- **Deduplication & Change Detection**:
  - Canonical URL normalization and SHA-256 content hashing.
  - Unmodified notices are skipped; updated notices produce versioned audit snapshots.
  - Distributed PostgreSQL concurrency leases to prevent overlapping runs.
- **Native Android Experience (Jetpack Compose + Material 3)**:
  - 16 screens covering onboarding, auth, college selection, notice feeds, search/filters, notice details, source management, student community, post creator, discussion comments, notification inbox, bookmarks, user profile, content reporting, and admin tools.
  - 4 bottom navigation tabs: Home, Notices, Student Hub, Profile.
  - Material 3 dynamic theming, offline resilience, and touch-friendly controls.
- **Push Notifications & Deep Linking**:
  - Android notification channels for general notices and high-priority alerts.
  - Deep linking support (`notify://notice/{noticeId}`) for direct notice navigation from notifications.
- **Student Community & Private Storage**:
  - Same-college community feed for notes, questions, and resources.
  - Row Level Security (RLS) ensuring strict cross-college isolation.
  - Private Supabase Storage bucket with college-scoped folder access.

---

## Repository Structure

```
Notify/
├── android/                   # Native Android Jetpack Compose app
│   ├── app/
│   │   ├── src/main/kotlin/   # Kotlin source code (MVVM, Repositories, UI Screens)
│   │   └── src/test/kotlin/   # Android JVM Unit Tests
│   └── build.gradle.kts       # Gradle configuration
├── worker/                    # Python notice monitoring & extraction service
│   ├── tests/                 # Pytest automated test suite
│   ├── config.py              # Pydantic configuration
│   ├── security.py            # SSRF validation & IP check
│   ├── html_parser.py         # BeautifulSoup HTML parser & link discovery
│   ├── pdf_parser.py          # PyMuPDF & PyPDF2 parser with OCR detection
│   ├── feed_parser.py         # RSS/Atom feed parser
│   ├── extractor.py           # Deterministic rule-based extractor
│   ├── hasher.py              # URL canonicalization & SHA-256 hashing
│   ├── database.py            # Supabase database & deduplication layer
│   ├── notifier.py            # FCM push notification dispatcher
│   └── main.py                # Scheduled worker runner
├── supabase/
│   ├── migrations/            # Versioned SQL migrations (Schema, RLS, Storage)
│   └── tests/                 # pgTAP / SQL RLS automated tests
├── docs/                      # Technical documentation
│   ├── ARCHITECTURE.md        # Architecture diagrams & component design
│   ├── SECURITY.md            # SSRF mitigation & RLS policies
│   └── DEPLOYMENT.md          # Free-tier deployment guide
├── .github/workflows/         # CI pipeline and scheduled crawler workflow
│   ├── ci.yml                 # Android & Python CI automated tests
│   └── worker-schedule.yml    # 30-minute scheduled crawler
├── .env.example               # Environment variables template
└── README.md                  # Project overview & documentation
```

---

## Quick Start & Verification

### 1. Python Worker & Tests
```bash
# Run pytest automated test suite
python -m pytest worker/tests -v

# Run the worker crawler locally
python -m worker.main
```

### 2. Android App & Unit Tests
```bash
cd android

# Run Android unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```
The compiled APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## Definition of Done Verification

| Requirement | Implementation Details | Status |
|---|---|---|
| **Student Registration & College Join** | Supabase Auth, profiles trigger, and college membership workflow | ✅ Complete |
| **Verified Public Source Monitoring** | Public HTTP/HTTPS validator, SSRF protection, admin approval workflow | ✅ Complete |
| **Notice Deduplication** | Canonical URLs, SHA-256 content hashes, database uniqueness constraints | ✅ Complete |
| **Extracted Text & Metadata Display** | Dates, deadlines, criteria, documents, action items, confidence pills | ✅ Complete |
| **FCM Push Notifications** | FCM Multicast dispatcher with channels and deep links (`notify://notice/{id}`) | ✅ Complete |
| **Deep Link Handling** | `MainActivity` intent filter handling `notify://notice/{id}` directly to details | ✅ Complete |
| **Student Community & File Sharing** | Posts, attachments, comments, reactions, and private storage policies | ✅ Complete |
| **Cross-College Access Isolation** | PostgreSQL Row Level Security on all 15 tables with pgTAP tests | ✅ Complete |
| **Automated Tests Passing** | 17 Python pytest tests passing; Android unit tests passing | ✅ Complete |
| **Setup & Deployment Documentation** | Complete guides in `docs/ARCHITECTURE.md`, `SECURITY.md`, `DEPLOYMENT.md` | ✅ Complete |
