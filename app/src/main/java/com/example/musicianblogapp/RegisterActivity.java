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
import android.app.AlarmManager; // Import
import android.app.PendingIntent; // Import
import android.app.TimePickerDialog; // Import
import android.content.Context; // Import
import android.os.Build; // Import
import java.util.Calendar; // Import
import java.util.Locale; // Import

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
    private Button buttonPickReminderTime;
    private TextView textViewReminderLabel;
    private int reminderHour = -1; // Kezdetben nincs kiválasztva
    private int reminderMinute = -1;
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
        buttonPickReminderTime = findViewById(R.id.buttonPickReminderTime);
        textViewReminderLabel = findViewById(R.id.textViewReminderLabel);
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
        updateReminderButtonText();
        buttonPickReminderTime.setOnClickListener(v -> showTimePickerDialog());
        textViewGoToLogin.setOnClickListener(v -> {
            // Visszalépés a LoginActivity-be (feltételezve, hogy onnan indítottuk)
            finish();
            // VAGY explicit indítás, ha máshonnan is elérhető lenne:
            // Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            // startActivity(intent);
            // finish();
        });
    }

    private void showTimePickerDialog() {
        Calendar currentTime = Calendar.getInstance();
        int hour = (reminderHour != -1) ? reminderHour : currentTime.get(Calendar.HOUR_OF_DAY);
        int minute = (reminderMinute != -1) ? reminderMinute : currentTime.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, selectedHour, selectedMinute) -> {
                    reminderHour = selectedHour;
                    reminderMinute = selectedMinute;
                    updateReminderButtonText();
                    Log.d(TAG, "Reminder time set to: " + reminderHour + ":" + reminderMinute);
                }, hour, minute, true // true for 24-hour view
        );
        timePickerDialog.setTitle("Emlékeztető ideje");
        timePickerDialog.show();

    }

    private void updateReminderButtonText() {
        if (reminderHour != -1 && reminderMinute != -1) {
            buttonPickReminderTime.setText(String.format(Locale.getDefault(), "%02d:%02d", reminderHour, reminderMinute));
        } else {
            buttonPickReminderTime.setText("Időpont választása (alapért. 3 perc múlva)"); // Vagy string erőforrás
        }

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
                    setupDailyReminder(this, reminderHour, reminderMinute);

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

    public static void setupDailyReminder(Context context, int selectedHour, int selectedMinute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);

        PendingIntent pendingIntent;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags); // Request code legyen egyedi

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        if (selectedHour != -1 && selectedMinute != -1) {
            // Felhasználó által választott időpont
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
            calendar.set(Calendar.MINUTE, selectedMinute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // Ha a mai választott időpont már elmúlt, a következő napra állítjuk
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
            Log.d(TAG, "Setting daily reminder for user selected time: " + selectedHour + ":" + selectedMinute);
        } else {
            // Alapértelmezett: 3 perc múlva (csak az első alkalommal)
            // Ezt az ismétlődő alarm nem tudja jól kezelni, ha minden nap 3 perccel később lenne.
            // Ezért az ELSŐ értesítést állítjuk be 3 percre, és a NAPI ismétlődőt egy fix időpontra.
            // VAGY: Ha nincs választott idő, legyen egy fix alapértelmezett napi idő, pl. reggel 9.

            // Mostani egyszerűsítés: Ha nincs választva, az első 3 perc múlva,
            // de az ismétlődés (INTERVAL_DAY) mindig a 3 perccel későbbi időponthoz képest lesz.
            // Ez nem ideális napi ismétlődéshez.

            // JOBB MEGOLDÁS ALAPÉRTELMEZÉSRE: Fix napi időpont, pl. reggel 9, ha nem választott.
            Log.d(TAG, "No reminder time selected by user. Setting default to 9 AM for daily repeat.");
            calendar.set(Calendar.HOUR_OF_DAY, 9); // Alapértelmezett 9:00
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        // Ismétlődő alarm beállítása (napi)
        // FIGYELEM: Az setInexactRepeating energiatakarékos, de nem garantálja a pontos időzítést.
        // Pontosabbhoz setRepeating vagy setExactAndAllowWhileIdle + újraütemezés kellene.
        if (alarmManager != null) {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, // Napi ismétlődés
                    pendingIntent);
            Log.d(TAG, "Daily reminder alarm set for: " + calendar.getTime().toString());
        } else {
            Log.e(TAG, "AlarmManager is null, cannot set reminder.");
        }
    }
    // Visszanavigál a LoginActivity-be
    private void goToLogin() {

        finish();

    }
}