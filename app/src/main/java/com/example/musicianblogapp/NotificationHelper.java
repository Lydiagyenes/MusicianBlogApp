package com.example.musicianblogapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.musicianblogapp.ForumActivity; // A fő Activity, amit megnyitunk
import com.example.musicianblogapp.R; // Az R osztály az erőforrásokhoz

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    public static final String CHANNEL_ID_REMINDERS = "musician_app_reminders"; // Emlékeztetőknek
    public static final String CHANNEL_ID_POSTS = "musician_app_new_posts";   // Új posztoknak (FCM)

    // Metódus az értesítési csatornák létrehozására (API 26+ szükséges)
    // Ezt hívd meg az Application osztály onCreate-jében, vagy az első Activity onCreate-jében.
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            // Emlékeztető Csatorna
            NotificationChannel remindersChannel = new NotificationChannel(
                    CHANNEL_ID_REMINDERS,
                    "Napi Emlékeztetők", // Felhasználó által látható név
                    NotificationManager.IMPORTANCE_DEFAULT // Fontosság
            );
            remindersChannel.setDescription("Emlékeztetők új posztok írására vagy olvasására.");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(remindersChannel);
                Log.d(TAG, "Reminder Notification Channel created.");
            }

            // Új Posztok Csatorna (FCM-hez)
            NotificationChannel postsChannel = new NotificationChannel(
                    CHANNEL_ID_POSTS,
                    "Új Bejegyzések", // Felhasználó által látható név
                    NotificationManager.IMPORTANCE_HIGH // Fontosabb, hanggal/rezgéssel
            );
            postsChannel.setDescription("Értesítés, ha egy követett zenész új bejegyzést tesz közzé.");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(postsChannel);
                Log.d(TAG, "New Posts Notification Channel created.");
            }
        }
    }

    // Metódus egy egyszerű szöveges értesítés megjelenítésére
    // Ezt hívhatja a ReminderReceiver vagy az MyFirebaseMessagingService
    public static void showSimpleNotification(Context context, String channelId, int notificationId, String title, String message, @Nullable String targetPostId) {
        Intent intent;
        // Ha van targetPostId, akkor a ForumActivity-t nyitjuk meg extra adattal, hogy oda navigáljon
        // Ezt a ForumActivity-nek kell majd kezelnie az onCreate-ben vagy onNewIntent-ben.
        // Most egyszerűen csak a ForumActivity-t nyitjuk meg.
        intent = new Intent(context, ForumActivity.class);
        if (targetPostId != null) {
            intent.putExtra("NAVIGATE_TO_POST_ID", targetPostId); // Ezt a kulcsot kell majd keresni
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            pendingIntent = PendingIntent.getActivity(context, notificationId /* Request code */, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            pendingIntent = PendingIntent.getActivity(context, notificationId /* Request code */, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }


        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, channelId) // A megfelelő csatorna ID-t használjuk
                        .setSmallIcon(R.drawable.ic_notification_icon) // Saját értesítési ikon
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true) // Értesítés eltűnik kattintásra
                        .setSound(defaultSoundUri)
                        .setPriority(channelId.equals(CHANNEL_ID_POSTS) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, notificationBuilder.build());
            Log.d(TAG, "Notification shown with ID: " + notificationId + " on channel: " + channelId);
        } else {
            Log.e(TAG, "NotificationManager is null, cannot show notification.");
        }
    }
}