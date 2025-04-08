package com.example.musicianblogapp;

import com.google.firebase.firestore.DocumentId;
import java.util.List;

public class User {

    @DocumentId
    private String uid; // Megegyezik a Firebase Auth UID-val
    private String email;
    private String displayName;
    private String photoURL; // Profilkép URL a Storage-ból
    private List<String> following; // Kit követ ez a user (UID lista)
    private List<String> followers; // Ki követi ezt a usert (UID lista)
    private List<String> fcmTokens; // Eszköz tokenek az értesítésekhez

    // Firestore-nak szükséges üres konstruktor
    public User() {}

    // --- Getters ---
    public String getUid() { return uid; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPhotoURL() { return photoURL; }
    public List<String> getFollowing() { return following; }
    public List<String> getFollowers() { return followers; }
    public List<String> getFcmTokens() { return fcmTokens; }

    // --- Setters ---
    public void setUid(String uid) { this.uid = uid; }
    public void setEmail(String email) { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPhotoURL(String photoURL) { this.photoURL = photoURL; }
    public void setFollowing(List<String> following) { this.following = following; }
    public void setFollowers(List<String> followers) { this.followers = followers; }
    public void setFcmTokens(List<String> fcmTokens) { this.fcmTokens = fcmTokens; }
}