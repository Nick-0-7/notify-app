# Notify: Free-First Deployment Guide

This guide walks you through deploying the complete Notify system using standard free tiers:
- **Supabase**: Free tier (PostgreSQL, Auth, Storage, Realtime)
- **Firebase**: Free Spark plan (Firebase Cloud Messaging)
- **GitHub Actions**: 2,000 free runner minutes/month (Notice Crawler & CI)

---

## 1. Supabase Project Setup

1. **Create an Account**: Go to [supabase.com](https://supabase.com) and create a free project.
2. **Apply Database Migrations**:
   - In your Supabase Dashboard, open the **SQL Editor**.
   - Copy and execute the migrations in order:
     1. `supabase/migrations/20261028000001_initial_schema.sql`
     2. `supabase/migrations/20261028000002_row_level_security.sql`
     3. `supabase/migrations/20261028000003_storage_and_functions.sql`
   - Alternatively, use the Supabase CLI:
     ```bash
     supabase link --project-ref your-project-ref
     supabase db push
     ```
3. **Configure Authentication**:
   - In Supabase Dashboard -> **Authentication** -> **Providers**, enable **Email**.
   - Turn off "Confirm Email" if you want instant student registration during testing.
4. **Copy API Keys**:
   - Go to **Project Settings** -> **API**.
   - Note the **Project URL**, the public **Anon key**, and the **Service Role key** (keep secret!).

---

## 2. Firebase Cloud Messaging (FCM) Setup

1. **Create Firebase Project**:
   - Open [console.firebase.google.com](https://console.firebase.google.com) and create a new project.
2. **Add Android App**:
   - Package name: `com.notify.app`
   - Download `google-services.json` and place it in `android/app/google-services.json`.
3. **Generate Service Account Key for Backend Worker**:
   - Go to **Project Settings** -> **Service Accounts** tab.
   - Click **Generate new private key** and download the JSON file.
   - This credential is used by the Python worker to dispatch FCM push notifications.

---

## 3. Scheduled Worker Deployment (GitHub Actions)

1. Push your repository to GitHub.
2. Go to your repository's **Settings** -> **Secrets and variables** -> **Actions**.
3. Add the following repository secrets:
   - `SUPABASE_URL`: `https://your-project-ref.supabase.co`
   - `SUPABASE_SERVICE_ROLE_KEY`: `eyJhbGciOi...` (your Supabase service role key)
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: Paste the entire contents of your Firebase service account JSON.
4. The workflow in `.github/workflows/worker-schedule.yml` is preconfigured to trigger every 30 minutes and can also be triggered manually via the **Run workflow** button.

---

## 4. Building and Testing the Android App

### Running Unit Tests
```bash
cd android
./gradlew testDebugUnitTest
```

### Building the Debug APK
```bash
cd android
./gradlew assembleDebug
```
The compiled APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Installing to a Physical Android Device
1. Enable **Developer Options** and **USB Debugging** on your Android phone.
2. Connect your phone via USB and run:
   ```bash
   adb install -r android/app/build/outputs/apk/debug/app-debug.apk
   ```
3. Open Notify on your phone.
4. Go to **Profile** -> **Configure Live Supabase Credentials** to enter your Supabase URL and Anon key.
5. In Android 13+, allow notification permissions when prompted.

---

## 5. Free-Tier Quota Monitoring & Limits

- **Supabase Free Tier**:
  - 500 MB database space.
  - 1 GB file storage.
  - Pauses after 7 days of inactivity (can be unpaused from the dashboard).
- **GitHub Actions**:
  - 2,000 free minutes per month on public/private repositories.
  - A 30-minute schedule with ~45s execution time uses under 1,100 minutes per month.
- **Firebase Cloud Messaging**:
  - Unlimited push notification delivery on the free Spark plan.
