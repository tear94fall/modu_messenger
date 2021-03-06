package com.example.modumessenger.Grid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.modumessenger.Activity.AppInfoActivity;
import com.example.modumessenger.Activity.NotificationActivity;
import com.example.modumessenger.Activity.SetupActivity;
import com.example.modumessenger.R;

import java.util.ArrayList;
import java.util.List;

public class SettingGridAdapter extends BaseAdapter {
    private final Context context;
    private final List<SettingGridItem> settingGridItemList;

    public SettingGridAdapter(Context context) {
        this.context = context;
        this.settingGridItemList = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return this.settingGridItemList.size();
    }

    @Override
    public Object getItem(int position) {
        return this.settingGridItemList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @SuppressLint("ViewHolder")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        convertView = inflater.inflate(R.layout.setting_grid_item, parent, false);

        SettingGridItem settingGridItem = settingGridItemList.get(position);

        TextView itemTextView = convertView.findViewById(R.id.item_text_view);
        itemTextView.setText(settingGridItem.getItemName());

        ImageView itemImageView = convertView.findViewById(R.id.item_image_view);
        Glide.with(this.context)
                .load(settingGridItem.getItemImage())
                .error(Glide.with(this.context)
                        .load(R.drawable.basic_profile_image)
                        .into(itemImageView))
                .into(itemImageView);

        return convertView;
    }

    public void setGridItems(View view) {
        setGridItem(new SettingGridItem(new Intent(view.getContext(), AppInfoActivity.class), "????????????", R.drawable.ic_baseline_info_24));
        setGridItem(new SettingGridItem(new Intent(view.getContext(), NotificationActivity.class), "????????????", R.drawable.ic_baseline_celebration_24));
        setGridItem(new SettingGridItem(null, "??????", R.drawable.ic_baseline_cloud_download_24));
        setGridItem(new SettingGridItem(new Intent(view.getContext(), SetupActivity.class), "??????", R.drawable.ic_baseline_settings_24_grey));
        setGridItem(new SettingGridItem(null, "??????", R.drawable.ic_baseline_color_lens_24));
        setGridItem(new SettingGridItem(null, "????????????", R.drawable.ic_baseline_person_24));
        setGridItem(new SettingGridItem(null, "????????????", R.drawable.ic_baseline_person_outline_24));
        setGridItem(new SettingGridItem(null, "????????????", R.drawable.ic_baseline_person_off_24));
    }

    public void setGridItem(SettingGridItem settingGridItem) {
        this.settingGridItemList.add(settingGridItem);
    }

    public List<SettingGridItem> getGridItems() {
        return this.settingGridItemList;
    }

    public SettingGridItem getGridItem(int index) {
        return this.settingGridItemList.get(index);
    }
}
