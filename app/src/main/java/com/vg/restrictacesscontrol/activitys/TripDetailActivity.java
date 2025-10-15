package com.vg.restrictacesscontrol.activitys;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import android.view.View;
import androidx.activity.ComponentActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.adapter.TripDetailPagerAdapter;

public class TripDetailActivity  extends AppCompatActivity
{

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        toolbar = findViewById(R.id.topAppBar);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        toolbar.setNavigationOnClickListener(v -> {
            Intent i = new Intent(this, ScanActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        // Lấy dữ liệu QR để chuyền cho Tab 1
        String raw = getIntent().getStringExtra("qr_raw");
        if (raw == null) raw = "";

        TripDetailPagerAdapter adapter = new TripDetailPagerAdapter((FragmentActivity) this, raw);
        viewPager.setAdapter(adapter);

        final String[] tabs = getResources().getStringArray(R.array.main_tabs);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabs[position])
        ).attach();

        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(this, R.color.primaryColor)); // <-- thêm dòng này

        tabLayout.post(() -> {
            int tabCount = tabLayout.getTabCount();
            int tabWidth = tabLayout.getWidth() / Math.max(tabCount, 1);

            for (int i = 0; i < tabCount; i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null && tab.view != null) {
                    tab.view.setMinimumWidth(tabWidth);
                    tab.view.getLayoutParams().width = tabWidth;

                    TextView tv = new TextView(this);
                    tv.setText(tab.getText());
                    tv.setTextSize(25);
                    tv.setAllCaps(true);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setGravity(Gravity.CENTER);
                    tv.setTextColor(ContextCompat.getColorStateList(this, R.color.tab_text_color)); // selector

                    tab.setCustomView(tv);
                }
            }

            // Hiệu ứng đổi màu khi chọn tab
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    android.view.View custom = tab.getCustomView();
                    if (custom instanceof TextView) {
                        ((TextView) custom).setTextColor(ContextCompat.getColor(TripDetailActivity.this, R.color.primaryColor));
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                    android.view.View custom = tab.getCustomView();
                    if (custom instanceof TextView) {
                        ((TextView) custom).setTextColor(ContextCompat.getColor(TripDetailActivity.this, android.R.color.black));
                    }
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) { }
            });
        });



    }
}
