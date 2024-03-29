package com.example.modumessenger.Activity;

import static android.app.Activity.RESULT_OK;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.example.modumessenger.Global.ScopedStorageUtil;
import com.example.modumessenger.Grid.SendOthersGridAdapter;
import com.example.modumessenger.Grid.SendOthersGridItem;
import com.example.modumessenger.R;
import com.example.modumessenger.Retrofit.RetrofitClient;
import com.example.modumessenger.Retrofit.RetrofitImageAPI;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatSendOthersActivity extends BottomSheetDialogFragment {

    ActivityResultLauncher<Intent> resultLauncher;
    ActivityResultLauncher<Uri> startCamera;
    ScopedStorageUtil scopedStorageUtil;

    GridView settingGridView;
    SendOthersGridAdapter sendOthersGridAdapter;
    String roomId;

    List<SendOthers> sendOthersList;

    Uri tempImageUri;

    RetrofitImageAPI retrofitImageAPI;

    private ChatSendOthersBottomSheetListener mListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mListener = (ChatSendOthersBottomSheetListener) getContext();
        return inflater.inflate(R.layout.chat_send_others, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getData();
        setData();
        bindingView(view);
        setLauncher();
        setTakePictureLauncher(tempImageUri);
        setButtonClickEvent();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        scopedStorageUtil.deleteTempFiles();
    }

    public interface ChatSendOthersBottomSheetListener {
        void sendImageChat(String filename);
        void sendOthersFinish();
    }

    private void getData() {
        roomId = requireActivity().getIntent().getStringExtra("roomId");
    }

    private void setData() {
        retrofitImageAPI = RetrofitClient.createImageApiService();

        scopedStorageUtil = new ScopedStorageUtil();
        sendOthersList = new ArrayList<>(Arrays.asList(SendOthers.SEND_OTHERS_GALLERY, SendOthers.SEND_OTHERS_CAMERA, SendOthers.SEND_OTHERS_FILE, SendOthers.SEND_OTHERS_AUDIO));
        tempImageUri = initTempUri();
    }

    private void bindingView(View view) {
        settingGridView = view.findViewById(R.id.send_others_grid);
        sendOthersGridAdapter = new SendOthersGridAdapter(requireActivity());
        sendOthersGridAdapter.setGridItems(view, sendOthersList);

        settingGridView.setAdapter(sendOthersGridAdapter);
    }

    private void setButtonClickEvent() {
        settingGridView.setOnItemClickListener((parent, view1, position, id) -> {
            SendOthersGridItem gridItem = sendOthersGridAdapter.getGridItem(position);
            Toast.makeText(requireActivity().getApplicationContext(), gridItem.getItemName(), Toast.LENGTH_SHORT).show();

            SendOthers sendOthers = this.sendOthersList.get(position);

            if(sendOthers == SendOthers.SEND_OTHERS_CAMERA) {
                startCamera.launch(tempImageUri);
            } else {
                Intent intent = new Intent();
                intent.setAction(sendOthers.getSendItemAction());
                intent.setType(sendOthers.getSendItemExtension() == null ? "*/*" : sendOthers.getSendItemExtension());
                resultLauncher.launch(intent);
            }
        });
    }

    public Uri initTempUri() {
        File tempImagesDir = new File(requireActivity().getApplicationContext().getFilesDir(), getString(R.string.temp_images_dir));
        tempImagesDir.mkdir();
        File tempImage = new File(tempImagesDir, getString(R.string.temp_image));
        return FileProvider.getUriForFile(requireActivity().getApplicationContext(), getString(R.string.authorities), tempImage);
    }

    private void setLauncher() {
        resultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == RESULT_OK){
                        Intent intent = result.getData();
                        Uri uri = intent != null ? intent.getData() : null;
                        String fileName = getFileName(requireActivity().getContentResolver(), uri);
                        if(fileName!=null) {
                            String filePath = scopedStorageUtil.copyFromScopedStorage(requireActivity(), uri, fileName);
                            sendImageChat(filePath);
                        } else {
                            Log.d("파일명 가져오기 실패 : ", "갤러리 에서 가져오기 실패");
                        }
                    }
                }
        );
    }

    private void setTakePictureLauncher(Uri tempImagePath) {
        startCamera = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                isSuccess -> {
                    String fileName = getFileName(requireActivity().getContentResolver(), tempImagePath);
                    if(fileName!=null) {
                        String filePath = scopedStorageUtil.copyFromScopedStorage(requireActivity(), tempImagePath, fileName);
                        sendImageChat(filePath);
                    } else {
                        Log.d("파일명 가져오기 실패 : ", "카메라에서 찍고 가져오기 실패");
                    }
                });
    }

    private void sendImageChat(String filePath) {
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.Companion.create(file, MediaType.parse("multipart/form-data"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), fileBody);
        uploadChatImage(filePart);
    }

    private String getFileName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        cursor.moveToFirst();
        String fileName = cursor.getString(columnIndex);
        cursor.close();

        return fileName;
    }

    public enum SendOthers {
        SEND_OTHERS_GALLERY("앨범", "image/*", Intent.ACTION_GET_CONTENT, R.drawable.ic_baseline_image_24),
        SEND_OTHERS_CAMERA("카메라", null, MediaStore.ACTION_IMAGE_CAPTURE, R.drawable.ic_baseline_photo_camera_24),
        SEND_OTHERS_FILE("파일", "*/*", Intent.ACTION_GET_CONTENT, R.drawable.ic_baseline_attach_file_24),
        SEND_OTHERS_AUDIO("음성", "audio/*", Intent.ACTION_GET_CONTENT, R.drawable.ic_baseline_audio_file_24);

        private final String sendItemName;
        private final String sendItemExtension;
        private final String sendItemAction;
        private final int sendItemImage;

        SendOthers(String sendItemName, String sendItemExtension, String sendItemAction, int sendItemImage) {
            this.sendItemName = sendItemName;
            this.sendItemExtension = sendItemExtension;
            this.sendItemAction = sendItemAction;
            this.sendItemImage = sendItemImage;
        }

        public String getSendItemName() { return sendItemName; }
        public String getSendItemExtension() { return sendItemExtension; }
        public String getSendItemAction() { return sendItemAction; }
        public int getSendItemImage() { return sendItemImage; }
    }

    // Retrofit function
    public void uploadChatImage(MultipartBody.Part file) {
        Call<String> call = retrofitImageAPI.RequestUploadImage(file);

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if(!response.isSuccessful()){
                    Log.e("연결이 비정상적 : ", "error code : " + response.code());
                }

                assert response.body() != null;
                String filePath = response.body();
                scopedStorageUtil.deleteTempFiles();

                mListener.sendImageChat(filePath);
                mListener.sendOthersFinish();

                Log.d("채팅 이미지 업로드 요청 : ", response.body());
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                Log.e("연결실패", t.getMessage());
            }
        });
    }
}