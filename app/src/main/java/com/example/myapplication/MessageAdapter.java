package com.example.myapplication;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.model.Message;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private List<Message> messages;
    private String currentUserEmail;

    public MessageAdapter(List<Message> messages, String currentUserEmail) {
        this.messages = messages;
        this.currentUserEmail = currentUserEmail != null ? currentUserEmail.toLowerCase(Locale.getDefault()) : null;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.tvMessageContent.setText(message.getMessageContent());

        // Zaman damgasını formatla ve göster
        String timestampStr;
        if (message.getTimestamp() != null) {
            Date date = message.getTimestamp().toDate();
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            timestampStr = dateFormat.format(date);
        } else {
            timestampStr = "Bilinmeyen Zaman";
        }
        holder.tvTimestamp.setText(timestampStr);

        // Gönderen benimse sağa, değilse sola hizala
        String senderEmail = message.getSenderEmail() != null ? message.getSenderEmail().toLowerCase(Locale.getDefault()) : "";
        if (senderEmail.equals(currentUserEmail)) {
            holder.rootContainer.setGravity(Gravity.END);
            holder.messageContainer.setBackgroundResource(R.drawable.message_bubble);
            
            // Gönderilen mesajlardaki metin rengini tema atributundan al
            int sentTextColor = holder.itemView.getContext().getTheme()
                    .obtainStyledAttributes(new int[] { R.attr.messageSentText })
                    .getColor(0, 0xFFFFFFFF);
            holder.tvMessageContent.setTextColor(sentTextColor);
            holder.tvTimestamp.setTextColor(sentTextColor);
            holder.tvTimestamp.setAlpha(0.7f);
        } else {
            holder.rootContainer.setGravity(Gravity.START);
            holder.messageContainer.setBackgroundResource(R.drawable.message_bubble_received);
            
            // Alınan mesajlardaki metin rengini tema atributundan al
            int receivedTextColor = holder.itemView.getContext().getTheme()
                    .obtainStyledAttributes(new int[] { R.attr.messageReceivedText })
                    .getColor(0, 0xFF000000);
            holder.tvMessageContent.setTextColor(receivedTextColor);
            holder.tvTimestamp.setTextColor(receivedTextColor);
            holder.tvTimestamp.setAlpha(0.7f);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessageContent, tvTimestamp;
        LinearLayout messageContainer, rootContainer;

        public ViewHolder(View itemView) {
            super(itemView);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            rootContainer = itemView.findViewById(R.id.rootContainer);
        }
    }
}