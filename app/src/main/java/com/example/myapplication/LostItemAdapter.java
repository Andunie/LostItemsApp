package com.example.myapplication;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.model.LostItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LostItemAdapter extends RecyclerView.Adapter<LostItemAdapter.ViewHolder> {

    private final List<LostItem> items;
    private final OnItemClickListener onItemClickListener;
    private final Set<String> selectedItems = new HashSet<>();
    private boolean isSelectionMode = false;

    public interface OnItemClickListener {
        void onItemClick(LostItem item);
    }

    public LostItemAdapter(List<LostItem> items, OnItemClickListener onItemClickListener) {
        this.items = items != null ? items : new ArrayList<>();
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lost_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LostItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvAddress.setText(item.getAddress());

        // postedBy durumuna göre UI güncelle
        if ("lost".equals(item.getPostedBy())) {
            holder.tvPostedBy.setText("Lost");
            holder.postedByIndicator.setBackgroundColor(
                    ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_red_light)
            );
        } else if ("found".equals(item.getPostedBy())) {
            holder.tvPostedBy.setText("Found");
            holder.postedByIndicator.setBackgroundColor(
                    ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_green_light)
            );
        } else {
            holder.tvPostedBy.setText("Bilinmeyen");
            holder.postedByIndicator.setBackgroundColor(
                    ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray)
            );
        }

        // Seçili durumu görselleştir
        boolean isSelected = selectedItems.contains(item.getId());
        if (isSelected) {
            holder.rootLayout.setStrokeColor(
                    holder.itemView.getContext().getResources().getColor(R.color.colorPrimary)
            );
            holder.rootLayout.setStrokeWidth(3);
        } else {
            holder.rootLayout.setStrokeColor(
                    holder.itemView.getContext().getResources().getColor(R.color.dividerColor)
            );
            holder.rootLayout.setStrokeWidth(1);
        }

        // Tıklama olayı
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(item, position);
                Log.d("LostItemAdapter", "Item clicked in selection mode: " + item.getId());
            } else {
                onItemClickListener.onItemClick(item);
                Log.d("LostItemAdapter", "Item clicked: " + item.getId());
            }
        });

        // Uzun basma olayı
        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                toggleSelection(item, position);
                Log.d("LostItemAdapter", "Long click, selection mode started: " + item.getId());
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void toggleSelection(LostItem item, int position) {
        String itemId = item.getId();
        if (selectedItems.contains(itemId)) {
            selectedItems.remove(itemId);
            Log.d("LostItemAdapter", "Removed item ID: " + itemId);
        } else {
            selectedItems.add(itemId);
            Log.d("LostItemAdapter", "Added item ID: " + itemId);
        }
        if (selectedItems.isEmpty()) {
            isSelectionMode = false;
            Log.d("LostItemAdapter", "Selection mode disabled");
        }
        notifyItemChanged(position); // Sadece değişen öğeyi güncelle
    }

    public Set<String> getSelectedItems() {
        return selectedItems;
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void clearSelection() {
        selectedItems.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        Log.d("LostItemAdapter", "Selection cleared");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAddress, tvPostedBy;
        LinearLayout postedByIndicator;
        com.google.android.material.card.MaterialCardView rootLayout;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvPostedBy = itemView.findViewById(R.id.tvPostedBy);
            postedByIndicator = itemView.findViewById(R.id.postedByIndicator);
            rootLayout = itemView.findViewById(R.id.root_layout);
        }
    }
}