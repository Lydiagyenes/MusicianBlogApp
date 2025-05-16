package com.example.musicianblogapp;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide; // Glide import
import com.bumptech.glide.RequestManager; // Glide RequestManager
import com.google.firebase.Timestamp; // Timestamp import
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import android.text.method.LinkMovementMethod;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import de.hdodenhof.circleimageview.CircleImageView; // CircleImageView import

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
    private final Context context;
    private final Markwon markwon;
    private final List<Post> posts;
    private final RequestManager glide; // Glide hatékony használatához
    private final OnPostInteractionListener listener;
    private final String currentUserId; // Hogy tudjuk, melyik a saját poszt
    private final boolean showOptions; // Megjelenítsük-e a szerk/törlés menüt
    private int lastPosition = -1;
    // Interfész a kattintások kezelésére
    public interface OnPostInteractionListener {
        void onAuthorClick(String authorUid);
        void onPostClick(Post post); // Sima kattintás a kártyára
        void onPlayStopAudioClick(String audioUrl);
        void onEditClick(Post post);
        void onDeleteClick(Post post);
    }

    // Konstruktor frissítve
    public PostAdapter(Context context, List<Post> posts, RequestManager glide, String currentUserId, boolean showOptions, OnPostInteractionListener listener) {
        this.context = context;
        this.posts = posts;
        this.glide = glide; // Glide RequestManager átvétele
        this.currentUserId = currentUserId;
        this.showOptions = showOptions;
        this.listener = listener;
        this.markwon = Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create()) // Add optional plugins
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(context)
                .inflate(R.layout.list_item_post, parent, false);
        return new PostViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post currentPost = posts.get(position);

        // --- Adatok betöltése a nézetekbe ---

        // Szerzői adatok
        holder.textViewAuthorName.setText(currentPost.getAuthorDisplayName() != null ? currentPost.getAuthorDisplayName() : context.getString(R.string.unknown_author));
        if (currentPost.getAuthorPhotoURL() != null) {
            glide.load(currentPost.getAuthorPhotoURL())
                    .placeholder(R.drawable.ic_person) // Placeholder kép
                    .error(R.drawable.ic_person)       // Hiba esetén is placeholder
                    .into(holder.imageViewAuthorPhoto);
        } else {
            holder.imageViewAuthorPhoto.setImageResource(R.drawable.ic_person); // Alapértelmezett ikon
        }



        // Dátum formázása
        if (currentPost.getCreatedAt() != null) {
            // SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault());
            // holder.textViewPostDate.setText(sdf.format(currentPost.getCreatedAt().toDate()));
            // VAGY relatív idő:
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    currentPost.getCreatedAt().toDate().getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            holder.textViewPostDate.setText(relativeTime);
        } else {
            holder.textViewPostDate.setText("");
        }

        if (currentPost.getTitle() != null && !currentPost.getTitle().isEmpty()) {
            holder.textViewPostTitle.setVisibility(View.VISIBLE);
            holder.textViewPostTitle.setText(currentPost.getTitle());
        } else {
            // Ha nincs cím, rejtsük el a TextView-t, hogy ne foglaljon helyet
            holder.textViewPostTitle.setVisibility(View.GONE);
        }
        // Tartalom
        holder.textViewPostContent.setText(currentPost.getContent());

        // Kép (Glide-dal)
        if (currentPost.getImageUrl() != null && !currentPost.getImageUrl().isEmpty()) {
            holder.imageViewPostImage.setVisibility(View.VISIBLE);
            glide.load(currentPost.getImageUrl())
                    .placeholder(R.drawable.image_placeholder) // Placeholder kép betöltéshez
                    .error(R.drawable.image_placeholder_error) // Placeholder hiba esetén
                    .into(holder.imageViewPostImage);
        } else {
            holder.imageViewPostImage.setVisibility(View.GONE);
            glide.clear(holder.imageViewPostImage); // Fontos a kép eltüntetése újrahasznosításkor
        }

        // Audio gomb
        if (currentPost.getAudioUrl() != null && !currentPost.getAudioUrl().isEmpty()) {
            holder.buttonPlayAudio.setVisibility(View.VISIBLE);
            final String audioUrl = currentPost.getAudioUrl(); // final a lambdához

            // Kezdeti állapot beállítása (Lejátszás) - VAGY ellenőrizzük, hogy épp ezt játsszuk-e le


            holder.buttonPlayAudio.setText(R.string.play_audio);
            if (holder.buttonPlayAudio instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) holder.buttonPlayAudio).setIconResource(R.drawable.ic_play_arrow);
            } else {
                // Esetleg setCompoundDrawables, vagy semmi, ha sima Button
            }

            // Kattintáskor meghívjuk az Activity listener metódusát
            holder.buttonPlayAudio.setOnClickListener(v -> {
                if (listener != null) {
                    // Meghívjuk az Activity onPlayStopAudioClick metódusát
                    listener.onPlayStopAudioClick(audioUrl);
                }
            });
        } else {
            holder.buttonPlayAudio.setVisibility(View.GONE);
        }

        TextView textViewPostContent = holder.textViewPostContent;
        String markdownContent = currentPost.getContent();

        if (markdownContent != null && !markdownContent.isEmpty()) {
            textViewPostContent.setVisibility(View.VISIBLE);
            Log.d("PostAdapter", "Setting markdown for pos " + position + ": " + markdownContent);
            markwon.setMarkdown(textViewPostContent, markdownContent);
            Log.d("PostAdapter", "Markdown set for pos " + position);
            textViewPostContent.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            textViewPostContent.setVisibility(View.GONE);
            textViewPostContent.setText("");
        }

        // --- Kattintás események ---
        View.OnClickListener authorClickListener = v -> {
            if (listener != null) {
                listener.onAuthorClick(currentPost.getAuthorUid());
            }
        };
        holder.imageViewAuthorPhoto.setOnClickListener(authorClickListener);
        holder.textViewAuthorName.setOnClickListener(authorClickListener);

        // Teljes kártya kattintás
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPostClick(currentPost);
            }
        });

        boolean isOwnPost = currentUserId != null && currentUserId.equals(currentPost.getAuthorUid());
        Log.d("PostAdapter", "onBindViewHolder - Pos: " + position + ", showOptions: " + showOptions + ", isOwnPost: " + isOwnPost); // Logolás

        if (showOptions && isOwnPost) {
            Log.d("PostAdapter", "Setting menu button VISIBLE for pos: " + position); // Logolás
            if (holder.buttonMenuOptions != null) { // Null check!
                holder.buttonMenuOptions.setVisibility(View.VISIBLE);
                holder.buttonMenuOptions.setOnClickListener(v -> showPopupMenu(holder.buttonMenuOptions, currentPost));
            } else {
                Log.e("PostAdapter", "buttonMenuOptions is NULL in onBindViewHolder for pos: " + position); // Hiba logolása
            }
        } else {
            Log.d("PostAdapter", "Setting menu button GONE for pos: " + position); // Logolás
            if (holder.buttonMenuOptions != null) { // Null check!
                holder.buttonMenuOptions.setVisibility(View.GONE);
            } else {
                // Itt is lehet null, de kevésbé kritikus, ha eleve GONE lenne
            }
        }
        setListItemAnimation(holder.itemView, position);
    }
    @Override
    public void onViewDetachedFromWindow(@NonNull PostViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        // Fontos lehet az animáció törlése, ha a view eltűnik a képernyőről,
        // hogy ne okozzon problémát az újrahasznosításkor.
        holder.itemView.clearAnimation();
    }
    private void setListItemAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(context, R.anim.fade_in);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }
    public void resetAnimation() {
        lastPosition = -1;
    }
    // Popup menü megjelenítése
    private void showPopupMenu(View view, Post post) {
        PopupMenu popup = new PopupMenu(context, view);
        popup.inflate(R.menu.post_options_menu);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit_post) {
                if (listener != null) listener.onEditClick(post);
                return true;
            } else if (id == R.id.action_delete_post) {
                if (listener != null) listener.onDeleteClick(post);
                return true;
            } else {
                return false;
            }
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public void updatePosts(List<Post> newPosts) {

        posts.clear();
        posts.addAll(newPosts);
        notifyDataSetChanged();

    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        final CircleImageView imageViewAuthorPhoto;
        final TextView textViewAuthorName;
        final TextView textViewPostTitle;
        final TextView textViewPostDate;
        final TextView textViewPostContent;
        final ImageView imageViewPostImage;
        final com.google.android.material.button.MaterialButton buttonPlayAudio;
        final ImageButton buttonMenuOptions; // Menü gomb

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewAuthorPhoto = itemView.findViewById(R.id.imageViewAuthorPhoto);
            textViewAuthorName = itemView.findViewById(R.id.textViewAuthorName);
            textViewPostDate = itemView.findViewById(R.id.textViewPostDate);
            textViewPostTitle = itemView.findViewById(R.id.textViewPostTitle);
            textViewPostContent = itemView.findViewById(R.id.textViewPostContent);
            imageViewPostImage = itemView.findViewById(R.id.imageViewPostImage);
            buttonPlayAudio = itemView.findViewById(R.id.buttonPlayAudio);
            buttonMenuOptions = itemView.findViewById(R.id.buttonMenuOptions);

            Log.d("PostViewHolder", "buttonMenuOptions is " + (buttonMenuOptions == null ? "NULL" : "FOUND"));
        }
    }
}