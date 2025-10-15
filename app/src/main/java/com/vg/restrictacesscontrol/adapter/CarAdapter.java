package com.vg.restrictacesscontrol.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.models.Car;

import java.util.List;

public class CarAdapter extends RecyclerView.Adapter<CarAdapter.VH> {

    public interface OnCarSelected {
        void onSelected(Car car, int position);
    }

    private final Context ctx;
    private final List<Car> items;
    private int selected = RecyclerView.NO_POSITION;
    private final OnCarSelected callback;

    public CarAdapter(Context ctx, List<Car> items, OnCarSelected callback) {
        this.ctx = ctx;
        this.items = items;
        this.callback = callback;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_car_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Car c = items.get(pos);

        // ✅ Sửa lại field cho đúng với model
        h.tvLic.setText(c.lic_no);
        h.tvName.setText(c.name);
        Glide.with(ctx).load(c.getThumbUrl()).into(h.img);  // dùng method getThumbUrl()

        boolean isSelected = pos == selected;
        ((MaterialCardView) h.itemView).setStrokeColor(
                ctx.getColor(isSelected
                        ? com.google.android.material.R.color.design_default_color_primary
                        : android.R.color.transparent));
        ((MaterialCardView) h.itemView).setStrokeWidth(isSelected ? 4 : 2);

        h.itemView.setOnClickListener(v -> {
            int old = selected;
            selected = h.getBindingAdapterPosition();
            notifyItemChanged(old);
            notifyItemChanged(selected);
            if (callback != null) callback.onSelected(c, selected);
        });
    }


    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView tvLic, tvName;
        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.imgThumb);
            tvLic = v.findViewById(R.id.tvLic);
            tvName = v.findViewById(R.id.tvName);
        }
    }
}
