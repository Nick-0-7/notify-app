# Notify: Architecture & System Design

## 1. System Overview

**Notify** is a native Android application and cloud-backed monitoring system designed for college students to receive automated notices from college websites and interact within a private student hub.

```
                           +------------------------------+
                           |    College Notice Websites   |
                           |   (HTML / PDFs / RSS Feeds)  |
                           +--------------+---------------+
                                          |
                                          v (HTTPX with SSRF Defense)
+-------------------------------------------------------------------------+
|                  Python Monitoring Worker (GitHub Actions)              |
|                                                                         |
|  [Security Validator] -> [HTML/PDF/Feed Parsers] -> [Rule Extractor]   |
|         |                        |                          |           |
|  (SSRF & DNS recheck)    (Text & Hashes)             (Dates, Deadlines) |
+----------------------------------+--------------------------------------+
                                   |
                                   v (PostgREST / Service Role)
+-------------------------------------------------------------------------+
|                       Supabase Backend (PostgreSQL)                     |
|                                                                         |
|  - RLS Security Policies on all tables                                  |
|  - Versioned Notice Deduplication & History                             |
|  - Private College-Isolated Community Storage                           |
|  - Concurrency Lease Locking Table                                      |
+---------------------+-------------------------------+-------------------+
                      |                               |
                      v (FCM HTTP v1)                 v (Supabase Kotlin)
+---------------------+-------------+   +-------------+-------------------+
|      Firebase Cloud Messaging     |   |       Native Android App        |
|                                   |   |                                 |
|  - Channel: College Notices       |-->|  - Jetpack Compose (Material 3) |
|  - Channel: Urgent Announcements  |   |  - MVVM & Repository Pattern    |
|  - Deep-link: notify://notice/{id}|   |  - Notification Handlers        |
+-----------------------------------+   +---------------------------------+
```

---

## 2. Core Subsystems

### A. Native Android Client (`/android`)
- **UI Framework**: Modern Jetpack Compose with Material 3 design system, supporting light and dark themes.
- **Architecture**: MVVM with Repository Pattern, Kotlin Coroutines, and `StateFlow`.
- **Navigation**: Compose navigation supporting 4 primary bottom navigation destinations (`Home`, `Notices`, `Student Hub`, `Profile`) and 12 detail sub-screens.
- **Deep Linking**: Configured intent filter handling `notify://notice/{noticeId}` from push notifications and web links.
- **Resilience**: Repository layer connects to Supabase when credentials are configured and falls back to an offline in-memory cache when disconnected.

### B. Python Notice Worker (`/worker`)
- **HTTP Fetching**: Async client with `httpx` enforcing bounded timeouts (15s), maximum response sizes (10MB), and custom user-agent headers.
- **SSRF Defense**: Validates URL scheme and performs DNS resolution to block loopback (`127.0.0.0/8`), private networks (`10/8`, `172.16/12`, `192.168/16`), link-local (`169.254/16`), and cloud metadata endpoints (`169.254.169.254`). Re-validates every redirect destination.
- **Multi-Format Extraction**:
  - **HTML**: BeautifulSoup parsing extracting main body and discovering notice links.
  - **PDF**: PyMuPDF (`fitz`) and `PyPDF2` dual engine extracting selectable text; detects scanned images and flags `requires_ocr=True`.
  - **Feeds**: RSS/Atom parser with `feedparser`.
- **Deterministic Rules Extraction**:
  - Classifies notices into 8 categories (`examination`, `result`, `placement`, `scholarship`, `admission`, `event`, `timetable`, `general`).
  - Regex extraction for dates, deadlines, eligibility criteria, required documents, and action items.
  - Calculates confidence scores and preserves exact supporting text passages.
- **Deduplication**:
  - Canonical URL normalization.
  - SHA-256 content hashing.
  - Skips identical content; generates versioned snapshots for changed notices.

### C. Supabase Database & Security (`/supabase`)
- **15 Core Tables**: Versioned schema with UUID keys, foreign key constraints, and automatic `updated_at` triggers.
- **Strict Row Level Security (RLS)**: Enforces server-side permissions so students can only access their own college's data, cannot elevate roles, and cannot approve notice sources.
- **Private Storage**: `community_attachments` bucket restricts access to active members of the associated college.
