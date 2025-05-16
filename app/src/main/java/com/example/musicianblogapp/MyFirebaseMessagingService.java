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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import androidx.annotation.NonNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    private static final String CHANNEL_ID = "musician_post_notifications";

    /**
     * Called when message is received.
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
        public void onMessageReceived(@NonNull RemoteMessage remoteMessage) { // Itt van a remoteMessage!
            super.onMessageReceived(remoteMessage); // Jó gyakorlat meghívni a szülőt

            Log.d(TAG, "From: " + remoteMessage.getFrom());

            String title = "Új értesítés"; // Alapértelmezett, ha nincs jobb
            String body = "Üzeneted érkezett."; // Alapértelmezett
            String postId = null; // Alapértelmezetten null

            // Adat payload ellenőrzése
            if (remoteMessage.getData().size() > 0) {
                Log.d(TAG, "Message data payload: " + remoteMessage.getData());
                title = remoteMessage.getData().getOrDefault("title", title); // Title az adatokból
                body = remoteMessage.getData().getOrDefault("body", body);   // Body az adatokból
                postId = remoteMessage.getData().get("postId"); // postId az adatokból
            }

            // Notification payload ellenőrzése (ha a Cloud Function küld ilyet is)
            if (remoteMessage.getNotification() != null) {
                Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
                // Felülírhatjuk a title/body-t, ha a notification payload jobb adatot ad
                if (remoteMessage.getNotification().getTitle() != null) {
                    title = remoteMessage.getNotification().getTitle();
                }
                if (remoteMessage.getNotification().getBody() != null) {
                    body = remoteMessage.getNotification().getBody();
                }
            }

            // Értesítés megjelenítése a NotificationHelperrel
            NotificationHelper.showSimpleNotification(
                    getApplicationContext(), // getApplicationContext() ajánlott Service-ből
                    NotificationHelper.CHANNEL_ID_POSTS, // A megfelelő csatorna
                    (int) System.currentTimeMillis(), // Egyedi ID minden értesítésnek
                    title,
                    body,
                    postId // A kiolvasott postId átadása
            );
        }
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer(token);
    }

    /**
     * Persist token to third-party servers. Modify this method to associate the user's FCM registration token with any server-side account maintained by your application.
     * @param token The new token.
     */
    private void sendRegistrationToServer(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && token != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            // Használj arrayUnion-t, hogy ne duplikálódjon a token
            db.collection("users").document(user.getUid())
                    .update("fcmTokens", FieldValue.arrayUnion(token))
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM Token updated successfully."))
                    .addOnFailureListener(e -> {
                        // Ha a user doksi még nem létezik vagy nincs tokens mező
                        Map<String, Object> tokenData = new HashMap<>();
                        tokenData.put("fcmTokens", Arrays.asList(token));
                        db.collection("users").document(user.getUid())
                                .set(tokenData, SetOptions.merge())
                                .addOnSuccessListener(s -> Log.d(TAG,"FCM Token created/merged successfully."))
                                .addOnFailureListener(f -> Log.e(TAG,"Error saving/merging FCM token", f));
                    });
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     * @param messageBody FCM message body received.
     * @param postId ID of the post to navigate to (optional)
     */
    private void sendNotification(String messageTitle, String messageBody, @Nullable String postId) {
        Intent intent;

        intent = new Intent(this, ForumActivity.class);

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_IMMUTABLE); // Vagy FLAG_ONE_SHOT

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_icon) // Cseréld le saját ikonra!
                        .setContentTitle(messageTitle)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Új Bejegyzés Értesítések", // Felhasználó által látható név
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }
}