package com.example.musicianblogapp;

import android.Manifest; // Engedélyekhez
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager; // Engedély ellenőrzéshez
import android.net.Uri;
import android.os.Build; // API szint ellenőrzéshez
import android.os.Bundle;
import android.provider.MediaStore; // Kamera intenthez
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog; // Dialógushoz
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat; // Engedély ellenőrzéshez
import androidx.core.content.FileProvider; // FileProvider használatához

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File; // File kezeléshez
import java.text.SimpleDateFormat; // Fájlnévhez
import java.util.Date; // Fájlnévhez
import java.util.HashMap;
import java.util.Locale; // Locale fájlnévhez
import java.util.Map;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    // --- UI Elemek --- (maradnak)
    private CircleImageView imageViewEditProfile;
    private ImageButton buttonEditImage;
    private EditText editTextEditDisplayName;
    private Button buttonSaveChanges;
    private ProgressBar progressBarEditProfile;
    private Toolbar toolbarEditProfile;

    // --- Firebase --- (maradnak)
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseUser currentUser;

    // --- Állapot ---
    private Uri imageUriToUpload = null; // Az URI, amit FEL KELL tölteni (lehet galéria vagy kamera kép)
    private Uri cameraImageUri = null; // Az URI, amit a kamera intentnek adunk át
    private String currentPhotoUrl = null; // A JELENLEGI profilkép URL-je
    private boolean isUploading = false;

    // --- Activity Result Launchers ---
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    // Opcionális: Tároló olvasási engedélyhez (általában nem kell GET_CONTENT-hez)
    // private ActivityResultLauncher<String> requestStoragePermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Firebase inicializálás
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, R.string.error_not_logged_in, Toast.LENGTH_SHORT).show();
            finish(); // Ha nincs bejelentkezve, nem szerkeszthet
            return;
        }

        // UI elemek bekötése (activity_edit_profile.xml alapján!)
        imageViewEditProfile = findViewById(R.id.imageViewEditProfile);
        buttonEditImage = findViewById(R.id.buttonEditImage);
        editTextEditDisplayName = findViewById(R.id.editTextEditDisplayName);
        buttonSaveChanges = findViewById(R.id.buttonSaveChanges);
        progressBarEditProfile = findViewById(R.id.progressBarEditProfile);
        toolbarEditProfile = findViewById(R.id.toolbarEditProfile);

        // Toolbar beállítása
        setSupportActionBar(toolbarEditProfile);
        Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.title_edit_profile); // Cím beállítása
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Vissza gomb megjelenítése

        setupResultLaunchers();

        View.OnClickListener pickImageListener = v -> showImagePickDialog(); // Dialógust mutatunk
        buttonEditImage.setOnClickListener(pickImageListener);
        imageViewEditProfile.setOnClickListener(pickImageListener);
        buttonSaveChanges.setOnClickListener(v -> {
            if (!isUploading) {
                saveProfileChanges();
            } else {
                Toast.makeText(this, R.string.upload_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        loadCurrentUserData();
    }

    // Vissza gomb kezelése a toolbaron
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Visszalépés az előző Activity-re
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupResultLaunchers() {
        // Galéria Launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedUri = result.getData().getData();
                        if (selectedUri != null) {
                            imageUriToUpload = selectedUri; // Ezt fogjuk feltölteni
                            Log.d(TAG, "Gallery image selected: " + imageUriToUpload);
                            Glide.with(this).load(imageUriToUpload).into(imageViewEditProfile);
                        }
                    }
                });

        // Kamera Launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Nem kell a result.getData(), mert a képet a cameraImageUri helyre mentettük
                    if (result.getResultCode() == RESULT_OK) {
                        if (cameraImageUri != null) {
                            imageUriToUpload = cameraImageUri; // Ezt fogjuk feltölteni
                            Log.d(TAG, "Camera image captured: " + imageUriToUpload);
                            Glide.with(this).load(imageUriToUpload).into(imageViewEditProfile);
                            // Opcionális: Kép hozzáadása a médiatárhoz (galériához)
                            // galleryAddPic(cameraImageUri);
                        } else {
                            Log.e(TAG, "cameraImageUri was null after camera returned OK");
                            Toast.makeText(this, "Hiba a kép mentésekor.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "Camera capture cancelled or failed.");
                    }
                });

        // Kamera Engedély Kérő Launcher
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Camera permission granted");
                        launchCameraIntent(); // Engedély megadva, indítjuk a kamerát
                    } else {
                        Log.w(TAG, "Camera permission denied");
                        Toast.makeText(this, "Kamera engedély szükséges a fotózáshoz.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadCurrentUserData() {
        progressBarEditProfile.setVisibility(View.VISIBLE);
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBarEditProfile.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            // Név beállítása az EditText-be
                            editTextEditDisplayName.setText(user.getDisplayName() != null ? user.getDisplayName() : "");
                            // Jelenlegi kép URL eltárolása
                            currentPhotoUrl = user.getPhotoURL();
                            // Profilkép betöltése Glide-dal
                            if (currentPhotoUrl != null) {
                                Glide.with(this)
                                        .load(currentPhotoUrl)
                                        .placeholder(R.drawable.avatar)
                                        .error(R.drawable.avatar)
                                        .into(imageViewEditProfile);
                            } else {
                                imageViewEditProfile.setImageResource(R.drawable.avatar);
                            }
                        } else {
                            Toast.makeText(this, R.string.error_loading_user_data, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Ez nem fordulhat elő, ha a RegisterActivity helyesen hozza létre a doksit
                        Toast.makeText(this, R.string.error_user_data_not_found, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBarEditProfile.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading user data", e);
                    Toast.makeText(this, R.string.error_loading_user_data, Toast.LENGTH_SHORT).show();
                });
    }
    private void showImagePickDialog() {
        final CharSequence[] options = { "Fotózás", "Galéria", "Mégse" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Profilkép választása");
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
    private void launchGalleryIntent() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // Vagy: Intent intent = new Intent(Intent.ACTION_GET_CONTENT); intent.setType("image/*");
        galleryLauncher.launch(intent);
    }
    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Engedély már megvan
            launchCameraIntent();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Opcionális: Magyarázat, miért kell az engedély (dialógus)
            new AlertDialog.Builder(this)
                    .setTitle("Engedély szükséges")
                    .setMessage("A fotózáshoz szükség van a kamera használatának engedélyezésére.")
                    .setPositiveButton("Engedélyezés", (d, w) -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA))
                    .setNegativeButton("Mégse", (d, w) -> d.dismiss())
                    .show();
        } else {
            // Engedély kérése közvetlenül
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // Kamera Intent indítása
    private void launchCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Győződjünk meg róla, hogy van olyan Activity, ami kezeli az intentet
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Hozzunk létre egy fájlt a képnek
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (Exception ex) {
                Log.e(TAG, "Error creating image file", ex);
                Toast.makeText(this, "Hiba a képfájl létrehozásakor.", Toast.LENGTH_SHORT).show();
            }
            // Csak akkor folytatjuk, ha a fájl sikeresen létrejött
            if (photoFile != null) {
                // Hozzunk létre egy content:// URI-t a FileProvider segítségével
                cameraImageUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".provider", // Meg kell egyeznie a Manifestben megadott authorities-szal
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                cameraLauncher.launch(takePictureIntent);
            }
        } else {
            Toast.makeText(this, "Nincs kamera alkalmazás telepítve.", Toast.LENGTH_SHORT).show();
        }
    }

    // Üres képfájl létrehozása a kamera számára
    private File createImageFile() throws java.io.IOException {
        // Hozzunk létre egy egyedi fájlnevet
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        // Használjuk az external cache könyvtárat (ahogy a file_paths.xml-ben definiáltuk)
        File storageDir = new File(getExternalCacheDir(), "images"); // Meg kell egyeznie a file_paths path-szal
        if (!storageDir.exists()){
            if (!storageDir.mkdirs()){ // Hozzuk létre a mappát, ha nincs
                Log.e(TAG,"failed to create directory");
                throw new java.io.IOException("Failed to create directory for images");
            }
        }
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        Log.d(TAG, "Image file created: " + image.getAbsolutePath());
        return image;
    }
    private void saveProfileChanges() {
        String newDisplayName = editTextEditDisplayName.getText().toString().trim();
        if (TextUtils.isEmpty(newDisplayName)) { /* ... hiba ... */ return; }

        progressBarEditProfile.setVisibility(View.VISIBLE);
        buttonSaveChanges.setEnabled(false);
        isUploading = true;

        // Ha új képet választott a felhasználó (URI nem null), azt töltjük fel
        if (imageUriToUpload != null) { // *** EZ VÁLTOZOTT ***
            uploadImageAndSaveData(newDisplayName, imageUriToUpload); // *** EZ VÁLTOZOTT ***
        } else {
            // Ha nem választott új képet, csak a Firestore adatokat frissítjük
            // a régi (vagy null) kép URL-lel
            updateFirestoreData(newDisplayName, currentPhotoUrl);
        }
    }

    // Kép feltöltése és mentés (MÓDOSÍTVA: URI-t kap)
    private void uploadImageAndSaveData(String displayName, Uri imageUri) {
        String fileName = "profile_pic.jpg";
        StorageReference profilePicRef = storage.getReference()
                .child("profile_pictures/" + currentUser.getUid() + "/" + fileName);

        profilePicRef.putFile(imageUri) // *** EZ VÁLTOZOTT ***
                .addOnProgressListener(snapshot -> {

                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw Objects.requireNonNull(task.getException()); // Dobjuk tovább a hibát
                    }
                    // Ha a feltöltés sikeres, kérjük le a letöltési URL-t
                    return profilePicRef.getDownloadUrl();
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        updateFirestoreData(displayName, downloadUri.toString());
                    } else {
                        handleUpdateFailure(task.getException());
                    }
                });
    }

    private void updateFirestoreData(String displayName, @Nullable String photoUrl) {
        Log.d(TAG, "Attempting to update Firestore. New displayName: " + displayName + ", New photoUrl: " + photoUrl);
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", displayName);
        updates.put("photoURL", photoUrl); // Lehet null is
        Log.d(TAG, "Data to update in Firestore: " + updates.toString());
        db.collection("users").document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Firestore update successful.");
                    handleUpdateSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore update FAILED.", e); // *** NAGYON FONTOS LOG A HIBÁVAL ***
                    handleUpdateFailure(e);
                });
    }

    // Sikeres mentés kezelése
    private void handleUpdateSuccess() {
        progressBarEditProfile.setVisibility(View.GONE);
        isUploading = false;
        // Gombot nem kell engedélyezni, mert visszalépünk
        Toast.makeText(this, R.string.profile_updated_success, Toast.LENGTH_SHORT).show();
        // Figyelmeztetés a denormalizációról (opcionális)
        // Toast.makeText(this, R.string.denormalization_warning, Toast.LENGTH_LONG).show();
        finish(); // Visszalépés a ProfileActivity-be
    }

    // Sikertelen mentés/feltöltés kezelése
    private void handleUpdateFailure(Exception e) {
        progressBarEditProfile.setVisibility(View.GONE);
        buttonSaveChanges.setEnabled(true);
        isUploading = false;
        Log.e(TAG, "Error updating profile", e);
        Toast.makeText(this, getString(R.string.error_updating_profile) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}