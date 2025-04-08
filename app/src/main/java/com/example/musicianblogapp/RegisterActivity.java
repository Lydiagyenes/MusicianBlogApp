package com.example.musicianblogapp;

import android.content.Intent; // Ha vissza akarsz térni LoginActivity-be
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns; // Email validáláshoz
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException; // Specifikus hiba
import com.google.firebase.auth.FirebaseAuthWeakPasswordException; // Specifikus hiba
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList; // Üres listákhoz
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText editTextRegisterName, editTextEmail, editTextPassword, editTextPasswordConfirm;
    private Button buttonRegister;
    private TextView textViewGoToLogin;
    private ProgressBar progressBarRegister;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Feltételezzük, hogy létezik egy activity_register.xml layout
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTextRegisterName = findViewById(R.id.editTextRegisterName);
        editTextEmail = findViewById(R.id.editTextRegisterEmail);
        editTextPassword = findViewById(R.id.editTextRegisterPassword);
        editTextPasswordConfirm = findViewById(R.id.editTextRegisterPasswordConfirm);
        buttonRegister = findViewById(R.id.buttonRegister);
        textViewGoToLogin = findViewById(R.id.textViewGoToLogin);
        progressBarRegister = findViewById(R.id.progressBarRegister);

        buttonRegister.setOnClickListener(v -> registerUser());

        textViewGoToLogin.setOnClickListener(v -> {
            // Visszalépés a LoginActivity-be (feltételezve, hogy onnan indítottuk)
            finish();
            // VAGY explicit indítás, ha máshonnan is elérhető lenne:
            // Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            // startActivity(intent);
            // finish();
        });
    }

    private void registerUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String passwordConfirm = editTextPasswordConfirm.getText().toString().trim();
        String displayName = editTextRegisterName.getText().toString().trim();

        // --- Validáció ---
        if (TextUtils.isEmpty(displayName)) {
            editTextRegisterName.setError(getString(R.string.error_field_required));
            editTextRegisterName.requestFocus();
            return;
        }

        if (!isValidEmail(email)) { // Használjunk jobb email validációt
            editTextEmail.setError(getString(R.string.error_invalid_email));
            editTextEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError(getString(R.string.error_field_required));
            editTextPassword.requestFocus();
            return;
        }
        if (password.length() < 6) { // Firebase alapértelmezett követelmény
            editTextPassword.setError(getString(R.string.error_password_too_short));
            editTextPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(passwordConfirm)) {
            editTextPasswordConfirm.setError(getString(R.string.error_field_required));
            editTextPasswordConfirm.requestFocus();
            return;
        }

        if (!password.equals(passwordConfirm)) {
            editTextPasswordConfirm.setError(getString(R.string.error_passwords_do_not_match));
            editTextPasswordConfirm.requestFocus();
            return;
        }


        // --- Regisztráció indítása ---
        progressBarRegister.setVisibility(View.VISIBLE);
        buttonRegister.setEnabled(false); // Gomb letiltása a folyamat alatt

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sikeres Firebase Auth regisztráció
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        // Fontos: User dokumentum létrehozása Firestore-ban
                        if (firebaseUser != null) {
                            Log.d(TAG, "Creating user document with display name: " + displayName);
                            createNewUserDocument(firebaseUser, email, displayName);
                        } else {

                            Log.e(TAG, "User is null after successful registration!");
                            progressBarRegister.setVisibility(View.GONE);
                            buttonRegister.setEnabled(true);
                            Toast.makeText(RegisterActivity.this, R.string.error_registration_failed_unexpected, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Sikertelen Firebase Auth regisztráció
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        progressBarRegister.setVisibility(View.GONE);
                        buttonRegister.setEnabled(true);
                        // Próbáljunk meg specifikusabb hibaüzenetet adni
                        try {
                            throw task.getException();
                        } catch(FirebaseAuthWeakPasswordException e) {
                            editTextPassword.setError(getString(R.string.error_weak_password));
                            editTextPassword.requestFocus();
                        } catch(FirebaseAuthUserCollisionException e) {
                            editTextEmail.setError(getString(R.string.error_email_already_in_use));
                            editTextEmail.requestFocus();
                        } catch(Exception e) {
                            Toast.makeText(RegisterActivity.this, getString(R.string.error_registration_failed) + ": " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // Segédmetódus az email validáláshoz
    private boolean isValidEmail(CharSequence target) {
        return (!TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches());
    }

    // Létrehozza az új felhasználó dokumentumát a Firestore "users" kollekciójában
    private void createNewUserDocument(FirebaseUser firebaseUser, String email, String displayName) {
        Log.d(TAG, "createNewUserDocument called with email: " + email + ", displayName: " + displayName);

        String uid = firebaseUser.getUid();
        Log.d(TAG, "Attempting to create document for UID: " + uid + " in collection 'users'");

        Map<String, Object> userData = new HashMap<>();
        // userData.put("uid", uid);
        userData.put("email", email);
        userData.put("displayName", displayName);
        userData.put("photoURL", null);
        userData.put("following", new ArrayList<String>());
        userData.put("followers", new ArrayList<String>());
        userData.put("fcmTokens", new ArrayList<String>());
        Log.d(TAG, "Data being saved to Firestore: " + userData.toString());
        db.collection("users").document(uid)
                .set(userData) // set() létrehozza, ha nem létezik
                .addOnSuccessListener(aVoid -> {
                    // Sikeres Firestore dokumentum létrehozás
                    Log.d(TAG, "User document created successfully for UID: " + uid);
                    progressBarRegister.setVisibility(View.GONE);
                    // Itt már nem kell újra engedélyezni a gombot, mert visszalépünk
                    Toast.makeText(RegisterActivity.this, getString(R.string.registration_successful), Toast.LENGTH_SHORT).show();
                    goToLogin(); // Visszatérés a Login Activity-be
                })
                .addOnFailureListener(e -> {
                    // Hiba a Firestore dokumentum létrehozásakor
                    // FONTOS: A felhasználó már regisztrálva van Firebase Auth-ban!
                    Log.w(TAG, "Error creating user document for UID: " + uid, e);
                    progressBarRegister.setVisibility(View.GONE);
                    buttonRegister.setEnabled(true); // Itt újra engedélyezzük, hátha újra próbálja? Vagy csak menjünk vissza.
                    // Értesítjük a usert, de valószínűleg vissza kell lépnie a loginhoz
                    Toast.makeText(RegisterActivity.this, R.string.error_creating_user_profile_partial, Toast.LENGTH_LONG).show();
                    goToLogin(); // A felhasználó regisztrált, de a profilja nem jött létre helyesen -> Login
                });
    }

    // Visszanavigál a LoginActivity-be
    private void goToLogin() {

        finish();

    }
}