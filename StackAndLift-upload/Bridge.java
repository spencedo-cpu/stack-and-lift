package app.stacklift;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/** Functions the web page can call as window.AndroidBridge.* */
public class Bridge {
    private final Activity activity;

    Bridge(Activity activity) { this.activity = activity; }

    /** Saves the supplement schedule and (re)sets one daily reminder per time slot. */
    @JavascriptInterface
    public void setSchedule(String json) {
        activity.getSharedPreferences(Reminders.PREFS, Context.MODE_PRIVATE)
                .edit().putString(Reminders.KEY_SCHEDULE, json).apply();
        Reminders.scheduleAll(activity);
    }

    @JavascriptInterface
    public void testNotification() {
        Reminders.notify(activity, 999, "Stack & Lift", "Reminders are working. You'll get one at each supplement time.");
    }

    @JavascriptInterface
    public String notificationStatus() {
        NotificationManager nm = activity.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled() ? "on" : "off";
    }

    @JavascriptInterface
    public void openNotificationSettings() {
        activity.runOnUiThread(() -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            activity.startActivity(i);
        });
    }

    /** Opens the Android share sheet with a backup, e.g. to save it to Drive or email it. */
    @JavascriptInterface
    public void share(String text) {
        activity.runOnUiThread(() -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Stack & Lift backup");
            send.putExtra(Intent.EXTRA_TEXT, text);
            activity.startActivity(Intent.createChooser(send, "Save backup"));
        });
    }
}
