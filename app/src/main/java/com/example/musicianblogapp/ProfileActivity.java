package com.example.musicianblogapp;

import android.content.Intent;
import android.net.Uri; // Szükség lehet rá
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView; // ImageView import
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull; // NonNull import
import androidx.appcompat.app.AlertDialog; // AlertDialog import
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Toolbar import (ha használsz)
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer; // ExoPlayer import (ha itt is kell)
import androidx.recyclerview.widget.LinearLayoutManager; // RecyclerView import
import androidx.recyclerview.widget.RecyclerView; // RecyclerView import

import com.bumptech.glide.Glide; // Glide import
import com.bumptech.glide.RequestManager; // Glide import
import com.google.android.gms.tasks.Task; // Task import
import com.google.android.gms.tasks.Tasks; // Tasks import (törléshez)
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference; // DocumentReference import
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration; // ListenerRegistration import
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch; // WriteBatch import (követéshez)
import com.google.firebase.storage.FirebaseStorage; // FirebaseStorage import
import com.google.firebase.storage.StorageReference; // StorageReference import


import java.util.ArrayList; // ArrayList import
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView; // CircleImageView import

public class ProfileActivity extends AppCompatActivity implements PostAdapter.OnPostInteractionListener {

    private static final String TAG = "ProfileActivity";

    // --- UI Elemek (activity_profile.xml alapján) ---
    private CircleImageView profileImageView;
    private TextView textViewDisplayName, textViewFollowersCount, textViewFollowingCount;
    private Button buttonFollowEdit; // Ez lesz vagy "Követés", "Mégse", vagy "Szerkesztés"
    private RecyclerView recyclerViewUserPosts;
    private ProgressBar progressBarProfile;
    private TextView textViewEmptyList;
    private Toolbar toolbarProfile;

    // --- Firebase ---
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage; // Törléshez kellhet
    private FirebaseUser currentUser; // A bejelentkezett user
    private ListenerRegistration postsListener; // Posztok figyeléséhez
    private ListenerRegistration userListener;  // Profil user adatainak figyeléséhez

    // --- Adapter és Adatok ---
    private PostAdapter postAdapter;
    private List<Post> userPostList;
    private RequestManager glide;

    // --- Állapot ---
    private String profileUserId; // Annak a usernek az ID-ja, akinek a profilját nézzük
    private boolean isOwnProfile;
    private boolean isFollowing = false; // Követi-e a currentUser a profileUser-t?
    private boolean isLoadingFollowStatus = true; // Követési állapot töltése

    // --- ExoPlayer (ha itt is kell lejátszás) ---
    private ExoPlayer exoPlayer;
    private String currentlyPlayingUrl = null;
    private Player.Listener playerListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Firebase inicializálás
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = mAuth.getCurrentUser();
        glide = Glide.with(this); // Glide inicializálása

        // Profil ID lekérése az Intentből
        profileUserId = getIntent().getStringExtra("USER_ID");
        if (profileUserId == null) {
            Toast.makeText(this, "Hiba: Felhasználói ID hiányzik.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUser = mAuth.getCurrentUser();

        isOwnProfile = currentUser != null && currentUser.getUid().equals(profileUserId);


        Log.d(TAG, "Profile User ID: " + profileUserId); // Logolás debuggoláshoz
        Log.d(TAG, "Current User ID: " + (currentUser != null ? currentUser.getUid() : "null")); // Logolás
        Log.d(TAG, "Is Own Profile? " + isOwnProfile); // Logolás
        // UI elemek bekötése (activity_profile.xml alapján!)


        profileImageView = findViewById(R.id.profileImageView); // Példa ID
        textViewDisplayName = findViewById(R.id.textViewProfileName); // Példa ID
        textViewFollowersCount = findViewById(R.id.textViewFollowersCount); // Példa ID
        textViewFollowingCount = findViewById(R.id.textViewFollowingCount); // Példa ID
        buttonFollowEdit = findViewById(R.id.buttonFollowEditProfile); // Példa ID
        recyclerViewUserPosts = findViewById(R.id.recyclerViewUserPosts); // Példa ID
        progressBarProfile = findViewById(R.id.progressBarProfile); // Példa ID
        textViewEmptyList = findViewById(R.id.textViewEmptyList);
        toolbarProfile = findViewById(R.id.toolbarProfile); // Példa ID

        if (toolbarProfile != null) { // Null check!
            setSupportActionBar(toolbarProfile);
            if (getSupportActionBar() != null) { // Null check!
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }}
        // RecyclerView beállítása
        userPostList = new ArrayList<>();
        recyclerViewUserPosts.setLayoutManager(new LinearLayoutManager(this));

        // Gomb listener beállítása
        buttonFollowEdit.setOnClickListener(v -> handleFollowEditButtonClick());

        if (textViewEmptyList != null) {
            textViewEmptyList.setVisibility(View.GONE);
        }
        // Adatok betöltése
        loadUserData();
        if (!isOwnProfile && currentUser != null) {
            checkIfFollowing();
        }
        loadUserPosts();
    }

    private void setupRecyclerView() {

        String loggedInUserId = (currentUser != null) ? currentUser.getUid() : null;
        postAdapter = new PostAdapter(
                this,
                userPostList,
                glide,
                loggedInUserId,
                true, // Profil oldalon MINDIG mutatjuk az opciókat (de csak saját posztnál jelenik meg)
                this
        );
        recyclerViewUserPosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewUserPosts.setAdapter(postAdapter);
    }

    // Betölti a profilhoz tartozó felhasználó adatait (név, kép, követők)
    private void loadUserData() {
        progressBarProfile.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading user data for UID: " + profileUserId);

        db.collection("users").document(profileUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBarProfile.setVisibility(View.GONE); // Töltés kész (részben)
                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "User document data: " + documentSnapshot.getData());
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            Log.d(TAG, "Parsed User displayName: " + user.getDisplayName());
                            if (textViewDisplayName != null) { // Null check a TextView-ra
                                //  User objektumból kiolvasott nevet
                                textViewDisplayName.setText(user.getDisplayName() != null ? user.getDisplayName() : getString(R.string.default_display_name));
                            } else {
                                Log.e(TAG, "textViewDisplayName is null!");
                            }


                            int followers = (user.getFollowers() != null) ? user.getFollowers().size() : 0;
                            int following = (user.getFollowing() != null) ? user.getFollowing().size() : 0;
                            textViewFollowersCount.setText(getString(R.string.followers_count, followers));
                            textViewFollowingCount.setText(getString(R.string.following_count, following));

                            // Profilkép betöltése Glide-dal
                            if (user.getPhotoURL() != null) {
                                glide.load(user.getPhotoURL())
                                        .placeholder(R.drawable.avatar) // Placeholder
                                        .error(R.drawable.avatar)       // Hiba placeholder
                                        .into(profileImageView);
                            } else {
                                profileImageView.setImageResource(R.drawable.avatar);
                            }

                            // Gomb szövegének és állapotának beállítása
                            updateFollowEditButton();

                        } else {
                            Log.w(TAG, "User data could not be parsed for UID: " + profileUserId);
                            Toast.makeText(this, "Hiba a profiladatok feldolgozásakor.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "User document not found for UID: " + profileUserId);
                        Toast.makeText(this, "Felhasználó nem található.", Toast.LENGTH_SHORT).show();
                        // finish(); // Opcionálisan bezárhatjuk az activity-t
                    }
                })
                .addOnFailureListener(e -> {
                    progressBarProfile.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading user data for UID: " + profileUserId, e);
                    Toast.makeText(this, "Hiba a profil betöltésekor.", Toast.LENGTH_SHORT).show();
                });
    }

    // Ellenőrzi, hogy a bejelentkezett user követi-e a profiltulajdonost
    private void checkIfFollowing() {
        if (currentUser == null) return;
        isLoadingFollowStatus = true;
        updateFollowEditButton();

        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User loggedInUser = documentSnapshot.toObject(User.class);
                        if (loggedInUser != null && loggedInUser.getFollowing() != null) {
                            isFollowing = loggedInUser.getFollowing().contains(profileUserId);
                        } else {
                            isFollowing = false; // Ha nincs following lista, nem követi
                        }
                    } else {
                        isFollowing = false; // Ha a bejelentkezett user doksija nincs meg, nem követi
                    }
                    isLoadingFollowStatus = false;
                    updateFollowEditButton(); // Frissítsük a gombot a betöltött állapottal
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error checking follow status", e);
                    isLoadingFollowStatus = false;
                    isFollowing = false; // Hiba esetén feltételezzük, hogy nem követi
                    updateFollowEditButton(); // Frissítsük a gombot
                });
    }

    // Betölti a felhasználó posztjait
    private void loadUserPosts() {
        // Csak a profilhoz tartozó user posztjai kellenek
        Query query = db.collection("posts")
                .whereEqualTo("authorUid", profileUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        // Ha nem saját profil, csak a publikus posztokat mutatjuk
        if (!isOwnProfile) {
            query = query.whereEqualTo("isPublic", true);
        }

        postsListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed for user posts.", e);
                // Opcionális: Hibaüzenet
                return;
            }

            if (snapshots != null) {
                userPostList.clear();
                userPostList.addAll(snapshots.toObjects(Post.class));
                postAdapter.notifyDataSetChanged(); // Vagy DiffUtil használata
                Log.d(TAG, "User posts loaded/updated: " + userPostList.size());

            }
        });
    }
// ProfileActivity.java

    private void setupAdapter() {
        // Csak akkor hozzuk létre, ha még nincs
        if (postAdapter == null) {
            String loggedInUserId = (currentUser != null) ? currentUser.getUid() : null;
            glide = Glide.with(this);

            postAdapter = new PostAdapter(
                    this,
                    userPostList, // Az Activity listája
                    glide,
                    loggedInUserId, // Bejelentkezett user ID-ja
                    true,
                    this     // Az Activity kezeli a kattintásokat
            );
            recyclerViewUserPosts.setAdapter(postAdapter); // Adapter beállítása
            Log.d(TAG, "Adapter setup complete.");
        } else {
            Log.d(TAG, "Adapter already exists.");
        }
    }
    // Kezeli a Követés/Mégse/Szerkesztés gomb kattintását
    private void handleFollowEditButtonClick() {
        Log.d(TAG, "handleFollowEditButtonClick called. isOwnProfile: " + isOwnProfile); // Logolás
        if (isOwnProfile) {
            // Saját profil: Szerkesztés oldal indítása
            Log.d(TAG, "Starting EditProfileActivity..."); // Logolás
            Intent intent = new Intent(this, EditProfileActivity.class); // Helyes Activity név?
            startActivity(intent);
        } else {
            // Másik profil: Követés / Követés megszüntetése
            if (currentUser == null) {
                Toast.makeText(this, "A követéshez be kell jelentkezni.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isLoadingFollowStatus) {
                toggleFollow();
            }
        }
    }

    // Frissíti a Követés/Szerkesztés gomb kinézetét és állapotát
    private void updateFollowEditButton() {
        if (isOwnProfile) {
            buttonFollowEdit.setText(R.string.button_edit_profile);
            buttonFollowEdit.setEnabled(true); // Mindig engedélyezett
        } else {
            if (isLoadingFollowStatus) {
                buttonFollowEdit.setText(R.string.button_loading);
                buttonFollowEdit.setEnabled(false); // Letiltjuk töltés közben
            } else {
                buttonFollowEdit.setText(isFollowing ? R.string.button_unfollow : R.string.button_follow);
                buttonFollowEdit.setEnabled(true); // Engedélyezzük, ha betöltött
            }
        }
    }
    private void playAudio(String url) {
        if (url == null || url.isEmpty()) return;
        Log.d(TAG, "playAudio called with URL: " + url);
        initializePlayer();

        if (exoPlayer.isPlaying()) {
            exoPlayer.stop();
            if(currentlyPlayingUrl != null && !url.equals(currentlyPlayingUrl)){
                updatePlayButtonState(currentlyPlayingUrl, false);
            }
        }

        currentlyPlayingUrl = url;
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
        Toast.makeText(this, "Audio lejátszása...", Toast.LENGTH_SHORT).show();
        updatePlayButtonState(currentlyPlayingUrl, true);
    }
    private void initializePlayer() {
        if (exoPlayer == null) {
            Log.d(TAG, "Initializing ExoPlayer");
            exoPlayer = new ExoPlayer.Builder(this).build();
            playerListener = new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Log.d(TAG, "ExoPlayer onIsPlayingChanged: " + isPlaying);
                    if (!isPlaying) {
                        if (currentlyPlayingUrl != null) {
                            updatePlayButtonState(currentlyPlayingUrl, false);
                            currentlyPlayingUrl = null;
                        }
                    }
                }
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "ExoPlayer onPlaybackStateChanged: " + playbackState);
                    // Itt nem szükséges teendő a gomb frissítéséhez, az onIsPlayingChanged kezeli
                }
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error: ", error);
                    Toast.makeText(getApplicationContext(), "Hiba az audio lejátszásakor", Toast.LENGTH_SHORT).show();
                    if (currentlyPlayingUrl != null) {
                        updatePlayButtonState(currentlyPlayingUrl, false);
                        currentlyPlayingUrl = null;
                    }
                }
            };
            exoPlayer.addListener(playerListener);
        }
    }

    // Követés / Követés megszüntetése művelet
    private void toggleFollow() {
        if (currentUser == null || isOwnProfile) return;

        isLoadingFollowStatus = true;
        updateFollowEditButton();

        DocumentReference currentUserRef = db.collection("users").document(currentUser.getUid());
        DocumentReference profileUserRef = db.collection("users").document(profileUserId);

        // WriteBatch használata az atomi művelethez (mindkét doksit egyszerre frissítjük)
        WriteBatch batch = db.batch();

        FieldValue updateValueFollowing = isFollowing ? FieldValue.arrayRemove(profileUserId) : FieldValue.arrayUnion(profileUserId);
        FieldValue updateValueFollowers = isFollowing ? FieldValue.arrayRemove(currentUser.getUid()) : FieldValue.arrayUnion(currentUser.getUid());

        // Aktuális user 'following' listájának frissítése
        batch.update(currentUserRef, "following", updateValueFollowing);
        // Profil user 'followers' listájának frissítése
        batch.update(profileUserRef, "followers", updateValueFollowers);

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Follow status updated successfully.");
                    isFollowing = !isFollowing;
                    isLoadingFollowStatus = false;

                    updateFollowEditButton();

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating follow status", e);
                    Toast.makeText(this, "Hiba történt a követés állapotának frissítésekor.", Toast.LENGTH_SHORT).show();
                    isLoadingFollowStatus = false;
                    // Visszaállítjuk a gombot az eredeti állapotba (mivel a művelet nem sikerült)
                    updateFollowEditButton();
                });
    }


    // --- OnPostInteractionListener implementáció ---

    @Override
    public void onAuthorClick(String authorUid) {
        // Profil oldalon a szerzőre kattintva nem csinálunk semmit (már itt vagyunk)
    }

    @Override
    public void onPostClick(Post post) {

        Log.d(TAG, "Post clicked on profile: " + post.getId());
    }
    private void stopAudio() {
        Log.d(TAG, "stopAudio called");
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.stop(); // Megállítja és reseteli az állapotot

        }

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

    @Override
    public void onEditClick(Post post) {
        // Csak saját profilon, saját posztnál van értelme
        if (isOwnProfile && currentUser != null && currentUser.getUid().equals(post.getAuthorUid())) {
            Intent intent = new Intent(this, AddEditPostActivity.class);
            intent.putExtra("POST_ID", post.getId()); // Átadjuk a poszt ID-t szerkesztésre
            startActivity(intent);
        } else {
            Log.w(TAG, "Edit clicked on non-own post or profile.");
        }
    }


    @Override
    public void onDeleteClick(Post post) {
        // Csak saját profilon, saját posztnál van értelme
        if (isOwnProfile && currentUser != null && currentUser.getUid().equals(post.getAuthorUid())) {
            showDeleteConfirmationDialog(post); // Törlés megerősítése
        } else {
            Log.w(TAG, "Delete clicked on non-own post or profile.");
        }
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Kezeljük a vissza (Up) gombot a toolbaron
        if (item.getItemId() == android.R.id.home) {

            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void updatePlayButtonState(String url, boolean isPlaying) {
        if (postAdapter == null || recyclerViewUserPosts == null || url == null) return;
        Log.d(TAG, "Updating button state for URL: " + url + " to isPlaying: " + isPlaying);

        for (int i = 0; i < userPostList.size(); i++) {
            Post post = userPostList.get(i);
            // Biztonsági ellenőrzés, hátha az audioUrl null a listában
            if (post.getAudioUrl() != null && url.equals(post.getAudioUrl())) {
                PostAdapter.PostViewHolder holder = (PostAdapter.PostViewHolder) recyclerViewUserPosts.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.buttonPlayAudio != null) {
                    Log.d(TAG, "Found ViewHolder for position: " + i + ", updating button.");

                    com.google.android.material.button.MaterialButton materialButton = holder.buttonPlayAudio;
                    if (isPlaying) {
                        materialButton.setText(R.string.stop_audio);
                        materialButton.setIconResource(R.drawable.ic_stop);
                        materialButton.setOnClickListener(v -> stopAudio());
                    } else {
                        materialButton.setText(R.string.play_audio);
                        materialButton.setIconResource(R.drawable.ic_play_arrow);
                        final String audioUrl = post.getAudioUrl();
                        materialButton.setOnClickListener(v -> playAudio(audioUrl));
                    }
                } else {
                    Log.w(TAG, "ViewHolder or buttonPlayAudio is null for position: " + i);
                }
                break; // Kilépünk, ha megtaláltuk
            }
        }
    }
    // --- Poszt Törlése ---
    private void showDeleteConfirmationDialog(Post post) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                // Használjunk általános üzenetet, mert a Post modellben nincs title
                .setMessage(R.string.dialog_delete_message_generic)
                .setPositiveButton(R.string.dialog_delete_positive, (dialog, which) -> {
                    deletePost(post); // Indítjuk a törlési folyamatot
                })
                .setNegativeButton(R.string.dialog_delete_negative, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deletePost(Post post) {
        if (post == null || post.getId() == null) {
            Log.w(TAG, "Cannot delete post with null data or ID");
            return;
        }
        progressBarProfile.setVisibility(View.VISIBLE);

        String postId = post.getId();
        String imageUrl = post.getImageUrl();
        String audioUrl = post.getAudioUrl();

        // 1. Médiafájlok törlése a Storage-ból (ha vannak)
        List<Task<Void>> deleteTasks = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                StorageReference imageRef = storage.getReferenceFromUrl(imageUrl);
                deleteTasks.add(imageRef.delete());
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid image URL, skipping delete: " + imageUrl, e);
            }
        }
        if (audioUrl != null && !audioUrl.isEmpty()) {
            try {
                StorageReference audioRef = storage.getReferenceFromUrl(audioUrl);
                deleteTasks.add(audioRef.delete());
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid audio URL, skipping delete: " + audioUrl, e);
            }
        }

        // Task, ami a Firestore dokumentumot törli
        Task<Void> deleteDocumentTask = db.collection("posts").document(postId).delete();


        Tasks.whenAll(deleteTasks) // Először a fájlok törlése (ezek mehetnek párhuzamosan)
                .continueWithTask(task -> { // Ha a fájlok törlése kész (vagy nem volt mit törölni)
                    if (!task.isSuccessful()) {
                        // Logoljuk a hibát, de folytatjuk a dokumentum törlésével
                        Log.w(TAG, "Error deleting storage files (continuing with Firestore delete)", task.getException());
                    }
                    // Indítjuk a dokumentum törlését
                    return deleteDocumentTask;
                })
                .addOnCompleteListener(task -> { // Amikor a dokumentum törlése is befejeződött
                    progressBarProfile.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Post and associated files deleted successfully!");
                        Toast.makeText(ProfileActivity.this, R.string.post_deleted_success, Toast.LENGTH_SHORT).show();
                        // A SnapshotListener automatikusan frissíti a RecyclerView-t
                    } else {
                        Log.w(TAG, "Error deleting post document (files might be deleted)", task.getException());
                        Toast.makeText(ProfileActivity.this, R.string.error_deleting_post, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkIfListIsEmpty() {
        if (textViewEmptyList != null && recyclerViewUserPosts != null) { // Ellenőrizzük a view-kat
            if (userPostList.isEmpty()) {
                Log.d(TAG, "Post list is empty, showing empty message.");
                textViewEmptyList.setVisibility(View.VISIBLE);
                recyclerViewUserPosts.setVisibility(View.GONE);
            } else {
                Log.d(TAG, "Post list is NOT empty, showing RecyclerView.");
                textViewEmptyList.setVisibility(View.GONE);
                recyclerViewUserPosts.setVisibility(View.VISIBLE);
            }
        } else {
            Log.w(TAG, "checkIfListIsEmpty: textViewEmptyList or recyclerViewUserPosts is null!");
        }
    }
    // --- Audio Lejátszás (ExoPlayer) ---


    private void releasePlayer() {
        if (exoPlayer != null) {
            // Fontos a listener eltávolítása is, mielőtt elengedjük a playert
            if (playerListener != null) {
                exoPlayer.removeListener(playerListener);
                playerListener = null;
            }
            exoPlayer.release();
            exoPlayer = null;
            Log.d(TAG, "ExoPlayer released.");
            // Ha a lejátszás leállítása nem állította vissza a gombot, itt megtehetnénk
            if (currentlyPlayingUrl != null) {
                updatePlayButtonState(currentlyPlayingUrl, false);
                currentlyPlayingUrl = null;
            }
        }
    }

    // --- Lifecycle metódusok ---

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart called.");

        // Ellenőrizzük, hogy a user még be van-e jelentkezve (elvileg igen)
        currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {

            Log.w(TAG, "User became null in onStart, finishing ProfileActivity.");
            finish();
            return;
        }
        loadUserData();
        loadUserPostsWithGet();
        if(!isOwnProfile){
            checkIfFollowing();
        }
    }

    private void loadUserPostsWithGet() {
        Log.d(TAG, "loadUserPostsWithGet() called");
        progressBarProfile.setVisibility(View.VISIBLE); // Mutassuk a töltést
        if (textViewEmptyList != null) textViewEmptyList.setVisibility(View.GONE);


        setupAdapter(); // Ezt itt is lefuttatjuk, hogy biztosan legyen adapter

        Query query = db.collection("posts")
                .whereEqualTo("authorUid", profileUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (!isOwnProfile) {
            query = query.whereEqualTo("isPublic", true);
        }

        query.get().addOnCompleteListener(task -> { // get() hívás addSnapshotListener helyett
            progressBarProfile.setVisibility(View.GONE); // Töltés vége
            if (task.isSuccessful()) {
                QuerySnapshot snapshots = task.getResult();
                if (snapshots != null) {
                    Log.d(TAG, "Received " + snapshots.size() + " posts with get().");
                    userPostList.clear();
                    userPostList.addAll(snapshots.toObjects(Post.class));
                    if (postAdapter != null) { // Ellenőrizzük, hogy van-e adapter
                        postAdapter.notifyDataSetChanged(); // Frissítjük az adaptert
                    }
                    checkIfListIsEmpty();
                } else {
                    Log.w(TAG, "get() returned null snapshot.");
                    userPostList.clear();
                    if (postAdapter != null) postAdapter.notifyDataSetChanged();
                    checkIfListIsEmpty();
                }
            } else {
                Log.e(TAG, "Error getting user posts with get()", task.getException());
                Toast.makeText(this, "Hiba a posztok betöltésekor.", Toast.LENGTH_SHORT).show();
                userPostList.clear();
                if (postAdapter != null) postAdapter.notifyDataSetChanged();
                checkIfListIsEmpty();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Listener-ek leválasztása a memóriaszivárgás elkerülése végett
        if (postsListener != null) {
            postsListener.remove();
            postsListener = null;
        }
        if (userListener != null) {
            userListener.remove();
        }
        releasePlayer(); // ExoPlayer elengedése
    }

}