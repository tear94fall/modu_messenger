package com.example.memberservice.common.dto;

import jakarta.validation.constraints.NotBlank;

/** 백오피스가 보내는 수정 본문. 키는 경로에 있으므로 값만 받는다. */
public record UpdateCommonDataDto(@NotBlank String value) {
}
