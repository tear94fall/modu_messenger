package com.example.modumessenger.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.modumessenger.Activity.ProfileActivity;
import com.example.modumessenger.Activity.SearchActivity;
import com.example.modumessenger.Global.PreferenceManager;
import com.example.modumessenger.R;
import com.example.modumessenger.dto.MemberDto;

import java.util.List;

public class SearchFriendsAdapter extends RecyclerView.Adapter<SearchFriendsAdapter.SearchFriendsViewHolder> {

    List<MemberDto> searchMemberList;

    public SearchFriendsAdapter(List<MemberDto> searchMemberList) { this.searchMemberList = searchMemberList; }

    @NonNull
    @Override
    public SearchFriendsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.friend_search_row, parent, false);
        return new SearchFriendsViewHolder(parent.getContext(), view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchFriendsViewHolder holder, int position) {
        MemberDto member = this.searchMemberList.get(position);

        holder.setUserInfo(member);
        holder.setUserClickEvent(member);
        holder.setAddFriendsButton(member);

        String userId = PreferenceManager.getString("userId");

        searchMemberList.forEach(m -> {
            // exclude my info
            if(userId.equals(member.getUserId())) {
                holder.addFriendsButton.setVisibility(View.INVISIBLE);
            }
            // need to add exclude already friends info
        });
    }

    @Override
    public int getItemCount() {
        return searchMemberList.size();
    }

    public static class SearchFriendsViewHolder extends RecyclerView.ViewHolder {
        Context context;

        TextView username;
        TextView statusMessage;
        ImageView profileImage;
        Button addFriendsButton;
        ConstraintLayout cardViewLayout;

        public SearchFriendsViewHolder(Context context, @NonNull View itemView) {
            super(itemView);

            this.context = context;
            username = itemView.findViewById(R.id.search_user_name);
            statusMessage = itemView.findViewById(R.id.search_status_message);
            profileImage = itemView.findViewById(R.id.search_profile_image);
            addFriendsButton = itemView.findViewById(R.id.add_friends_button);
            cardViewLayout = itemView.findViewById(R.id.searchFriendCardViewLayout);
        }

        public void setUserInfo(MemberDto member) {
            this.username.setText(member.getUsername());
            this.statusMessage.setText(member.getStatusMessage());
            Glide.with(profileImage)
                    .load(member.getProfileImage())
                    .error(Glide.with(profileImage)
                            .load(R.drawable.basic_profile_image)
                            .into(profileImage))
                    .into(profileImage);
        }

        public void setUserClickEvent(MemberDto member) {
            this.cardViewLayout.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), ProfileActivity.class);
                intent.putExtra("email", member.getEmail());
                intent.putExtra("userId", member.getUserId());
                intent.putExtra("username", member.getUsername());
                intent.putExtra("statusMessage", member.getStatusMessage());
                intent.putExtra("profileImage", member.getProfileImage());

                v.getContext().startActivity(intent);
            });
        }

        public void setAddFriendsButton(MemberDto member) {
            this.addFriendsButton.setOnClickListener(view -> {
                ((SearchActivity) this.context).addFriendByEmail(member);
                this.addFriendsButton.setVisibility(View.INVISIBLE);
            });
        }
    }
}
