package com.example.profileservice.profile.service;

import com.example.profileservice.member.client.MemberFeignClient;
import com.example.profileservice.member.dto.AddProfileDto;
import com.example.profileservice.profile.dto.CreateProfileDto;
import com.example.profileservice.profile.dto.ProfileDto;
import com.example.profileservice.profile.entity.Profile;
import com.example.profileservice.profile.entity.ProfileType;
import com.example.profileservice.profile.repository.ProfileRepository;
import com.example.profileservice.storage.client.StorageFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final MemberFeignClient memberFeignClient;
    private final StorageFeignClient storageFeignClient;

    public List<ProfileDto> getMemberProfiles(Long memberId) {
        List<Profile> profileList = profileRepository.findByMemberId(memberId);

        return profileList.stream()
                .map(ProfileDto::new)
                .collect(Collectors.toList());
    }

    public ProfileDto getMemberLatestProfile(String memberId) {
        Profile latestProfile = profileRepository.findLatestProfile(Long.valueOf(memberId));
        return new ProfileDto(latestProfile);
    }

    public List<ProfileDto> getMemberProfileOffset(String memberId, String id, String count) {
        List<Profile> profileList = profileRepository.findByMemberProfileOffset(Long.valueOf(memberId), Long.valueOf(id), Long.valueOf(count));

        return profileList.stream()
                .map(ProfileDto::new)
                .collect(Collectors.toList());
    }

    public ProfileDto getMemberProfile(String memberId, String id) {
        Profile profile = profileRepository.findByMemberProfile(Long.valueOf(memberId), Long.valueOf(id));
        return new ProfileDto(profile);
    }

    public Long getMemberProfileTotalCount(String memberId) {
        return profileRepository.findMemberTotalProfiles(Long.valueOf(memberId));
    }

    public ProfileDto registerProfile(CreateProfileDto createProfileDto) {
        Profile profile = new Profile(createProfileDto);

        profileRepository.save(profile);
        memberFeignClient.addMemberProfile(new AddProfileDto(profile));

        return new ProfileDto(profile);
    }

    public Long deleteProfile(String memberId, String id) {
        Profile findProfile = profileRepository.findByMemberProfile(Long.valueOf(memberId), Long.valueOf(id));

        if (findProfile.getProfileType() != ProfileType.PROFILE_STATUS_MESSAGE) {
            storageFeignClient.delete(findProfile.getValue());
        }

        return profileRepository.deleteByMemberProfile(Long.valueOf(memberId), Long.valueOf(id));
    }
}
