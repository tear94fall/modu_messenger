package com.example.memberservice.common.dto;

import com.example.memberservice.common.entity.CommonData;

/** 안드로이드가 기대하는 모양 그대로다: {"key":"version","value":"1.2.3"}. */
public record CommonDataDto(String key, String value) {

    public static CommonDataDto from(CommonData commonData) {
        return new CommonDataDto(commonData.getKey(), commonData.getValue());
    }
}
