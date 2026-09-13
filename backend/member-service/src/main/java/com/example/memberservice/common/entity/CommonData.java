package com.example.memberservice.common.entity;

import com.example.memberservice.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱이 켜질 때 읽어 가는 key/value 설정 한 줄. 앱 버전(version) 처럼 배포 없이 바꿔야 하는 값을 담는다.
 * 키가 곧 식별자라 따로 시퀀스를 두지 않는다 — 같은 키를 두 줄로 갖고 있으면 어느 쪽이 맞는지 알 수 없다.
 */
@Getter
@Entity
@Table(name = "common_data")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonData extends BaseTimeEntity {

    /** key 는 여러 DB 에서 예약어라 컬럼 이름을 data_key 로 둔다. */
    @Id
    @Column(name = "data_key", length = 50)
    private String key;

    @Column(name = "data_value", nullable = false, length = 255)
    private String value;

    public CommonData(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public void updateValue(String value) {
        this.value = value;
    }
}
