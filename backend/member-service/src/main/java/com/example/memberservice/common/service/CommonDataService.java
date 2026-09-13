package com.example.memberservice.common.service;

import com.example.memberservice.common.dto.CommonDataDto;
import com.example.memberservice.common.entity.CommonData;
import com.example.memberservice.common.repository.CommonDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CommonDataService {

    /**
     * 키는 소문자로 시작하는 50자 이하의 영소문자/숫자/_/- 만 받는다.
     * 경로 변수로 들어오는 값이라 열어 두면 대소문자만 다른 키가 따로 저장돼 앱이 못 읽는 줄이 생긴다.
     * 길이는 data_key 컬럼(50)과 같게 맞춰 DB 에서 잘리지 않게 한다.
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]{0,49}$");

    private final CommonDataRepository commonDataRepository;

    @Transactional(readOnly = true)
    public Optional<CommonDataDto> get(String key) {
        return commonDataRepository.findById(validateKey(key)).map(CommonDataDto::from);
    }

    @Transactional(readOnly = true)
    public List<CommonDataDto> getAll() {
        return commonDataRepository.findAll().stream()
                .sorted(Comparator.comparing(CommonData::getKey))
                .map(CommonDataDto::from)
                .toList();
    }

    /** 있으면 값만 갈아 끼우고 없으면 새로 만든다. 백오피스에서 키를 따로 만들 화면을 두지 않으려는 것이다. */
    @Transactional
    public CommonDataDto upsert(String key, String value) {
        String validKey = validateKey(key);
        CommonData commonData = commonDataRepository.findById(validKey).orElse(null);
        if (commonData == null) {
            return CommonDataDto.from(commonDataRepository.save(new CommonData(validKey, value)));
        }
        commonData.updateValue(value);
        return CommonDataDto.from(commonData);
    }

    private String validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 키입니다: " + key);
        }
        return key;
    }
}
