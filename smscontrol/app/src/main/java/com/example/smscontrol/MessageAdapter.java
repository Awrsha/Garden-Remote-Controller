package com.example.smscontrol;

import android.content.res.Configuration;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private List<MessageItem> messages;

    public MessageAdapter(List<MessageItem> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            MessageItem message = messages.get(position);
            holder.contentText.setText(message.getContent());
            holder.timeText.setText(message.getTime());

            boolean isDarkMode = (holder.itemView.getContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.messageContainer.getLayoutParams();

            if (message.getType().equals("SENT")) {
                holder.rootLayout.setGravity(Gravity.END);
                holder.messageContainer.setBackgroundResource(R.drawable.bg_bubble_sent);
                holder.contentText.setTextColor(0xFFFFFFFF);
                holder.timeText.setTextColor(0xFFFFFFFF);
                holder.timeText.setAlpha(0.7f);
                params.setMargins(80, 4, 0, 4);
            } else {
                holder.rootLayout.setGravity(Gravity.START);
                holder.messageContainer.setBackgroundResource(R.drawable.bg_bubble_received);

                if (isDarkMode) {
                    holder.contentText.setTextColor(0xFFFFFFFF);
                    holder.timeText.setTextColor(0xFF8E8E93);
                } else {
                    holder.contentText.setTextColor(0xFF1C1C1E);
                    holder.timeText.setTextColor(0xFF8E8E93);
                }

                params.setMargins(0, 4, 80, 4);
            }

            holder.messageContainer.setLayoutParams(params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView contentText, timeText;
        LinearLayout rootLayout, messageContainer;

        ViewHolder(View itemView) {
            super(itemView);
            rootLayout = (LinearLayout) itemView;
            messageContainer = itemView.findViewById(R.id.messageContainer);
            contentText = itemView.findViewById(R.id.contentText);
            timeText = itemView.findViewById(R.id.timeText);
        }
    }
}