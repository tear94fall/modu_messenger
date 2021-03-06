package com.example.modumessenger.Activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.modumessenger.Global.OnSwipeListener;
import com.example.modumessenger.Global.PreferenceManager;
import com.example.modumessenger.R;
import com.example.modumessenger.Retrofit.RetrofitClient;
import com.example.modumessenger.dto.ChatRoomDto;
import com.example.modumessenger.dto.MemberDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    ImageView profileImageView;
    TextView usernameTextView, statusMessageTextView;
    Button profileEditButton, profileCloseButton, startChatButton;
    String email, userId, username, statusMessage, profileImage;
    GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        setGestureDetector();
        bindingView();
        getData();
        setData();
        setButtonClickEvent();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(username.equals(PreferenceManager.getString("username"))){
            getMyProfileInfo(new MemberDto(PreferenceManager.getString("userId"), PreferenceManager.getString("email")));
        }
    }

    private void setGestureDetector() {
        gestureDetector = new GestureDetector(this,new OnSwipeListener(){
            @Override
            public boolean onSwipe(Direction direction) {
                if (direction==Direction.up){
                    //do your stuff
                    Log.d("Swipe Up", "onSwipe: up");
                }

                if (direction==Direction.down){
                    //do your stuff
                    Log.d("Swipe Down", "onSwipe: down");
                    finish();
                }

                return false;
            }
        });
    }

    private void bindingView() {
        ActionBar actionBar = getSupportActionBar();
        Objects.requireNonNull(actionBar).hide();

        profileImageView = findViewById(R.id.profile_activity_image);
        usernameTextView = findViewById(R.id.profile_activity_username);
        statusMessageTextView = findViewById(R.id.profile_activity_status_message);

        profileEditButton = findViewById(R.id.profile_edit_button);
        profileCloseButton = findViewById(R.id.profile_close_button);
        startChatButton = findViewById(R.id.start_chat_button);

        profileEditButton.setVisibility(View.INVISIBLE);
    }

    private void getData() {
        if(getIntentExtra("profileImage") && getIntentExtra("username") && getIntentExtra("statusMessage"))
        {
            email = getIntent().getStringExtra("email");
            userId = getIntent().getStringExtra("userId");
            username = getIntent().getStringExtra("username");
            statusMessage = getIntent().getStringExtra("statusMessage");
            profileImage = getIntent().getStringExtra("profileImage");
        } else {
            Toast.makeText(this, "No Data", Toast.LENGTH_SHORT).show();
        }
    }

    private void setData() {
        setTextOnView(usernameTextView, username);
        setTextOnView(statusMessageTextView, statusMessage);
        Glide.with(this).load(profileImage).into(profileImageView);

        if(username!=null && username.length() !=0) {
            if(username.equals(PreferenceManager.getString("username"))) {
                profileEditButton.setVisibility(View.VISIBLE);
                startChatButton.setText("?????? ????????????");
            }else if(!username.equals(PreferenceManager.getString("username"))) {
                startChatButton.setText("????????? ????????????");
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setButtonClickEvent() {
        profileImageView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });

        profileImageView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), ProfileImageActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("userId", userId);
            startActivity(intent);
        });

        profileEditButton.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), ProfileEditActivity.class);

            intent.putExtra("username", PreferenceManager.getString("username"));
            intent.putExtra("statusMessage", PreferenceManager.getString("statusMessage"));
            intent.putExtra("profileImage", PreferenceManager.getString("profileImage"));

            startActivity(intent);
        });

        profileCloseButton.setOnClickListener(view -> finish());

        startChatButton.setOnClickListener(view -> {
            // exist chat room
            // if not, create chat room
            List<String> userIds = new ArrayList<>();
            userIds.add(PreferenceManager.getString("userId"));

            if(!username.equals(PreferenceManager.getString("username"))) {
                userIds.add(userId);
            }

            createChatRoom(userIds);
        });
    }

    private boolean getIntentExtra(String key) {
        return getIntent().hasExtra(key);
    }

    private void setTextOnView(TextView view, String value) {
        if(value != null && !value.equals("")) {
            view.setText(value);
        } else {
            view.setText("");
        }
    }

    private void setImageOnView(ImageView view, int value) {
        view.setImageResource(value);
    }

    // Retrofit function
    public void getMyProfileInfo(MemberDto memberDto) {
        Call<MemberDto> call = RetrofitClient.getMemberApiService().RequestUserId(memberDto);

        call.enqueue(new Callback<MemberDto>() {
            @Override
            public void onResponse(@NonNull Call<MemberDto> call, @NonNull Response<MemberDto> response) {
                if(!response.isSuccessful()){
                    Log.e("????????? ???????????? : ", "error code : " + response.code());
                    return;
                }

                MemberDto result = response.body();

                assert response.body() != null;
                assert result != null;

                // get my Profile Info
                usernameTextView.setText(result.getUsername());
                statusMessageTextView.setText(result.getStatusMessage());
                Glide.with(getApplicationContext())
                        .load(result.getProfileImage())
                        .error(Glide.with(getApplicationContext())
                                .load(R.drawable.basic_profile_image)
                                .into(profileImageView))
                        .into(profileImageView);

                if(memberDto.getEmail().equals(result.getEmail())){
                    Log.d("????????????: ", "????????? ????????? ????????????");
                }

                Log.d("??? ?????? ???????????? ?????? : ", response.body().toString());
            }

            @Override
            public void onFailure(@NonNull Call<MemberDto> call, @NonNull Throwable t) {
                Log.e("????????????", t.getMessage());
            }
        });
    }

    public void createChatRoom(List<String> userIds) {
        Call<ChatRoomDto> call = RetrofitClient.getChatRoomApiService().RequestCreateChatRoom(userIds);

        call.enqueue(new Callback<ChatRoomDto>() {
            @Override
            public void onResponse(@NonNull Call<ChatRoomDto> call, @NonNull Response<ChatRoomDto> response) {
                if(!response.isSuccessful()){
                    Log.e("????????? ???????????? : ", "error code : " + response.code() + ", body : " + response.body());
                    return;
                }

                assert response.body() != null;
                ChatRoomDto chatRoomDto = response.body();

                Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
                intent.putExtra("roomId", chatRoomDto.getRoomId());
                startActivity(intent);
                finish();

                Log.d("????????? ?????? ?????? : ", response.body().toString());
            }

            @Override
            public void onFailure(@NonNull Call<ChatRoomDto> call, @NonNull Throwable t) {
                Log.e("????????????", t.getMessage());
            }
        });
    }
}