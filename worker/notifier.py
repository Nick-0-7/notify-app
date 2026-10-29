"""Firebase Cloud Messaging (FCM) dispatch and token handling."""
import json
import logging
import os
from typing import List, Optional, Dict, Any
from worker.config import settings

logger = logging.getLogger("notify.notifier")

_firebase_initialized = False


def init_firebase_admin():
    """Initialize Firebase Admin SDK with service account credentials if configured."""
    global _firebase_initialized
    if _firebase_initialized:
        return True

    try:
        import firebase_admin
        from firebase_admin import credentials

        if settings.firebase_service_account_json:
            cert_dict = json.loads(settings.firebase_service_account_json)
            cred = credentials.Certificate(cert_dict)
            firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            logger.info("Firebase Admin SDK initialized via JSON string credentials.")
            return True
        elif settings.firebase_credentials_json_path and os.path.exists(settings.firebase_credentials_json_path):
            cred = credentials.Certificate(settings.firebase_credentials_json_path)
            firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            logger.info("Firebase Admin SDK initialized via credentials file path.")
            return True
        else:
            logger.warning("No Firebase credentials configured. Push notifications will run in dry-run mode.")
            return False
    except Exception as e:
        logger.warning(f"Could not initialize Firebase Admin SDK: {e}. Running in dry-run mode.")
        return False


def send_notice_push_notification(
    device_tokens: List[str],
    notice_id: str,
    title: str,
    body: str,
    category: str,
    is_high_priority: bool = False
) -> Dict[str, Any]:
    """
    Dispatch FCM notification to registered student device tokens:
    - High priority channel for exams & deadlines
    - Embeds deep-link 'notify://notice/{notice_id}'
    """
    if not device_tokens:
        logger.info(f"No active device tokens found for notice '{notice_id}'. Push skipped.")
        return {"success_count": 0, "failure_count": 0, "dry_run": True}

    channel_id = "channel_alerts" if is_high_priority else "channel_notices"
    deep_link = f"notify://notice/{notice_id}"

    has_firebase = init_firebase_admin()
    if not has_firebase:
        logger.info(
            f"[DRY-RUN PUSH] Target: {len(device_tokens)} devices | Channel: {channel_id} | "
            f"Title: '{title}' | Link: {deep_link}"
        )
        return {"success_count": len(device_tokens), "failure_count": 0, "dry_run": True}

    from firebase_admin import messaging

    android_config = messaging.AndroidConfig(
        priority="high" if is_high_priority else "normal",
        notification=messaging.AndroidNotification(
            channel_id=channel_id,
            click_action="FLUTTER_NOTIFICATION_CLICK",  # Compatible standard click intent
            tag=notice_id
        ),
        data={
            "notice_id": notice_id,
            "category": category,
            "deep_link": deep_link
        }
    )

    multicast = messaging.MulticastMessage(
        tokens=device_tokens,
        notification=messaging.Notification(
            title=title,
            body=body
        ),
        android=android_config,
        data={
            "notice_id": notice_id,
            "category": category,
            "deep_link": deep_link
        }
    )

    try:
        if hasattr(messaging, "send_each_for_multicast"):
            response = messaging.send_each_for_multicast(multicast)
        else:
            response = messaging.send_multicast(multicast)
        logger.info(
            f"FCM Multicast sent: {response.success_count} success, {response.failure_count} failures."
        )
        return {
            "success_count": response.success_count,
            "failure_count": response.failure_count,
            "dry_run": False
        }
    except Exception as e:
        logger.error(f"Error dispatching FCM multicast: {e}")
        return {"success_count": 0, "failure_count": len(device_tokens), "error": str(e)}
