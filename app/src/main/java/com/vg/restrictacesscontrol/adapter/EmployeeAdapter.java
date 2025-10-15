package com.vg.restrictacesscontrol.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.models.Employee;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmployeeAdapter extends RecyclerView.Adapter<EmployeeAdapter.VH> {

    public interface OnExtraRemoveListener {
        void onRemove(String empno, int position);
    }

    private final List<Employee> items = new ArrayList<>();
    private final Set<String> empnosInTrip = new HashSet<>();
    private final Set<String> verifiedSet = new HashSet<>();
    private boolean showVerificationColors = false;
    private OnExtraRemoveListener removeListener;

    public void setOnExtraRemoveListener(OnExtraRemoveListener l) {
        this.removeListener = l;
    }

    /** Nạp danh sách từ chuyến đi */
    public void submit(List<Employee> list) {
        items.clear();
        empnosInTrip.clear();
        if (list != null) {
            items.addAll(list);
            for (Employee e : list) {
                if (e.empno != null) empnosInTrip.add(e.empno);
            }
        }
        notifyDataSetChanged();
    }

    public void enableVerificationColors(boolean enable) {
        this.showVerificationColors = enable;
        notifyDataSetChanged();
    }

    /** Tích lũy verified IDs thay vì xóa mỗi lần */
    public void addVerified(List<String> ids) {
        if (ids != null) verifiedSet.addAll(ids);
        notifyDataSetChanged();
    }

    public void setVerified(Set<String> ids) {
        verifiedSet.clear();
        if (ids != null) verifiedSet.addAll(ids);
        notifyDataSetChanged();
    }

    public List<Employee> getItems() {
        return new ArrayList<>(items);
    }

    /** Thêm hoặc cập nhật nhân viên mới (ngoài danh sách trip) */
    public void addOrUpdatePeople(List<Employee> newList) {
        if (newList == null || newList.isEmpty()) return;
        for (Employee np : newList) {
            boolean found = false;
            for (int i = 0; i < items.size(); i++) {
                Employee cur = items.get(i);
                if (cur.empno != null && cur.empno.equals(np.empno)) {
                    if ((cur.name == null || cur.name.isEmpty()) && np.name != null) cur.name = np.name;
                    if ((cur.photo == null || cur.photo.isEmpty()) && np.photo != null) cur.photo = np.photo;
                    items.set(i, cur);
                    found = true;
                    break;
                }
            }
            if (!found) items.add(np);
        }
        notifyDataSetChanged();
    }

    public void removeAt(int adapterPosition) {
        if (adapterPosition >= 0 && adapterPosition < items.size()) {
            items.remove(adapterPosition);
            notifyItemRemoved(adapterPosition);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_employee, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Employee e = items.get(pos);

        h.tvEmpno.setText(e.empno != null ? e.empno : "");
        h.tvName.setText(e.name != null ? e.name : "");

        String url;
        if (e.photo != null && !e.photo.isEmpty()) {
            url = e.photo.startsWith("http") ? e.photo : "http://gmo021.cansportsvg.com/api/storage/app/" + e.photo;
        } else {
            url = "http://gmo021.cansportsvg.com/api/pccRfid/publicPhotoByEmpno/" +
                    (e.empno != null ? e.empno : "") + "/CSVN@741236";
        }
        Glide.with(h.img.getContext()).load(url).into(h.img);

        boolean isExtra = (e.empno == null || !empnosInTrip.contains(e.empno));

        if (!showVerificationColors) {
            h.card.setCardBackgroundColor(Color.WHITE);
            h.btnRemove.setVisibility(View.GONE);
        } else {
            if (isExtra) {
                h.card.setCardBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), R.color.red_unverified));
                h.btnRemove.setVisibility(View.VISIBLE);
            } else if (verifiedSet.contains(e.empno)) {
                h.card.setCardBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), R.color.green_verified));
                h.btnRemove.setVisibility(View.GONE);
            } else {
                h.card.setCardBackgroundColor(Color.WHITE);
                h.btnRemove.setVisibility(View.GONE);
            }
        }

        h.btnRemove.setOnClickListener(v -> {
            if (removeListener != null) {
                int adapterPos = h.getBindingAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    removeListener.onRemove(e.empno, adapterPos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView img;
        TextView tvEmpno, tvName;
        ImageButton btnRemove;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardRoot);
            img = itemView.findViewById(R.id.img);
            tvEmpno = itemView.findViewById(R.id.tvEmpno);
            tvName = itemView.findViewById(R.id.tvName);
            btnRemove = itemView.findViewById(R.id.btnRemove); // cần tồn tại trong layout
            if (card != null) card.setStrokeWidth(4);
        }
    }
}
