package com.example.musicianblogapp; // Vagy a te csomagneved

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

public class Utils {

    // Metódus a fájlnév kinyeréséhez URI-ból
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                // Hiba kezelése, pl. logolás
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        // Ha még mindig nincs név, adjunk valami alapértelmezettet
        return result != null ? result : "unknown_file";
    }

    // Metódus a fájlkiterjesztés kinyeréséhez URI-ból
    public static String getFileExtension(Context context, Uri uri) {
        String extension = null;
        // Próbálkozás ContentResolverrel
        if (uri.getScheme() != null && uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            final MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(context.getContentResolver().getType(uri));
        } else {
            // Próbálkozás a fájlnévből (ha van)
            String path = uri.getPath();
            if (path != null) {
                int lastDot = path.lastIndexOf(".");
                if (lastDot >= 0) {
                    extension = path.substring(lastDot + 1);
                }
            }
        }
        // Ha nem sikerült, null-t adunk vissza
        return extension;
    }
}