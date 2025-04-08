package com.example.musicianblogapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

public class Post {

    @DocumentId
    private String id;

    private String authorUid;          // A posztoló felhasználó UID-ja
    private String authorDisplayName;  // A posztoló neve (denormalizált)
    private String authorPhotoURL;     // A posztoló profilkép URL-je (denormalizált, lehet null)
    private boolean isPublic;          // Láthatóság
    private String imageUrl;           // Feltöltött kép URL-je (lehet null)
    private String audioUrl;           // Feltöltött audio URL-je (lehet null)

    private String content;            // Tartalom (korábbi 'content')
    @ServerTimestamp                   // Ezt használjuk a szerver oldali időbélyeghez
    private Timestamp createdAt;

    private String title;
    public Post() {}

    /*public Post(String userId, String title, String content) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        // Timestamp is set by Firestore via @ServerTimestamp
    }*/

    public String getId() { return id; }
    public String getAuthorUid() { return authorUid; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public String getAuthorPhotoURL() { return authorPhotoURL; }
    public boolean isPublic() { return isPublic; }
    public String getImageUrl() { return imageUrl; }
    public String getAudioUrl() { return audioUrl; }
    public String getContent() { return content; }
    public Timestamp getCreatedAt() { return createdAt; } // Korábban getTimestamp() volt

    public String getTitle() { return title;}
    // --- Setters (Firestore-nak kellenek) ---
    public void setId(String id) { this.id = id; }
    public void setAuthorUid(String authorUid) { this.authorUid = authorUid; }
    public void setAuthorDisplayName(String authorDisplayName) { this.authorDisplayName = authorDisplayName; }
    public void setAuthorPhotoURL(String authorPhotoURL) { this.authorPhotoURL = authorPhotoURL; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public void setContent(String content) { this.content = content; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public void setTitle(String title) {this.title = title;}
}