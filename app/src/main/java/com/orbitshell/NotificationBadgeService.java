package com.orbitshell;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationBadgeService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) { }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { }
}
