package com.example.modumessenger.member.entity;

import com.example.modumessenger.chat.entity.ChatRoomMember;
import com.example.modumessenger.common.domain.BaseTimeEntity;
import com.example.modumessenger.member.dto.MemberDto;
import com.example.modumessenger.member.entity.profile.Profile;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Member extends BaseTimeEntity {

    @Id
    @Column(name = "member_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private String userId;

    private String auth;

    @Enumerated(EnumType.STRING)
    private Role role;

    @NotNull
    private String email;

    private String username;

    private String statusMessage;
    private String profileImage;
    private String wallpaperImage;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL)
    private List<Profile> profileList = new ArrayList<>();

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL)
    private List<ChatRoomMember> chatRoomMemberList = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "friends", joinColumns = @JoinColumn(name = "id"))
    @Column(name = "friends")
    private List<Long> Friends;

    @Override
    public String toString() {
        return getUserId() + ", " + getUsername() + "," + getEmail() + "," + getAuth() + "," + getStatusMessage() + "," + getProfileImage();
    }

    public Member update(String name, String picture) {
        this.username = name;
        this.profileImage = picture;

        return this;
    }

    public Member(MemberDto memberDto) {
        setUserId(memberDto.getUserId());
        setAuth(memberDto.getAuth());
        setRole(memberDto.getRole());
        setEmail(memberDto.getEmail());
        setUsername(memberDto.getUsername());
        setStatusMessage(memberDto.getStatusMessage());
        setProfileImage(memberDto.getProfileImage());
        setWallpaperImage(memberDto.getWallpaperImage());
        setProfileList(memberDto.getProfileDtoList().stream().map(Profile::new).collect(Collectors.toList()));
        this.Friends = new ArrayList<>();
    }

    public Member(String userId) {
        this.userId = userId;
    }

    public Member(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public Member(String email, String name, String picture) {
        setEmail(email);
        setUsername(name);
        setProfileImage(picture);
    }
}
