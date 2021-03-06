package com.example.modumessenger.Activity;

import static android.os.SystemClock.sleep;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.modumessenger.Global.PreferenceManager;
import com.example.modumessenger.Global.ScopedStorageUtil;
import com.example.modumessenger.R;
import com.example.modumessenger.entity.Member;
import com.example.modumessenger.Retrofit.RetrofitClient;
import com.example.modumessenger.dto.MemberDto;

import java.io.File;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileEditActivity extends AppCompatActivity implements ProfileEditBottomSheetFragment.ProfileEditBottomSheetListener {

    Member member;
    ImageView profileImageView;
    EditText myProfileName, myStatusMessage;
    Button profileCloseButton, changeProfileButton, myProfileSaveButton;

    ActivityResultLauncher<Intent> launcher;

    ScopedStorageUtil scopedStorageUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        bindingView();
        setLauncher();
        getData();
        setData();
        setButtonClickEvent();
    }

    @Override
    public void onResume() {
        super.onResume();
        getMyProfileInfo(new MemberDto(PreferenceManager.getString("userId"), PreferenceManager.getString("email")));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        scopedStorageUtil.deleteTempFiles();
    }

    @Override
    public void onButtonClicked(int type) {
        if(type==1) {
            Toast.makeText(this, "???????????? ???????????????", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent();

            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpg", "image/jpeg", "image/png"});
            intent.setAction(Intent.ACTION_GET_CONTENT);

            launcher.launch(intent);
        } else if(type==2) {
            Toast.makeText(this, "?????? ???????????? ???????????????", Toast.LENGTH_SHORT).show();

            member.setProfileImage("");

            updateMyInfo(new MemberDto(member));
            setProfileImage(profileImageView, member.getProfileImage());
        }
    }

    private void bindingView() {
        ActionBar actionBar = getSupportActionBar();
        Objects.requireNonNull(actionBar).hide();

        profileImageView = findViewById(R.id.my_profile_activity_image);
        myProfileName = findViewById(R.id.my_profile_name);
        myStatusMessage = findViewById(R.id.my_status_message);

        profileCloseButton = findViewById(R.id.profile_close_button);
        changeProfileButton = findViewById(R.id.my_profile_image_change_button);
        myProfileSaveButton = findViewById(R.id.my_profile_save_button);
    }

    private void setLauncher() {
        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent intent = result.getData();
                        Uri uri = intent != null ? intent.getData() : null;
                        String fileName = getFileName(getContentResolver(), uri);
                        if(fileName!=null) {
                            String filePath = scopedStorageUtil.copyFromScopedStorage(this, uri, fileName);
                            changeMyProfileImage(filePath);
                        } else {
                            Log.d("????????? ???????????? ?????? : ", "????????? ?????? ???????????? ??????");
                        }
                    }
                });
    }

    private void getData() {
        if(getIntentExtra("profileImage") && getIntentExtra("username") && getIntentExtra("statusMessage")) {
            member = new Member(PreferenceManager.getString("userId"), PreferenceManager.getString("email"));
            member.setUsername(getIntent().getStringExtra("username"));
            member.setStatusMessage(getIntent().getStringExtra("statusMessage"));
            member.setProfileImage(getIntent().getStringExtra("profileImage"));
        } else {
            Toast.makeText(this, "No Data", Toast.LENGTH_SHORT).show();
        }
    }

    private void setData() {
        scopedStorageUtil = new ScopedStorageUtil();
        setTextOnView(myProfileName, member.getUsername());
        setTextOnView(myStatusMessage, member.getStatusMessage());
        Glide.with(this)
                .load(member.getProfileImage())
                .error(Glide.with(this)
                        .load(R.drawable.basic_profile_image)
                        .into(profileImageView))
                .into(profileImageView);
    }

    private void setButtonClickEvent() {
        myProfileSaveButton.setOnClickListener(v -> {
            if(!member.getUsername().equals(myProfileName.getText().toString()) ||
                    !member.getStatusMessage().equals(myStatusMessage.getText().toString())) {

                member.setUsername(myProfileName.getText().toString());
                member.setStatusMessage(myStatusMessage.getText().toString());

                updateMyInfo(new MemberDto(member));
            }
        });

        changeProfileButton.setOnClickListener(view -> {
            ProfileEditBottomSheetFragment bottomSheetDialog = new ProfileEditBottomSheetFragment();
            bottomSheetDialog.show(getSupportFragmentManager(), bottomSheetDialog.getTag());
        });

        profileCloseButton.setOnClickListener(view -> finish());
    }

    private void changeMyProfileImage(String filePath) {
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.Companion.create(file, MediaType.parse("multipart/form-data"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), fileBody);

        String originProfileImage = member.getProfileImage();
        uploadProfileImage(filePart, originProfileImage);
    }

    private String getFileName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        cursor.moveToFirst();
        String fileName = cursor.getString(columnIndex);
        cursor.close();

        return fileName;
    }

    private boolean getIntentExtra(String key) {
        return getIntent().hasExtra(key);
    }

    private void setProfileImage(ImageView imageView, String imageUrl) {
        Glide.with(this)
                .load(imageUrl==null || imageUrl.equals("") ? R.drawable.basic_profile_image : imageUrl)
                .error(Glide.with(this)
                        .load(R.drawable.basic_profile_image)
                        .into(imageView))
                .into(imageView);
    }

    private void setDefaultProfileImage() {
        Glide.with(this)
                .load(R.drawable.basic_profile_image)
                .into(profileImageView);
    }

    private void setTextOnView(TextView view, String value) {
        if(value != null && !value.equals("")) {
            view.setText(value);
        } else {
            view.setText("No Value");
        }
    }

    // Retrofit function
    public void updateMyInfo(MemberDto memberDto) {
        Call<MemberDto> call = RetrofitClient.getMemberApiService().RequestUpdate(member.getUserId(), memberDto);

        call.enqueue(new Callback<MemberDto>() {
            @Override
            public void onResponse(@NonNull Call<MemberDto> call, @NonNull Response<MemberDto> response) {
                if(!response.isSuccessful()){
                    Log.e("????????? ???????????? : ", "error code : " + response.code());
                    return;
                }

                try {
                    assert response.body() != null;
                    MemberDto myInfo = response.body();
                    member = new Member(myInfo);

                    Log.d("????????? ???????????? ?????? : ", response.body().toString());

                    Toast.makeText(getApplicationContext(), "????????? ????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show();
                    finish();
                } catch (Exception e) {
                    Log.e("?????? ?????? : ", e.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<MemberDto> call, @NonNull Throwable t) {
                Log.e("????????????", t.getMessage());
            }
        });
    }

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
                myProfileName.setText(result.getUsername());
                myStatusMessage.setText(result.getStatusMessage());
                Glide.with(getApplicationContext())
                        .load(result.getProfileImage())
                        .error(Glide.with(getApplicationContext())
                                .load(R.drawable.basic_profile_image)
                                .into(profileImageView))
                        .into(profileImageView);

                if(member.getEmail().equals(result.getEmail())){
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

    public void uploadProfileImage(MultipartBody.Part file, String originProfileImage) {
        Call<String> call = RetrofitClient.getImageApiService().RequestUploadImage(file);

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if(!response.isSuccessful()){
                    Log.e("????????? ???????????? : ", "error code : " + response.code());
                }

                assert response.body() != null;
                String filePath = response.body();
                member.setProfileImage(RetrofitClient.getBaseUrl() + "modu_chat/images/" + filePath);
                scopedStorageUtil.deleteTempFiles();

                if(!member.getProfileImage().equals(originProfileImage)) {
                    member.setUsername(String.valueOf(myProfileName.getText()));
                    member.setStatusMessage(String.valueOf(myStatusMessage.getText()));

                    sleep(1000);

                    updateMyInfo(new MemberDto(member));

                    Glide.with(getApplicationContext())
                            .load(!member.getProfileImage().equals("") && member.getProfileImage() != null ? member.getProfileImage() : R.drawable.basic_profile_image)
                            .error(Glide.with(getApplicationContext())
                                    .load(R.drawable.basic_profile_image)
                                    .into(profileImageView))
                            .into(profileImageView);
                }

                Log.d("????????? ????????? ????????? ?????? : ", response.body());
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                Log.e("????????????", t.getMessage());
                member.setProfileImage(originProfileImage);
            }
        });
    }
}