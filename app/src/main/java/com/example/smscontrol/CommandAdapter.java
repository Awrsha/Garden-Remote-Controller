package com.example.smscontrol;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.ViewHolder> {

    private List<CommandItem> commands;
    private OnCommandClickListener listener;
    private OnCommandLongClickListener longClickListener;

    public interface OnCommandClickListener {
        void onCommandClick(CommandItem command);
    }

    public interface OnCommandLongClickListener {
        void onCommandLongClick(CommandItem command, int position);
    }

    public CommandAdapter(List<CommandItem> commands, OnCommandClickListener listener) {
        this.commands = commands;
        this.listener = listener;
    }

    public void setOnLongClickListener(OnCommandLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_command, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommandItem command = commands.get(position);
        holder.iconText.setText(command.getIcon());
        holder.labelText.setText(command.getLabel());
        holder.descText.setText(command.getDescription());

        // Disabled state
        if (command.isDisabled()) {
            holder.cardView.setCardBackgroundColor(0xFFEEEEEE);
            holder.iconText.setAlpha(0.5f);
            holder.labelText.setAlpha(0.5f);
            holder.descText.setAlpha(0.5f);
            holder.toggleContainer.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.GONE);
        }
        // Loading state
        else if (command.isLoading()) {
            holder.cardView.setCardBackgroundColor(0xFFFFFFFF);
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.iconText.setAlpha(0.3f);
            holder.toggleContainer.setAlpha(0.5f);
            holder.labelText.setAlpha(0.7f);
            holder.descText.setAlpha(0.5f);
        } else {
            holder.cardView.setCardBackgroundColor(0xFFFFFFFF);
            holder.progressBar.setVisibility(View.GONE);
            holder.iconText.setAlpha(1.0f);
            holder.toggleContainer.setAlpha(1.0f);
            holder.labelText.setAlpha(1.0f);
            holder.descText.setAlpha(1.0f);
        }

        // Toggle visibility and state
        if (command.isToggleable()) {
            holder.toggleContainer.setVisibility(View.VISIBLE);
            updateToggleUI(holder, command.getCurrentState(), false);
        } else {
            holder.toggleContainer.setVisibility(View.GONE);
        }

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && !command.isLoading()) {
                animateClick(holder.cardView);
                listener.onCommandClick(command);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null && !command.isLoading()) {
                animateLongClick(holder.cardView);
                longClickListener.onCommandLongClick(command, position);
                return true;
            }
            return false;
        });
    }

    private void updateToggleUI(ViewHolder holder, boolean isOn, boolean animate) {
        int colorOn = 0xFF34C759;  // iOS green
        int colorOff = 0xFFE9E9EB; // iOS gray
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;

        // Calculate translation: container width (51dp) - circle width (27dp) - padding (4dp) = 20dp
        float targetTranslation = isOn ? 20f * density : 0f;

        if (animate) {
            // Smooth translation animation
            ObjectAnimator translationAnim = ObjectAnimator.ofFloat(
                    holder.toggleCircle,
                    "translationX",
                    targetTranslation
            );
            translationAnim.setDuration(300);
            translationAnim.setInterpolator(new OvershootInterpolator(1.2f));
            translationAnim.start();

            // Color transition animation with rounded corners
            ValueAnimator colorAnim = ValueAnimator.ofObject(
                    new ArgbEvaluator(),
                    isOn ? colorOff : colorOn,
                    isOn ? colorOn : colorOff
            );
            colorAnim.setDuration(300);
            colorAnim.addUpdateListener(animator -> {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(16 * density);
                drawable.setColor((int) animator.getAnimatedValue());
                holder.toggleBackground.setBackground(drawable);
            });
            colorAnim.start();

            // Scale animation for feedback
            holder.toggleCircle.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction(() ->
                            holder.toggleCircle.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(150)
                                    .start()
                    )
                    .start();
        } else {
            holder.toggleCircle.setTranslationX(targetTranslation);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(16 * density);
            drawable.setColor(isOn ? colorOn : colorOff);
            holder.toggleBackground.setBackground(drawable);
        }

        // Optional: Toggle icon
        holder.toggleIcon.setText(isOn ? "☀️" : "🌙");
    }

    private void animateClick(View view) {
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(100)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(new OvershootInterpolator(2f))
                        .start())
                .start();
    }

    private void animateLongClick(View view) {
        view.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(200)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(new OvershootInterpolator(1.5f))
                        .start())
                .start();
    }

    @Override
    public int getItemCount() {
        return commands.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView iconText, labelText, descText, toggleIcon;
        ProgressBar progressBar;
        View toggleContainer, toggleBackground, toggleCircle;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            iconText = itemView.findViewById(R.id.iconText);
            labelText = itemView.findViewById(R.id.labelText);
            descText = itemView.findViewById(R.id.descText);
            progressBar = itemView.findViewById(R.id.progressBar);
            toggleContainer = itemView.findViewById(R.id.toggleContainer);
            toggleBackground = itemView.findViewById(R.id.toggleBackground);
            toggleCircle = itemView.findViewById(R.id.toggleCircle);
            toggleIcon = itemView.findViewById(R.id.toggleIcon);
        }
    }
}