package com.example.modumessenger.entity;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.example.modumessenger.dto.MemberDto;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.gson.annotations.SerializedName;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Member implements Parcelable {
    @SerializedName("id")
    private Long id;
    @SerializedName("userId")
    private String userId;
    @SerializedName("email")
    private String email;
    @SerializedName("auth")
    private String auth;
    @SerializedName("role")
    private Role role;
    @SerializedName("username")
    private String username;
    @SerializedName("statusMessage")
    private String statusMessage;
    @SerializedName("profileImage")
    private String profileImage;
    @SerializedName("wallpaperImage")
    private String wallpaperImage;
    @SerializedName("profile")
    private List<Profile> profiles;

    public Member(String userId, Member member) {

    }

    public Member(MemberDto memberDto) {
        setId(memberDto.getId());
        setUserId(memberDto.getUserId());
        setEmail(memberDto.getEmail());
        setAuth("google");
        setUsername(memberDto.getUsername());
        setStatusMessage(memberDto.getStatusMessage());
        setProfileImage(memberDto.getProfileImage() == null ? null : memberDto.getProfileImage().toString());
        setWallpaperImage(memberDto.getWallpaperImage());
        setProfiles(memberDto.getProfiles() != null ? memberDto.getProfiles().stream().map(Profile::new).collect(Collectors.toList()) : null);
    }

    public Member(GoogleSignInAccount account) {
        setUserId(account.getId());
        setEmail(account.getEmail());
        setAuth("google");
        setUsername(account.getDisplayName());
        setStatusMessage("");
        setProfileImage(account.getPhotoUrl() == null ? null : account.getPhotoUrl().toString());
    }

    public Member(String userId, String email, String username, String statusMessage, String profileImage, String wallpaperImage) {
        setUserId(userId);
        setEmail(email);
        setUsername(username);
        setStatusMessage(statusMessage);
        setProfileImage(profileImage);
        setWallpaperImage(wallpaperImage);
    }

    public Member(String email) {
        this.userId = null;
        this.email = email;
        this.auth = null;
        this.username = null;
        this.statusMessage = null;
        this.profileImage = null;
    }

    public Member(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    protected Member(Parcel in) {
        userId = in.readString();
        email = in.readString();
    }

    public Long getId() { return this.id; }
    public String getUserId() { return this.userId; }
    public String getEmail() { return this.email; }
    public String getAuth() { return this.auth; }
    public Role getRole() { return this.role; }
    public String getUsername() { return this.username; }
    public String getStatusMessage() { return this.statusMessage; }
    public String getProfileImage() { return this.profileImage; }
    public String getWallpaperImage() { return this.wallpaperImage; }
    public List<Profile> getProfiles() { return this.profiles; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setEmail(String email) { this.email = email; }
    public void setUsername(String username) { this.username = username; }
    public void setAuth(String auth) { this.auth = auth; }
    public void setRole(Role role) { this.role = role; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public void setProfileImage(String profileImage) { this.profileImage = (profileImage == null || profileImage.equals("") ? "" : profileImage); }
    public void setWallpaperImage(String wallpaperImage) { this.wallpaperImage = (wallpaperImage == null || wallpaperImage.equals("") ? "" : wallpaperImage); }
    public void setProfiles(List<Profile> profileList) { this.profiles = profileList; }

    public void updateProfile(String username, String statusMessage, String profileImage, String wallpaperImage) {
        if(username != null) setUsername(username);
        if(statusMessage != null) setStatusMessage(statusMessage);
        if(profileImage != null) setProfileImage(profileImage);
        if(wallpaperImage != null) setWallpaperImage(wallpaperImage);
    }

    public void updateProfile(MemberDto memberDto) {
        if(memberDto.getUsername() != null) setUsername(memberDto.getUsername() );
        if(memberDto.getStatusMessage() != null) setStatusMessage(memberDto.getStatusMessage());
        if(memberDto.getProfileImage() != null) setProfileImage(memberDto.getProfileImage());
        if(memberDto.getWallpaperImage() != null) setWallpaperImage(memberDto.getWallpaperImage());
    }

    public List<String> getProfileListTypedDesc(ProfileType type) {
        return this.getProfiles()
                .stream()
                .filter(profile -> profile.getProfileType().equals(type))
                .sorted(Comparator.comparing(Profile::getLastModifiedDateTime).reversed())
                .map(Profile::getValue)
                .collect(Collectors.toList());
    }

    public List<Profile> getAllProfileListDesc() {
        return this.getProfiles()
                .stream()
                .sorted(Comparator.comparing(Profile::getLastModifiedDateTime).reversed())
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public String toString() { return "Member{" + "user_id='" + userId + '\'' + ", email='" + email + '\'' + '}'; }

    public static final Creator<Member> CREATOR = new Creator<Member>() {
        @Override
        public Member createFromParcel(Parcel in) {
            return new Member(in);
        }

        @Override
        public Member[] newArray(int size) {
            return new Member[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(userId);
        parcel.writeString(email);
    }
}