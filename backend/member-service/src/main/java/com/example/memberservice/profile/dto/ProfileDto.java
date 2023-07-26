package com.example.memberservice.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto implements Serializable {

    private Long id;
    private Long memberId;
    private ProfileType profileType;
    private String value;
    private String createdDate;
    private String updatedDate;
}
