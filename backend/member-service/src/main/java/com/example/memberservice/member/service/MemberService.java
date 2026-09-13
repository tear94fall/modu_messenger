package com.example.memberservice.member.service;

import com.example.memberservice.api.admin.dto.AdminMemberDetailDto;
import com.example.memberservice.api.admin.dto.AdminMemberSummaryDto;
import com.example.memberservice.global.exception.CustomException;
import com.example.memberservice.global.exception.ErrorCode;
import com.example.memberservice.global.lock.ApiLock;
import com.example.memberservice.global.lock.LockParam;
import com.example.memberservice.member.dto.*;
import com.example.memberservice.member.entity.Member;
import com.example.memberservice.member.repository.MemberRepository;
import com.example.memberservice.member.repository.MemberSort;
import com.example.memberservice.profile.client.ProfileFeignClient;
import com.example.memberservice.profile.dto.AddProfileDto;
import com.example.memberservice.profile.dto.ProfileDto;
import com.example.memberservice.profile.dto.ProfileType;
import com.example.memberservice.storage.client.StorageFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemberService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final ModelMapper modelMapper;
    private final StorageFeignClient storageFeignClient;
    private final ProfileFeignClient profileFeignClient;
    private final MemberFriendService memberFriendService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_FOUND, email));

        return new User(member.getEmail(), member.getUserId(),
                true, true, true, true, new ArrayList<>());
    }

    public MemberDto getUserById(String userId) {
        Member member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USERID_NOT_FOUND_ERROR, userId));

        return modelMapper.map(member, MemberDto.class);
    }

    /**
     * auth-service 가 구글 ID 토큰을 검증한 뒤 부른다. 이메일로 찾고 없으면 만든다(가입 = 첫 로그인).
     * 새 회원이고 구글 프로필 사진이 있으면 storage 에 올려 첫 프로필로 기록한다.
     */
    @ApiLock
    public MemberDto findOrCreateGoogleMember(@LockParam GoogleAccountDto account) {
        Optional<Member> registered = memberRepository.findByEmail(account.getEmail());
        if (registered.isPresent()) {
            return MemberDto.createMemberDto(registered.get());
        }

        MemberDto created = registerMember(account);
        if (account.getPicture() != null && !account.getPicture().isBlank()) {
            return addProfileImage(created);
        }
        return created;
    }

    MemberDto registerMember(GoogleAccountDto account) {
        MemberDto memberDto = new MemberDto(account);
        Member member = new Member(memberDto);

        // 동시에 들어온 두 요청이 모두 findByEmail 을 통과한 뒤 저장을 시도하면 DB 의 유일 제약
        // (uk_member_email/uk_member_user_id) 이 하나만 통과시키고 나머지는 DataIntegrityViolationException
        // 을 던진다. 같은 트랜잭션 안에서는 REPEATABLE READ 스냅샷 때문에 상대가 커밋한 행이 보이지
        // 않고 트랜잭션도 이미 롤백 표시가 되어 여기서 복구할 수 없다 — 그대로 흘려보내고
        // MemberSignupService 가 트랜잭션 밖에서 재시도한다.
        Member saveMember = memberRepository.save(member);
        return MemberDto.createMemberDto(saveMember);
    }

    public MemberDto addProfileImage(MemberDto memberDto) {
        Member member = memberRepository.findById(memberDto.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_ID_NOT_FOUND_ERROR, memberDto.getId()));

        String uploadFile = storageFeignClient.upload(member.getProfileImage()).getBody();
        if (uploadFile == null || uploadFile.isEmpty()) {
            throw new CustomException(ErrorCode.USER_PROFILE_IMAGE_UPLOAD_ERROR, memberDto.getProfileImage());
        }

        ProfileDto profileDto = ProfileDto.from(0L, member.getId(), ProfileType.PROFILE_IMAGE, uploadFile, "", "");
        ProfileDto saveProfile = profileFeignClient.addProfileRequest(profileDto).getBody();

        if (saveProfile == null || !saveProfile.getValue().equals(uploadFile)) {
            throw new CustomException(ErrorCode.USER_PROFILE_IMAGE_UPLOAD_ERROR, uploadFile);
        }

        member.updateMemberInfo(saveProfile);
        member.addProfile(profileDto.getId());

        return MemberDto.createMemberDto(member);
    }

    public MemberDto getMemberById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_ID_NOT_FOUND_ERROR, id));
        return MemberDto.createMemberDto(member);
    }

    public MemberDto getMemberByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_FOUND, email));
        return MemberDto.createMemberDto(member);
    }

    public MemberDto updateMemberProfile(String userId, UpdateProfileDto updateProfileDto) {
        Member member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USERID_NOT_FOUND_ERROR, userId));

        member.updateProfile(updateProfileDto);
        Member updateMember = memberRepository.save(member);

        return MemberDto.createMemberDto(member);
    }

    public MemberDto rollbackMemberProfile(Long id, ProfileDto profileDto) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_ID_NOT_FOUND_ERROR, id));

        List<ProfileDto> profileList = profileFeignClient.getMemberProfiles(member.getId()).getBody();
        if (profileList != null && profileList.isEmpty()) {
            ProfileDto profile = profileList.getLast();

            if (profileDto.getProfileType().equals(ProfileType.PROFILE_IMAGE) || profileDto.getProfileType().equals(ProfileType.PROFILE_WALLPAPER)) {
                UpdateProfileDto updateProfileDto = UpdateProfileDto.createUpdateProfileDto(new MemberDto(member), profile);
                return updateMemberProfile(member.getUserId(), updateProfileDto);
            }
        }

        return MemberDto.createMemberDto(member);
    }

    public List<MemberDto> findFriend(String email) {
        if(!memberRepository.existsByEmail(email)) {
            return new ArrayList<>();
        }

        List<Member> memberList = memberRepository.findAllByEmail(email);

        return memberList
                .stream()
                .map(MemberDto::new)
                .collect(Collectors.toList());
    }

    public List<MemberDto> findMembers(List<String> userIds) {
        List<Member> members = memberRepository.findAllByUserIdIn(userIds);

        return members
                .stream()
                .map(MemberDto::new)
                .collect(Collectors.toList());
    }

    public List<MemberDto> findMembersById(List<Long> ids) {
        List<Member> members = memberRepository.findAllById(ids);
        return members
                .stream()
                .map(MemberDto::new)
                .collect(Collectors.toList());
    }

    public List<MemberDto> inviteMembers(ChatRoomMemberDto chatRoomMemberDto) {
        List<Long> chatRoomMembersIds = chatRoomMemberDto.getChatRoomMembers()
                .stream()
                .map(MemberDto::getId)
                .collect(Collectors.toList());

        List<Member> members = memberRepository.findAllById(chatRoomMembersIds);

        Long chatRoomId = chatRoomMemberDto.getChatRoomId();

        List<Member> inviteMembers = members
                .stream()
                .peek(member -> member.addChatRoom(chatRoomId))
                .toList();

        return inviteMembers
                .stream()
                .map(MemberDto::new)
                .collect(Collectors.toList());
    }

    public List<MemberDto> exitMembers(ChatRoomMemberDto chatRoomMemberDto) {
        List<Long> chatRoomMembersIds = chatRoomMemberDto.getChatRoomMembers()
                .stream()
                .map(MemberDto::getId)
                .collect(Collectors.toList());

        List<Member> members = memberRepository.findAllById(chatRoomMembersIds);

        Long chatRoomId = chatRoomMemberDto.getChatRoomId();

        List<Member> exitMembers = members
                .stream()
                .peek(member -> member.delChatRoom(chatRoomId))
                .toList();

        return exitMembers
                .stream()
                .map(MemberDto::new)
                .collect(Collectors.toList());
    }

    public Long addMemberProfile(AddProfileDto addProfileDto) {
        Member member = memberRepository.findById(addProfileDto.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_ID_NOT_FOUND_ERROR, addProfileDto.getMemberId()));

        member.addProfile(addProfileDto.getProfileId());

        return member.getProfiles().get(member.getProfiles().size()-1);
    }

    /**
     * 백오피스 검색. keyword 가 비면 전체.
     * 정렬은 {@link MemberSort} 로 정한다(기본 이름 가나다순, 한글 이름 먼저). Pageable 의 sort 는 쓰지 않는다 —
     * "한글 먼저" 는 컬럼 하나로 표현할 수 없어 QueryDSL CASE 로 만들어야 한다.
     */
    @Transactional(readOnly = true)
    public Page<AdminMemberSummaryDto> searchMembers(String keyword, MemberSort sort, Pageable pageable) {
        return memberRepository.searchForAdmin(keyword, sort == null ? MemberSort.DEFAULT : sort, pageable)
                .map(AdminMemberSummaryDto::from);
    }

    /** 백오피스 상세: 회원 + 친구 수. */
    public AdminMemberDetailDto getMemberDetail(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_ID_NOT_FOUND_ERROR, String.valueOf(id)));
        return toAdminMemberDetailDto(member);
    }

    /** 백오피스에서 로그인한 본인 정보. userId 는 게이트웨이가 넣어 준 값이다. */
    public AdminMemberDetailDto getMemberDetailByUserId(String userId) {
        Member member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USERID_NOT_FOUND_ERROR, userId));
        return toAdminMemberDetailDto(member);
    }

    /**
     * 백오피스에서 본인 정보를 고친다. null 인 필드는 기존 값을 유지한다 —
     * 엔티티의 updateProfile 은 네 필드를 통째로 덮어쓰기 때문이다.
     */
    public AdminMemberDetailDto updateMyProfile(String userId, UpdateProfileDto request) {
        Member member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USERID_NOT_FOUND_ERROR, userId));

        UpdateProfileDto full = UpdateProfileDto.builder()
                .username(request.getUsername() == null ? member.getUsername() : request.getUsername())
                .statusMessage(request.getStatusMessage() == null ? member.getStatusMessage() : request.getStatusMessage())
                .profileImage(request.getProfileImage() == null ? member.getProfileImage() : request.getProfileImage())
                .wallpaperImage(request.getWallpaperImage() == null ? member.getWallpaperImage() : request.getWallpaperImage())
                .build();

        member.updateProfile(full);
        memberRepository.save(member);

        return toAdminMemberDetailDto(member);
    }

    private AdminMemberDetailDto toAdminMemberDetailDto(Member member) {
        List<AdminMemberSummaryDto> friends = memberFriendService.listForAdmin(member.getId());
        return new AdminMemberDetailDto(
                modelMapper.map(member, MemberDto.class), friends.size(), member.getCreatedDate(), friends);
    }
}
