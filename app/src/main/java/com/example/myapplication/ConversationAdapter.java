package com.example.myapplication;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.model.Conversation;
import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    private List<Conversation> conversationList;
    private String currentUserEmail;
    private OnConversationClickListener listener;

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    public ConversationAdapter(List<Conversation> conversationList, String currentUserEmail, OnConversationClickListener listener) {
        this.conversationList = new ArrayList<>(conversationList); // Kopya oluştur
        this.currentUserEmail = currentUserEmail;
        this.listener = listener;
    }

    // Listeyi güncellemek için bir metod
    public void updateConversations(List<Conversation> newConversations) {
        this.conversationList.clear();
        this.conversationList.addAll(newConversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_item, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Conversation conversation = conversationList.get(position);

        // Kullanıcı adını göster
        String userName = conversation.getOtherUserName();
        holder.tvUserName.setText(userName != null ? userName : "Bilinmeyen Kullanıcı");

        // Kullanıcı adının ilk harfini yuvarlak alanda göster
        if (userName != null && userName.length() > 0) {
            holder.tvProfileInitial.setText(userName.substring(0, 1).toUpperCase());
        } else {
            holder.tvProfileInitial.setText("?");
        }

        // Son mesajı göster
        holder.tvLastMessage.setText(conversation.getLastMessage() != null ? conversation.getLastMessage() : "");

        // Son mesaj zamanını göster
        Timestamp lastMessageTime = conversation.getLastMessageTime();
        if (lastMessageTime != null) {
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    lastMessageTime.toDate().getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );
            holder.tvLastMessageTime.setText(timeAgo);
        } else {
            holder.tvLastMessageTime.setText(""); // Zaman yoksa boş bırak
        }

        // Sohbet aktif değilse kilit ikonunu göster
        if (!conversation.getIsActive()) {
            holder.ivLock.setVisibility(View.VISIBLE);
        } else {
            holder.ivLock.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onConversationClick(conversation));
    }

    @Override
    public int getItemCount() {
        return conversationList.size();
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        TextView tvProfileInitial; // İlk harfi gösteren TextView
        TextView tvUserName;
        TextView tvLastMessage;
        TextView tvLastMessageTime;
        ImageView ivLock; // Kilit ikonu

        public ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProfileInitial = itemView.findViewById(R.id.tvProfileInitial);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvLastMessageTime = itemView.findViewById(R.id.tvLastMessageTime);
            ivLock = itemView.findViewById(R.id.ivLock); // Kilit ikonunu bağla
        }
    }
}