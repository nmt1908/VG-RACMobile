package com.vg.restrictacesscontrol.adapter;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.vg.restrictacesscontrol.fragment.TripInfoFragment;
import com.vg.restrictacesscontrol.fragment.SelectCarFragment;

public class TripDetailPagerAdapter extends FragmentStateAdapter {

    private final String qrRaw;

    public TripDetailPagerAdapter(@NonNull FragmentActivity activity, String qrRaw) {
        super(activity);
        this.qrRaw = qrRaw;
    }



    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            TripInfoFragment f = new TripInfoFragment();
            Bundle args = new Bundle();
            args.putString("qr_raw", qrRaw);
            f.setArguments(args);
            return f;
        } else {
            return new SelectCarFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
