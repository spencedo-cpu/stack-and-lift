package app.stacklift;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Reminders.createChannel(context);
        Reminders.fire(context, intent.getStringExtra(Reminders.EXTRA_SLOT));
    }
}
