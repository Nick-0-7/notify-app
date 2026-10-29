# Notify: Security & Threat Mitigation Model

## 1. Threat Matrix & Controls

| Threat Category | Attack Vector | Mitigation in Notify |
|---|---|---|
| **Server-Side Request Forgery (SSRF)** | Malicious student enters internal/cloud metadata URL as notice source | `worker.security.validate_safe_url` resolves DNS and blocks private/loopback/cloud metadata IPs. Revalidates every redirect destination. |
| **Privilege Escalation** | Student modifies membership row or profile to become `college_admin` | PostgreSQL RLS explicitly forbids users from modifying `role`, `status`, or `is_system_admin`. Only server functions and existing admins can alter roles. |
| **Cross-College Data Leak** | Student from College B queries posts or notices from College A | RLS helper function `is_active_college_member` strictly confines queries to the caller's active college. |
| **Malicious File Uploads** | Attacker uploads executables (`.exe`, `.sh`) or scripts disguised as notes | Supabase Storage bucket policy restricts MIME types to `image/jpeg`, `image/png`, `image/webp`, and `application/pdf`. File size capped at 10MB. |
| **Worker Overlap / DoS** | Multiple workers run concurrently causing race conditions | Distributed worker leases via `acquire_worker_lease()` stored in PostgreSQL ensure mutual exclusion. |
| **Credential Leakage** | Hardcoding secrets in the Android APK | Android app only uses the public Supabase Anon key. Service role keys and Firebase service-account private keys are kept strictly in backend environment variables. |

---

## 2. Server-Side Request Forgery (SSRF) Defense

Notify implements a multi-layer defense for all user-submitted and monitored URLs:

1. **Protocol Restriction**: Only `http` and `https` schemes are allowed. Schemes like `file://`, `gopher://`, `ftp://`, or `data://` are rejected immediately.
2. **Hostname Blacklist**: Instant rejection for `localhost`, `metadata.google.internal`, `169.254.169.254`, and `instance-data`.
3. **DNS Pre-Resolution**:
   - Hostname is resolved to IPv4 and IPv6 addresses.
   - Every resolved IP is checked against standard non-routable CIDR blocks:
     - `127.0.0.0/8` (Loopback)
     - `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` (Private RFC 1918)
     - `169.254.0.0/16` (Link-Local & Cloud Metadata)
     - `fc00::/7`, `fe80::/10`, `::1` (IPv6 Private / Link-Local / Loopback)
4. **Redirect Destination Re-Validation**:
   - `httpx.AsyncClient` has `follow_redirects=False`.
   - Each HTTP redirect (301, 302, 307, 308) checks the target `Location` header through `validate_redirect` before making the next hop.
5. **Resource Limits**:
   - Max 3 redirects.
   - Max 10MB response body size.
   - 15-second request timeout.

---

## 3. Row Level Security (RLS) Policy Architecture

All tables in the `public` schema have Row Level Security enabled. Key access policies include:

- **`notices`**: Normal students have `SELECT` access to published notices in their college. Only service role or college admins have `INSERT` and `UPDATE` privileges.
- **`student_posts`**: Students can only `INSERT` posts if their membership in that college is `active`. They can only `UPDATE` or `DELETE` their own posts.
- **`college_memberships`**: Normal users can insert a membership request, but its `role` is locked to `'student'` and `status` to `'pending'`.
- **`device_tokens`**: Users can only query, insert, and update their own device tokens (`user_id = auth.uid()`).
