package com.example.modumessenger.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.modumessenger.R;

import java.util.List;

public class ProfileImageSliderAdapter extends RecyclerView.Adapter<ProfileImageSliderAdapter.ProfileImageSliderViewHolder> {
    private final List<String> profileImages;

    public ProfileImageSliderAdapter(List<String> profileImageList) {
        this.profileImages = profileImageList;
    }

    @NonNull
    @Override
    public ProfileImageSliderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.profile_image_item_slider, parent, false);
        return new ProfileImageSliderViewHolder(parent.getContext(), view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileImageSliderViewHolder holder, int position) {
        holder.bindSliderImage(profileImages.get(position));
    }

    @Override
    public int getItemCount() {
        return profileImages.size();
    }

    public static class ProfileImageSliderViewHolder extends RecyclerView.ViewHolder {
        Context context;
        ImageView profileImage;

        public ProfileImageSliderViewHolder(Context context, @NonNull View itemView) {
            super(itemView);

            this.context = context;
            this.profileImage = itemView.findViewById(R.id.imageSlider);
        }

        public void bindSliderImage(String imageURL) {
            Glide.with(context)
                    .load(imageURL)
                    .error(Glide.with(context)
                            .load(R.drawable.basic_profile_image)
                            .into(profileImage))
                    .into(profileImage);
        }
    }
}
