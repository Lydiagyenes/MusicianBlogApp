package com.example.musicianblogapp;

import android.Manifest; // Engedélyekhez
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager; // Engedély ellenőrzéshez
import android.net.Uri;
import android.os.Build; // API szint ellenőrzéshez
import android.os.Bundle;
import android.provider.MediaStore; // Kamera és audio választáshoz
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem; // Vissza gombhoz (ha van toolbar)
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull; // NonNull import
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog; // Dialógushoz
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar; // Toolbar import (ha használsz)
import androidx.core.content.ContextCompat; // Engedély ellenőrzéshez
import androidx.core.content.FileProvider; // FileProvider használatához

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task; // Task import
import com.google.android.gms.tasks.Tasks; // Tasks import
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File; // File kezeléshez
import java.text.SimpleDateFormat; // Fájlnévhez
import java.util.ArrayList;
import java.util.Date; // Fájlnévhez
import java.util.HashMap;
import java.util.List;
import java.util.Locale; // Locale fájlnévhez
import java.util.Map;
import java.util.Objects; // Objects.requireNonNull
import java.util.UUID;

public class AddEditPostActivity extends AppCompatActivity {

    private static final String TAG = "AddEditPostActivity";

    // --- UI Elemek --- (maradnak)
    private EditText editTextPostContent;
    private Button buttonSavePost, buttonPickImage, buttonPickAudio;
    private ImageView imageViewPreview;
    private TextView textViewSelectedAudio;
    private SwitchCompat switchIsPublic;
    private ProgressBar progressBarAddEdit;
    // private Toolbar toolbarAddEdit; // Ha van toolbar

    // --- Firebase --- (maradnak)
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseUser currentUser;

    // --- Állapot ---
    private String currentPostId = null;
    private Post currentPostData = null;
    private Uri imageUriToUpload = null;
    private Uri selectedAudioUri = null;
    private Uri cameraImageUri = null;
    private String authorDisplayName = null;
    private String authorPhotoUrl = null;
    private boolean isUploading = false;

    // --- Activity Result Launchers ---
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Intent> audioPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_post); // Ehhez új layout kell!

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, R.string.error_not_logged_in, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // UI elemek inicializálása (az új layout alapján)
        editTextPostContent = findViewById(R.id.editTextPostContent);
        buttonSavePost = findViewById(R.id.buttonSavePost);
        buttonPickImage = findViewById(R.id.buttonPickImage);
        buttonPickAudio = findViewById(R.id.buttonPickAudio);
        imageViewPreview = findViewById(R.id.imageViewPreview);
        textViewSelectedAudio = findViewById(R.id.textViewSelectedAudio);
        switchIsPublic = findViewById(R.id.switchIsPublic);
        progressBarAddEdit = findViewById(R.id.progressBarAddEdit);

        setupResultLaunchers(); // ActivityResultLauncher beállítása

        // Felhasználó adatainak (név, kép) előtöltése a denormalizációhoz
        loadCurrentUserInfo();

        // Mód ellenőrzése (Create vs Edit)
        if (getIntent().hasExtra("POST_ID")) {
            currentPostId = getIntent().getStringExtra("POST_ID");
            setTitle(R.string.title_edit_post);
            loadPostDataForEdit();
        } else {
            setTitle(R.string.title_add_post);
            switchIsPublic.setChecked(true); // Új poszt alapból publikus
        }

        ImageButton helpIconButton = findViewById(R.id.buttonMarkdownHelpIcon);
        helpIconButton.setOnClickListener(v -> {
            Intent helpIntent = new Intent(this, MarkdownHelpActivity.class);
            startActivity(helpIntent);
        });
        // Gombok eseménykezelői
        buttonSavePost.setOnClickListener(v -> {
            if (!isUploading) {
                savePost();
            } else {
                Toast.makeText(this, R.string.upload_in_progress, Toast.LENGTH_SHORT).show();
            }
        });
        buttonPickImage.setOnClickListener(v -> /*pickImage()*/ showImagePickDialog());
        buttonPickAudio.setOnClickListener(v -> pickAudio());

        Button boldButton = findViewById(R.id.buttonBold); // Adj ID-t a gombnak a layoutban
        boldButton.setOnClickListener(v -> {
            int start = editTextPostContent.getSelectionStart();
            // Egyszerűen beszúrja a csillagokat és középre helyezi a kurzort
            editTextPostContent.getText().insert(start, "****");
            editTextPostContent.setSelection(start + 2);
        });
    }

    private void setupResultLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedUri = result.getData().getData();
                        if (selectedUri != null) {
                            imageUriToUpload = selectedUri;
                            Log.d(TAG, "Gallery image selected: " + imageUriToUpload);
                            imageViewPreview.setVisibility(View.VISIBLE);
                            Glide.with(this).load(imageUriToUpload).into(imageViewPreview);
                            clearAudioSelection();
                        }
                    }
                });

        // Audio Picker Launcher (standard Android intent)
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (cameraImageUri != null) {
                            imageUriToUpload = cameraImageUri;
                            Log.d(TAG, "Camera image captured: " + imageUriToUpload);
                            imageViewPreview.setVisibility(View.VISIBLE);
                            Glide.with(this).load(imageUriToUpload).into(imageViewPreview);
                            clearAudioSelection();
                        } else {
                            Log.e(TAG,"cameraImageUri was null after camera returned OK");
                            Toast.makeText(this, "Hiba a kép mentésekor.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "Camera capture cancelled or failed.");
                    }
                });

    requestCameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
    isGranted -> {
        if (isGranted) {
            Log.d(TAG, "Camera permission granted");
            launchCameraIntent();
        } else {
            Log.w(TAG, "Camera permission denied");
            Toast.makeText(this, "Kamera engedély szükséges a fotózáshoz.", Toast.LENGTH_SHORT).show();
        }
    });

    // Audio Picker Launcher (marad)
    audioPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
    result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            selectedAudioUri = result.getData().getData();
            if (selectedAudioUri != null) {
                String fileName = Utils.getFileName(this, selectedAudioUri);
                textViewSelectedAudio.setText(getString(R.string.selected_audio_file, fileName));
                textViewSelectedAudio.setVisibility(View.VISIBLE);
                clearImageSelection(); // Ha audiót választ, kép törlődik
            }
        }
    });
}

    // Aktuális felhasználó nevének és képének lekérése
    private void loadCurrentUserInfo() {
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            authorDisplayName = user.getDisplayName();
                            authorPhotoUrl = user.getPhotoURL();
                        } else {
                            Log.w(TAG, "Couldn't parse user data for denormalization.");
                        }
                    } else {
                        Log.w(TAG, "User document not found for denormalization.");
                    }
                    // Ha nincs név/kép, a savePost majd null-t vagy alapértelmezettet használ
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error loading user info for denormalization", e));
    }

    // Poszt adatainak betöltése szerkesztéshez
    private void loadPostDataForEdit() {
        if (currentPostId == null) return;
        progressBarAddEdit.setVisibility(View.VISIBLE);
        db.collection("posts").document(currentPostId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBarAddEdit.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        currentPostData = documentSnapshot.toObject(Post.class);
                        if (currentPostData != null) {
                            editTextPostContent.setText(currentPostData.getContent());
                            switchIsPublic.setChecked(currentPostData.isPublic());

                            EditText editTextPostTitle = findViewById(R.id.editTextPostTitle); // ID ellenőrzése!
                            if (editTextPostTitle != null) { // Null check
                                // Beállítjuk a címet a Post objektumból
                                editTextPostTitle.setText(currentPostData.getTitle() != null ? currentPostData.getTitle() : "");
                            } else {
                                Log.e(TAG,"editTextPostTitle not found in layout!");
                            }
                            if (editTextPostContent != null) { // Null check
                                editTextPostContent.setText(currentPostData.getContent() != null ? currentPostData.getContent() : "");
                            } else {
                                Log.e(TAG,"editTextPostContent not found in layout!");
                            }
                            if (switchIsPublic != null) { // Null check
                                switchIsPublic.setChecked(currentPostData.isPublic());
                            } else {
                                Log.e(TAG,"switchIsPublic not found in layout!");
                            }
                            // Kép előnézet betöltése (ha van)
                            if (currentPostData.getImageUrl() != null) {
                                imageUriToUpload = null;
                                imageViewPreview.setVisibility(View.VISIBLE);
                                Glide.with(this).load(currentPostData.getImageUrl()).into(imageViewPreview);
                            } else {
                                imageViewPreview.setVisibility(View.GONE);
                            }

                            // Audio info (ha van) - URL-ből nevet nem tudunk könnyen kinyerni
                            if (currentPostData.getAudioUrl() != null) {
                                selectedAudioUri = null;
                                textViewSelectedAudio.setText(R.string.existing_audio_attached); // Jelezzük, hogy van régi
                                textViewSelectedAudio.setVisibility(View.VISIBLE);
                            } else {
                                textViewSelectedAudio.setVisibility(View.GONE);
                            }

                        } else {
                            Toast.makeText(this, R.string.error_loading_post_data, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        Toast.makeText(this, R.string.error_post_not_found, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBarAddEdit.setVisibility(View.GONE);
                    Log.w(TAG, "Error loading post for edit", e);
                    Toast.makeText(this, R.string.error_loading_post, Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
    private void showImagePickDialog() {
        final CharSequence[] options = { "Fotózás", "Galéria", "Mégse" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kép választása"); // Vagy adj hozzá stringet: R.string.dialog_pick_image_title
        builder.setItems(options, (dialog, item) -> {
            if (options[item].equals("Fotózás")) {
                checkCameraPermissionAndLaunch();
            } else if (options[item].equals("Galéria")) {
                launchGalleryIntent();
            } else if (options[item].equals("Mégse")) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    // *** ÚJ: Galéria indítása ***
    private void launchGalleryIntent() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(this)
                    .setTitle("Engedély szükséges")
                    .setMessage("A fotózáshoz szükség van a kamera használatának engedélyezésére.")
                    .setPositiveButton("Engedélyezés", (d, w) -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA))
                    .setNegativeButton("Mégse", (d, w) -> d.dismiss())
                    .show();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // *** ÚJ: Kamera indítása ***
    private void launchCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (Exception ex) {
                Log.e(TAG, "Error creating image file", ex);
                Toast.makeText(this, "Hiba a képfájl létrehozásakor.", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                cameraImageUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                cameraLauncher.launch(takePictureIntent);
            }
        } else {
            Toast.makeText(this, "Nincs kamera alkalmazás telepítve.", Toast.LENGTH_SHORT).show();
        }
    }

    // *** ÚJ: Képfájl létrehozása ***
    private File createImageFile() throws java.io.IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = new File(getExternalCacheDir(), "images"); // Kell a file_paths.xml-be is!
        if (!storageDir.exists()){
            if (!storageDir.mkdirs()){
                Log.e(TAG,"failed to create directory");
                throw new java.io.IOException("Failed to create directory for images");
            }
        }
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        Log.d(TAG, "Image file created: " + image.getAbsolutePath());
        return image;
    }

    // Audio választó indítása (marad)
    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        audioPickerLauncher.launch(intent);
    }

    // --- Segédmetódusok a kiválasztások törlésére ---
    private void clearImageSelection() {
        imageUriToUpload = null;
        cameraImageUri = null;
        imageViewPreview.setVisibility(View.GONE);
        Glide.with(this).clear(imageViewPreview); // Glide cache törlése is
    }
    private void clearAudioSelection() {
        selectedAudioUri = null;
        textViewSelectedAudio.setVisibility(View.GONE);
        textViewSelectedAudio.setText("");
    }


    // Poszt mentése (FRISSÍTVE: imageUriToUpload-ot használ)
    private void savePost() {
        EditText editTextPostTitle = findViewById(R.id.editTextPostTitle);
        String title = editTextPostTitle.getText().toString().trim();
        String content = editTextPostContent.getText().toString().trim();
        boolean isPublic = switchIsPublic.isChecked();

        if (TextUtils.isEmpty(title)) {
            editTextPostTitle.setError(getString(R.string.error_field_required));
            editTextPostTitle.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(content) && imageUriToUpload == null && selectedAudioUri == null) {
            // Legalább tartalom, kép vagy hang kelljen
            Toast.makeText(this, R.string.error_post_empty, Toast.LENGTH_SHORT).show(); // Új string kell!
            return;
        }


        progressBarAddEdit.setVisibility(View.VISIBLE);
        isUploading = true;
        buttonSavePost.setEnabled(false);

        uploadFilesAndSaveData(title, content, isPublic); // Indítjuk a feltöltést/mentést
    }


    // Fájlok feltöltése és mentés (FRISSÍTVE: imageUriToUpload-ot használ)
    private void uploadFilesAndSaveData(String title, String content, boolean isPublic) {
        final String postId = (currentPostId != null) ? currentPostId : db.collection("posts").document().getId();
        StorageReference storageRootRef = storage.getReference();

        boolean needsImageUpload = imageUriToUpload != null;
        boolean needsAudioUpload = selectedAudioUri != null;

        UploadTask imageUploadTask = null;
        UploadTask audioUploadTask = null;
        StorageReference imageRef = null;
        StorageReference audioRef = null;

        if (needsImageUpload) {
            imageRef = storageRootRef.child("post_media/" + postId + "/" + UUID.randomUUID().toString() + ".jpg");
            imageUploadTask = imageRef.putFile(imageUriToUpload);
            Log.d(TAG, "Starting image upload for: " + imageUriToUpload);
        }
        if (needsAudioUpload) {
            String extension = Utils.getFileExtension(this, selectedAudioUri);
            audioRef = storageRootRef.child("post_media/" + postId + "/" + UUID.randomUUID().toString() + (extension != null ? "." + extension : ""));
            audioUploadTask = audioRef.putFile(selectedAudioUri);
            Log.d(TAG, "Starting audio upload for: " + selectedAudioUri);
        }

        List<Task<?>> uploadTasks = new ArrayList<>();
        if (imageUploadTask != null) uploadTasks.add(imageUploadTask);
        if (audioUploadTask != null) uploadTasks.add(audioUploadTask);


        if (uploadTasks.isEmpty()) {
            String existingImageUrl = (currentPostData != null && !needsImageUpload) ? currentPostData.getImageUrl() : null;
            String existingAudioUrl = (currentPostData != null && !needsAudioUpload) ? currentPostData.getAudioUrl() : null;
            saveDataToFirestore(postId, title, content, isPublic, existingImageUrl, existingAudioUrl);
            return;
        }

        StorageReference finalImageRef = imageRef; // final a lambdához
        StorageReference finalAudioRef = audioRef; // final a lambdához

        Task<List<Object>> allUploadsTask = Tasks.whenAllSuccess(uploadTasks);

        allUploadsTask.continueWithTask(task -> {
            // Feltöltések sikeresek, most kérjük le az URL-eket
            Task<Uri> imageDownloadUrlTask = needsImageUpload ? finalImageRef.getDownloadUrl() : Tasks.forResult(null);
            Task<Uri> audioDownloadUrlTask = needsAudioUpload ? finalAudioRef.getDownloadUrl() : Tasks.forResult(null);

            // Várjuk meg mindkét URL lekérést
            return Tasks.whenAllSuccess(imageDownloadUrlTask, audioDownloadUrlTask);

        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Object> results = task.getResult();
                Uri imageUrlResult = (Uri) results.get(0); // null, ha nem volt kép feltöltés
                Uri audioUrlResult = (Uri) results.get(1); // null, ha nem volt audio feltöltés

                String imageUrlToSave = (imageUrlResult != null) ? imageUrlResult.toString() : ((currentPostData != null && !needsImageUpload) ? currentPostData.getImageUrl() : null);
                String audioUrlToSave = (audioUrlResult != null) ? audioUrlResult.toString() : ((currentPostData != null && !needsAudioUpload) ? currentPostData.getAudioUrl() : null);

                saveDataToFirestore(postId, title, content, isPublic, imageUrlToSave, audioUrlToSave);
            } else {
                // Hiba a feltöltés vagy URL lekérés során
                handleUploadFailure(task.getException());
            }
        });
    }

    private void saveDataToFirestore(String postId, String title, String content, boolean isPublic, @Nullable String imageUrl, @Nullable String audioUrl) {
        Map<String, Object> postMap = new HashMap<>();
        postMap.put("authorUid", currentUser.getUid());
        // Használjuk az előtöltött nevet/képet, ha van, különben null/alapértelmezett
        postMap.put("authorDisplayName", authorDisplayName != null ? authorDisplayName : currentUser.getEmail().split("@")[0]);
        postMap.put("authorPhotoURL", authorPhotoUrl); // Lehet null
        postMap.put("content", content);
        postMap.put("isPublic", isPublic);
        postMap.put("imageUrl", imageUrl); // Lehet null
        postMap.put("audioUrl", audioUrl); // Lehet null
        postMap.put("title", title);

        Log.d(TAG, "Saving content to Firestore: [" + content + "]");

        postMap.put("content", content);

        DocumentReference postRef = db.collection("posts").document(postId);

        if (currentPostId == null) { // Új poszt létrehozása
            postMap.put("createdAt", FieldValue.serverTimestamp()); // Csak létrehozáskor
            postRef.set(postMap)
                    .addOnSuccessListener(aVoid -> handleSaveSuccess(true))
                    .addOnFailureListener(this::handleSaveFailure);
        } else { // Meglévő poszt frissítése
            // createdAt mezőt nem bántjuk frissítéskor
            postRef.set(postMap, SetOptions.merge()) // Merge! Így a createdAt megmarad
                    .addOnSuccessListener(aVoid -> handleSaveSuccess(false))
                    .addOnFailureListener(this::handleSaveFailure);
        }
    }

    private void handleUploadFailure(Exception e) {
        Log.e(TAG, "Error uploading file", e);
        progressBarAddEdit.setVisibility(View.GONE);
        isUploading = false;
        buttonSavePost.setEnabled(true);
        Toast.makeText(this, getString(R.string.error_uploading_file) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.add_edit_post_menu, menu); // A menüfájl neve
        return true;
    }
    // AddEditPostActivity.java
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_markdown_help) {
            // Indítsuk el a Markdown Súgó Activity-t
            Intent helpIntent = new Intent(this, MarkdownHelpActivity.class);
            startActivity(helpIntent);
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        }
        // Ide jöhet a mentés gomb kezelése is, ha a toolbaron van
        // else if (id == R.id.action_save_post) {
        //     savePost();
        //     return true;
        // }

        return super.onOptionsItemSelected(item);
    }
    private void handleSaveSuccess(boolean created) {
        progressBarAddEdit.setVisibility(View.GONE);
        isUploading = false;
        // Gombot nem kell újra engedélyezni, mert visszalépünk
        Toast.makeText(this, created ? R.string.post_saved_success : R.string.post_updated_success, Toast.LENGTH_SHORT).show();
        // TODO: Cloud Function trigger értesítés küldéséhez, ha public
        finish();
    }

    private void handleSaveFailure(Exception e) {
        Log.w(TAG, "Error saving post data", e);
        progressBarAddEdit.setVisibility(View.GONE);
        isUploading = false;
        buttonSavePost.setEnabled(true);
        Toast.makeText(this, getString(R.string.error_saving_post) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}