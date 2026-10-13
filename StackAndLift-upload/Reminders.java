package app.stacklift;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Daily supplement reminders: one alarm per time slot, rescheduled after each fires. */
public class Reminders {
    static final String PREFS = "stacklift";
    static final String KEY_SCHEDULE = "schedule";
    static final String CHANNEL = "reminders";
    static final String EXTRA_SLOT = "slot";
    private static final int MAX_SLOTS = 12;

    static void createChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Supplement reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("A reminder at each time you take supplements or meds");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static JSONObject schedule(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCHEDULE, null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) { return null; }
    }

    static PendingIntent alarmIntent(Context c, int index, String slotId) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("app.stacklift.REMIND_" + index);
        i.putExtra(EXTRA_SLOT, slotId);
        return PendingIntent.getBroadcast(c, index, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Cancels old alarms and sets the next occurrence of every slot that has items. */
    static void scheduleAll(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (int i = 0; i < MAX_SLOTS; i++) am.cancel(alarmIntent(c, i, ""));
        JSONObject sch = schedule(c);
        if (sch == null) return;
        try {
            JSONArray slots = sch.getJSONArray("slots");
            for (int i = 0; i < slots.length() && i < MAX_SLOTS; i++) {
                JSONObject slot = slots.getJSONObject(i);
                String id = slot.getString("id");
                if (!slotHasItems(sch, id)) continue;
                String[] hm = slot.getString("time").split(":");
                Calendar t = Calendar.getInstance();
                t.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                t.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                t.set(Calendar.SECOND, 0);
                t.set(Calendar.MILLISECOND, 0);
                if (t.getTimeInMillis() <= System.currentTimeMillis() + 30_000) t.add(Calendar.DAY_OF_MONTH, 1);
                PendingIntent pi = alarmIntent(c, i, id);
                boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            }
        } catch (Exception ignored) {}
    }

    static boolean slotHasItems(JSONObject sch, String slotId) {
        try {
            JSONArray items = sch.getJSONArray("items");
            for (int i = 0; i < items.length(); i++)
                if (slotId.equals(items.getJSONObject(i).optString("slot"))) return true;
        } catch (Exception ignored) {}
        return false;
    }

    /** Called when a slot's alarm fires: posts a notification listing what's due today. */
    static void fire(Context c, String slotId) {
        JSONObject sch = schedule(c);
        if (sch != null && slotId != null) {
            try {
                int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
                String label = slotId, time = "";
                int index = 0;
                JSONArray slots = sch.getJSONArray("slots");
                for (int i = 0; i < slots.length(); i++) {
                    JSONObject s = slots.getJSONObject(i);
                    if (slotId.equals(s.getString("id"))) { label = s.optString("label", slotId); index = i; }
                }
                List<String> lines = new ArrayList<>();
                JSONArray items = sch.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    if (!slotId.equals(it.optString("slot"))) continue;
                    JSONArray days = it.optJSONArray("days");
                    boolean today = days == null || days.length() == 0;
                    if (days != null) for (int d = 0; d < days.length(); d++) if (days.optInt(d) == dow) today = true;
                    if (!today) continue;
                    String dose = it.optString("dose", "");
                    lines.add(it.optString("name") + (dose.isEmpty() ? "" : " · " + dose));
                }
                if (!lines.isEmpty()) notify(c, index, label + " · " + lines.size() + (lines.size() == 1 ? " item" : " items"), String.join("\n", lines));
            } catch (Exception ignored) {}
        }
        scheduleAll(c);
    }

    static void notify(Context c, int id, String title, String body) {
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 100 + id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String first = body.contains("\n") ? body.substring(0, body.indexOf('\n')) + " …" : body;
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(first)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.areNotificationsEnabled()) nm.notify(id, n);
    }
}
