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
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            // Handle data payload (e.g., navigate to specific post)
            // Itt dolgozhatod fel a Cloud Function által küldött adatokat
            String title = remoteMessage.getData().getOrDefault("title", "Új bejegyzés");
            String body = remoteMessage.getData().getOrDefault("body", "Egy általad követett zenész új bejegyzést tett közzé.");
            String postId = remoteMessage.getData().get("postId"); // Pl. hogy hova navigáljon
            sendNotification(title, body, postId);

        }

        // Check if message contains a notification payload. (Ha a Cloud Func notification részt is küld)
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            // Ha van data payload is, abból vehetjük a postId-t, különben null
            String postId = remoteMessage.getData().get("postId");
            sendNotification(title != null ? title : "Értesítés",
                    body != null ? body : "Üzeneted érkezett",
                    postId);
        }
    }

    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
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
        // Ha van postId, akkor a ForumActivity-t nyitjuk meg extra adattal
        // (A ForumActivity-nek kell majd kezelnie ezt az extra adatot és esetleg megnyitni a posztot)
        // VAGY csinálhatsz egy külön PostDetailActivity-t is.
        // Most egyszerűen a ForumActivity-t nyitjuk meg.
        intent = new Intent(this, ForumActivity.class);
        // if (postId != null) {
        //    intent.putExtra("NAVIGATE_TO_POST_ID", postId);
        // }
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