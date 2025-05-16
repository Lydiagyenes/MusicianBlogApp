package com.example.musicianblogapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.showSimpleNotification(
                context,
                NotificationHelper.CHANNEL_ID_REMINDERS, // Csatorna ID
                1001, // Egyedi notification ID az emlékeztetőnek
                "Ideje alkotni!",
                "Ne felejts el ma is posztolni a blogodra!",
                null // Nincs konkrét poszt, amihez navigálna
        );
    }
}