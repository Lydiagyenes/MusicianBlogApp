package com.example.musicianblogapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.Player; // Hiányzó import
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog; // Meglévő import
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.net.Uri;
import com.bumptech.glide.RequestManager; // Meglévő import
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.appcompat.widget.SearchView;

public class ForumActivity extends AppCompatActivity implements PostAdapter.OnPostInteractionListener, SearchView.OnQueryTextListener {

    // TAG átnevezése az Activity nevére (jobb gyakorlat)
    private static final String TAG = "ForumActivity";
    private ListenerRegistration currentPostsListener; // Az aktuálisan figyelt posztokhoz
    private SearchView searchView;
    private RecyclerView recyclerViewPosts;
    private PostAdapter postAdapter;
    private List<Post> postList;
    private FloatingActionButton fabAddPost;
    private ProgressBar progressBarMain;
    private Toolbar toolbar;
    private TextView textViewEmptyList;
    private String currentlyPlayingUrl = null;
    private Player.Listener playerListener;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener; // Meglévő

    private ExoPlayer exoPlayer; // Meglévő
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
     /*   AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent reminderIntent = new Intent(this, ReminderReceiver.class);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getBroadcast(this, 0, reminderIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            pendingIntent = PendingIntent.getBroadcast(this, 0, reminderIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

// Ha a mai 9 óra már elmúlt, a következő nap 9 órára állítjuk
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

// Ismétlődő alarm (napi)
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pendingIntent);
        Log.d(TAG, "Daily reminder alarm set for 9 AM."); */

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); // Set the toolbar as the action bar

        recyclerViewPosts = findViewById(R.id.recyclerViewPosts);
        fabAddPost = findViewById(R.id.fabAddPost);
        progressBarMain = findViewById(R.id.progressBarMain);
        textViewEmptyList = findViewById(R.id.textViewEmptyList);
        postList = new ArrayList<>();
        // Pass context to adapter for animation loading


        recyclerViewPosts.setLayoutManager(new LinearLayoutManager(this));


        fabAddPost.setOnClickListener(v -> {
            // Go to com.example.musicianblogapp.AddEditPostActivity for creating a new post
            Intent addPostIntent = new Intent(ForumActivity.this, AddEditPostActivity.class);
            // No POST_ID passed, indicating create mode
            startActivity(addPostIntent);
        });
        if (textViewEmptyList != null) {
            textViewEmptyList.setVisibility(View.GONE);
        }
        if (fabAddPost != null) { // Győződj meg róla, hogy a layoutban a FAB android:visibility="invisible"
            Animation fabAnimation = AnimationUtils.loadAnimation(this, R.anim.fab_show);
            fabAddPost.setVisibility(View.VISIBLE); // Először láthatóvá tesszük
            fabAddPost.startAnimation(fabAnimation); // Majd animáljuk
        }

    }

       private void loadPublicPosts() {
           Log.d(TAG, "loadPublicPosts() called");
           progressBarMain.setVisibility(View.VISIBLE);
           if (textViewEmptyList != null) {
               textViewEmptyList.setVisibility(View.GONE); // Üres üzenet elrejtése betöltéskor
           }
           setupAdapter();

           // Query definiálása
           Query query = db.collection("posts")
                   .whereEqualTo("isPublic", true)
                   .orderBy("createdAt", Query.Direction.DESCENDING);


           if (firestoreListener != null) {
               firestoreListener.remove();
           }
           firestoreListener = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
               @Override
               public void onEvent(@Nullable QuerySnapshot snapshots,
                                   @Nullable FirebaseFirestoreException e) {

                   // *** PROGRESS BAR ELREJTÉSE MINDEN ESETBEN! ***
                   progressBarMain.setVisibility(View.GONE);

                   if (e != null) {
                       Log.e(TAG, "Listen failed for public posts.", e);
                       Toast.makeText(ForumActivity.this, "Hiba a posztok betöltésekor: " + e.getMessage(), Toast.LENGTH_LONG).show();
                       // Hiba esetén is ürítjük a listát és frissítjük az adaptert
                       postList.clear();
                       postAdapter.notifyDataSetChanged();
                       checkIfListIsEmpty(); // Ellenőrizzük, hogy kell-e az üres üzenet
                       return;
                   }

                   if (snapshots == null) {
                       Log.w(TAG, "Snapshot listener returned null snapshots without error.");
                       postList.clear();
                       postAdapter.notifyDataSetChanged();
                       checkIfListIsEmpty(); // Ellenőrizzük, hogy kell-e az üres üzenet
                       return;
                   }

                   // Sikeres adatlekérdezés
                   Log.d(TAG, "Received " + snapshots.size() + " public posts.");
                   postList.clear(); // Lista ürítése
                   postList.addAll(snapshots.toObjects(Post.class)); // Új adatok hozzáadása
                   postAdapter.notifyDataSetChanged(); // Adapter értesítése (vagy DiffUtil)
                   Log.d(TAG, "Public posts loaded/updated in adapter: " + postList.size());

                   checkIfListIsEmpty(); // Ellenőrizzük, hogy kell-e az üres üzenet
                   postList.clear();
                   postList.addAll(snapshots.toObjects(Post.class));
                   if (postAdapter != null) { // Null check az adapterre
                       postAdapter.resetAnimation(); // Animáció resetelése
                       postAdapter.notifyDataSetChanged();
                   }
               }
           });
       }

    private void setupAdapter() {
        // Ha az adapter még nem létezik, vagy újra kell konfigurálni
        if (postAdapter == null) {
            RequestManager glide = Glide.with(this);
            String currentUserId = (mAuth.getCurrentUser() != null) ? mAuth.getCurrentUser().getUid() : null;

            postAdapter = new PostAdapter(
                    this,
                    postList,
                    glide,
                    currentUserId,
                    false, // Fórumon nincs szerk/törlés menü
                    new PostAdapter.OnPostInteractionListener() {
                        @Override
                        public void onAuthorClick(String authorUid) {
                            if (authorUid == null) return;
                            Intent intent = new Intent(ForumActivity.this, ProfileActivity.class);
                            intent.putExtra("USER_ID", authorUid);
                            startActivity(intent);
                        }

                        @Override
                        public void onPostClick(Post post) {
                            Log.d(TAG, "Post clicked: " + post.getId());
                            // Itt lehetne pl. egy PostDetailActivity-t indítani
                        }

                        @Override
                        public void onPlayStopAudioClick(String audioUrl) {
                            if (audioUrl == null || audioUrl.isEmpty()) return;

                            // Ellenőrizzük, hogy éppen ezt az URL-t játsszuk-e le
                            if (audioUrl.equals(currentlyPlayingUrl) && exoPlayer != null && exoPlayer.isPlaying()) {
                                // Ha igen, akkor állítsuk le
                                stopAudio();
                            } else {
                                // Ha nem, vagy nem játszunk le semmit, vagy mást játszunk, indítsuk el ezt
                                playAudio(audioUrl);
                            }
                        }
                        @Override public void onEditClick(Post post) { /* Fórumon nincs implementálva */ }
                        @Override public void onDeleteClick(Post post) { /* Fórumon nincs implementálva */ }
                    }
            );
            recyclerViewPosts.setAdapter(postAdapter); // Csak egyszer kell beállítani
        }
    }

    // *** ÚJ: Ellenőrzi, hogy a lista üres-e és megjeleníti/elrejti az üzenetet ***
    private void checkIfListIsEmpty() {
        if (textViewEmptyList != null) {
            if (postList.isEmpty()) {
                textViewEmptyList.setVisibility(View.VISIBLE);
                recyclerViewPosts.setVisibility(View.GONE); // Rejtsd el a RecyclerView-t is
            } else {
                textViewEmptyList.setVisibility(View.GONE);
                recyclerViewPosts.setVisibility(View.VISIBLE); // Mutasd a RecyclerView-t
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            goToLogin();
        } else {
            loadPublicPosts(); // Adatok betöltése/listener csatolása
            saveFcmToken();    // FCM token mentése
        }
        if (fabAddPost != null && fabAddPost.getVisibility() == View.VISIBLE) {
            Animation fabAnimation = AnimationUtils.loadAnimation(this, R.anim.fab_show);
            fabAddPost.startAnimation(fabAnimation);
        }
    }

          /* com.bumptech.glide.RequestManager glide = com.bumptech.glide.Glide.with(this);
           String currentUserId = (mAuth.getCurrentUser() != null) ? mAuth.getCurrentUser().getUid() : null;


            // Edit és Delete itt NEM kell
            @Override public void onEditClick(Post post) {  }
            @Override public void onDeleteClick(Post post) { } */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.forum_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();

        if (searchView != null) {
            searchView.setQueryHint("Keresés címekben..."); // String erőforrásból jobb
            searchView.setOnQueryTextListener(this); // Beállítjuk a listenert

            // Opcionális: Kezeljük a keresőmező bezárását, hogy újra betöltse az összes posztot
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    // Kereső megnyílt
                    return true; // True, hogy engedje a megnyitást
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    // Kereső bezárult (pl. vissza gombbal vagy X-szel)
                    loadPosts(null); // Töltsük be újra az összes posztot
                    return true; // True, hogy engedje a bezárást
                }
            });
        } else {
            Log.e(TAG, "SearchView not found in menu!");
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            // Saját profilra ugrás
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra("USER_ID", user.getUid());
                startActivity(intent);
            }
            return true;
        } else if (id == R.id.action_logout) {
            logoutUser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // FCM Token mentése
    private void saveFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);
                    // Save token to user's document
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null && token != null) {
                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        db.collection("users").document(user.getUid())
                                .update("fcmTokens", FieldValue.arrayUnion(token))
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM Token saved successfully."))
                                .addOnFailureListener(e -> {
                                    // Ha a user doksi még nem létezik (pl. régi regisztráció), set-tel próbálkozhatunk
                                    Map<String, Object> tokenData = new HashMap<>();
                                    tokenData.put("fcmTokens", Arrays.asList(token));
                                    db.collection("users").document(user.getUid())
                                            .set(tokenData, SetOptions.merge())
                                            .addOnSuccessListener(s -> Log.d(TAG,"FCM Token created/merged successfully."))
                                            .addOnFailureListener(f -> Log.e(TAG,"Error saving/merging FCM token", f));
                                });
                    }
                });
    }

    // Audio lejátszás (ExoPlayer példa)
   // private ExoPlayer exoPlayer;

    private void initializePlayer() {
        if (exoPlayer == null) {
            Log.d(TAG, "Initializing ExoPlayer");
            exoPlayer = new ExoPlayer.Builder(this).build();
            // Opcionális: Listener hozzáadása eseményekhez (pl. hiba, állapotváltozás)
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error: ", error);
                    Toast.makeText(ForumActivity.this, "Hiba az audio lejátszásakor", Toast.LENGTH_SHORT).show();
                }
            });
            playerListener = new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    // Ez hívódik meg, amikor a play/pause állapot változik
                    Log.d(TAG, "ExoPlayer onIsPlayingChanged: " + isPlaying);
                    if (!isPlaying) {
                        // Ha a lejátszás megállt (mert véget ért, vagy megállították)
                        // Akkor a "Lejátszás" állapotba kell visszaállítani a gombot,
                        // ha ez volt az az URL, amit játszottunk.
                        if (currentlyPlayingUrl != null) {
                            updatePlayButtonState(currentlyPlayingUrl, false); // Gomb frissítése "Play" állapotra
                            currentlyPlayingUrl = null; // Már nem játszunk le semmit
                        }
                    } else {
                        // Lejátszás elindult - a gombot már a playAudio-ban frissítettük "Stop"-ra
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    // Ez az állapotokat jelzi (IDLE, BUFFERING, READY, ENDED)
                    Log.d(TAG, "ExoPlayer onPlaybackStateChanged: " + playbackState);
                    if (playbackState == Player.STATE_ENDED) {
                        // Ha a lejátszás végére ért
                        Log.d(TAG, "ExoPlayer playback ended.");
                        // A gombot az onIsPlayingChanged(false) már visszaállítja,
                        // de itt is megtehetnénk, vagy elengedhetnénk a playert.
                        // currentlyPlayingUrl = null; // Biztonság kedvéért itt is nullázhatjuk
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error: ", error);
                    Toast.makeText(getApplicationContext(), "Hiba az audio lejátszásakor", Toast.LENGTH_SHORT).show();
                    // Hiba esetén is állítsuk vissza a gombot
                    if (currentlyPlayingUrl != null) {
                        updatePlayButtonState(currentlyPlayingUrl, false);
                        currentlyPlayingUrl = null;
                    }
                    // Opcionálisan releasePlayer();
                }
            };
            exoPlayer.addListener(playerListener);


        }
    }

    private void playAudio(String url) {
        if (url == null || url.isEmpty()) return;
        initializePlayer();
        if (exoPlayer.isPlaying()) {
            exoPlayer.stop(); // Megállítja és reseteli
            if(currentlyPlayingUrl != null){
                // A régi gombot visszaállítjuk "Play"-re
                updatePlayButtonState(currentlyPlayingUrl, false);
            }
        }
        currentlyPlayingUrl = url;
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
        Toast.makeText(this,"Audio lejátszása...", Toast.LENGTH_SHORT).show();
        updatePlayButtonState(currentlyPlayingUrl, true);
    }
    private void stopAudio() {
        Log.d(TAG, "stopAudio called");
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.stop(); // Megállítja és reseteli az állapotot

        }

    }
    private void updatePlayButtonState(String url, boolean isPlaying) {
        if (postAdapter == null || recyclerViewPosts == null || url == null) return;
        Log.d(TAG, "Updating button state for URL: " + url + " to isPlaying: " + isPlaying);

        // Végigmegyünk a listán, hogy megtaláljuk a posztot
        for (int i = 0; i < postList.size(); i++) {
            Post post = postList.get(i);
            if (url.equals(post.getAudioUrl())) {
                // Megtaláltuk a posztot, keressük meg a hozzá tartozó ViewHoldert
                PostAdapter.PostViewHolder holder = (PostAdapter.PostViewHolder) recyclerViewPosts.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.buttonPlayAudio != null) {
                    Log.d(TAG, "Found ViewHolder for position: " + i);
                    // Frissítjük a gombot
                    if (isPlaying) {
                        holder.buttonPlayAudio.setText(R.string.stop_audio); // Új string kell: "Leállítás"
                        holder.buttonPlayAudio.setIconResource(R.drawable.ic_stop); // Új ikon kell: Stop jel
                        // A kattintás mostantól a stopAudio-t hívja
                        holder.buttonPlayAudio.setOnClickListener(v -> stopAudio());
                    } else {
                        holder.buttonPlayAudio.setText(R.string.play_audio); // Vissza a "Lejátszás" szövegre
                        holder.buttonPlayAudio.setIconResource(R.drawable.ic_play_arrow); // Vissza a Play ikonra
                        // A kattintás újra a playAudio-t hívja az eredeti URL-lel
                        final String audioUrl = post.getAudioUrl(); // final a lambdához
                        holder.buttonPlayAudio.setOnClickListener(v -> playAudio(audioUrl));
                    }
                } else {
                    Log.w(TAG, "ViewHolder or buttonPlayAudio is null for position: " + i + ". Cannot update button state visually.");
                    // Ez előfordulhat, ha a view épp nincs a képernyőn.
                    // Amikor újra megjelenik, az onBindViewHolder helyesen fogja beállítani.
                }
                // Mivel megtaláltuk, kiléphetünk a ciklusból (feltéve, hogy egy URL csak egy poszthoz tartozik)
                break;
            }
        }
    }
    private void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detach the listener
        if (firestoreListener != null) {
            firestoreListener.remove();
            currentPostsListener = null;
        }
        releasePlayer(); // Fontos a lejátszó elengedése
    }


    private void showDeleteConfirmationDialog(Post post) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_title))
                .setMessage(getString(R.string.dialog_delete_message_generic))
                .setPositiveButton(getString(R.string.dialog_delete_positive), (dialog, which) -> {
                    deletePostFromFirestore(post.getId());
                })
                .setNegativeButton(getString(R.string.dialog_delete_negative), null) // Just dismiss
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void loadPosts(@Nullable String searchText) {
        Log.d(TAG, "loadPosts called with searchText: " + searchText);
        progressBarMain.setVisibility(View.VISIBLE);
        if (textViewEmptyList != null) textViewEmptyList.setVisibility(View.GONE);

        setupAdapter(); // Biztosítjuk, hogy az adapter létezik

        Query query;

        if (searchText != null && !searchText.trim().isEmpty()) {
            // --- KERESÉSI LEKÉRDEZÉS ---
            Log.d(TAG, "Performing search for title: " + searchText);
            query = db.collection("posts")
                    .whereEqualTo("isPublic", true) // Csak publikus
                    // A Firestore nem támogatja a "contains" vagy "like" keresést közvetlenül.
                    // Ez egy "kezdődik vele" (prefix) keresés lesz.
                    // A \uf8ff egy trükk, hogy a 'searchText'-tel kezdődő és utána bármi jöhető stringeket is megtalálja.
                    .orderBy("title") // Először cím szerint kell rendezni a range query-hez
                    .whereGreaterThanOrEqualTo("title", searchText.trim())
                    .whereLessThanOrEqualTo("title", searchText.trim() + "\uf8ff")
                    // Másodlagos rendezés dátum szerint (opcionális, de jó)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(20); // Limitáljuk a keresési eredményeket
        } else {
            // --- ALAPÉRTELMEZETT LEKÉRDEZÉS (Minden publikus poszt) ---
            Log.d(TAG, "Loading all public posts.");
            query = db.collection("posts")
                    .whereEqualTo("isPublic", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING);
        }

        // Régi listener eltávolítása, ha volt
        if (currentPostsListener != null) {
            currentPostsListener.remove();
        }

        currentPostsListener = query.addSnapshotListener((snapshots, e) -> {
            progressBarMain.setVisibility(View.GONE);
            if (e != null) {
                Log.e(TAG, "Listen failed for posts.", e);
                Toast.makeText(ForumActivity.this, "Hiba a posztok betöltésekor: " + e.getMessage(), Toast.LENGTH_LONG).show();
                postList.clear();
                if (postAdapter != null) postAdapter.notifyDataSetChanged();
                checkIfListIsEmpty();
                return;
            }

            if (snapshots != null) {
                Log.d(TAG, "Received " + snapshots.size() + " posts.");
                postList.clear();
                postList.addAll(snapshots.toObjects(Post.class));
                if (postAdapter != null) {
                    postAdapter.resetAnimation(); // Animáció reset
                    postAdapter.notifyDataSetChanged();
                }
                checkIfListIsEmpty();
            } else {
                postList.clear();
                if (postAdapter != null) postAdapter.notifyDataSetChanged();
                checkIfListIsEmpty();
            }
        });
    }
    private void deletePostFromFirestore(String postId) {
        if (postId == null) {
            Log.w(TAG, "Cannot delete post with null ID");
            return;
        }
        progressBarMain.setVisibility(View.VISIBLE); // Show progress during delete
        db.collection("posts").document(postId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    progressBarMain.setVisibility(View.GONE);
                    Log.d(TAG, "com.example.musicianblogapp.Post successfully deleted!");
                    Toast.makeText(ForumActivity.this, R.string.post_deleted_success, Toast.LENGTH_SHORT).show();
                    // The snapshot listener will automatically update the UI
                })
                .addOnFailureListener(e -> {
                    progressBarMain.setVisibility(View.GONE);
                    Log.w(TAG, "Error deleting post", e);
                    Toast.makeText(ForumActivity.this, getString(R.string.error_deleting_post) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void logoutUser() {
        mAuth.signOut();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(ForumActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.d(TAG, "Search submitted: " + query);
        loadPosts(query); // Indítjuk a keresést a beírt szöveggel
        if (searchView != null) {
            searchView.clearFocus(); // Billentyűzet elrejtése
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public void onAuthorClick(String authorUid) {

    }

    @Override
    public void onPostClick(Post post) {

    }

    @Override
    public void onPlayStopAudioClick(String audioUrl) {

    }

    @Override
    public void onEditClick(Post post) {

    }

    @Override
    public void onDeleteClick(Post post) {

    }
}