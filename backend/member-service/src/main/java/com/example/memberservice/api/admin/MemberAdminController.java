package com.example.memberservice.api.admin;

import com.example.memberservice.api.admin.dto.AdminMemberDetailDto;
import com.example.memberservice.api.admin.dto.AdminMemberSummaryDto;
import com.example.memberservice.member.dto.UpdateProfileDto;
import com.example.memberservice.member.repository.MemberSort;
import com.example.memberservice.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 백오피스가 게이트웨이(ROLE_ADMIN JWT)를 거쳐 부른다. InternalApiFilter 가 토큰을 검사한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/member")
public class MemberAdminController {

    private final MemberService memberService;

    /**
     * 백오피스 목록. 기본은 이름 가나다순이고 한글 이름이 영문·숫자보다 먼저 온다 — 앱 친구 목록과 같은 규칙이다.
     * sort 는 {@link MemberSort} 허용 목록(name | email | userId | role | createdDate 에 ,asc 또는 ,desc)만 받고
     * 모르는 값이면 400 이다. 조용히 기본 정렬로 되돌리면 화면은 정렬된 것처럼 보이는데 값이 다르다.
     */
    @GetMapping
    public ResponseEntity<Page<AdminMemberSummaryDto>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "sort", defaultValue = "name,asc") String sort,
                                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                                  @RequestParam(value = "size", defaultValue = "20") int size) {
        MemberSort memberSort = MemberSort.parse(sort)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬입니다: " + sort));
        return ResponseEntity.ok(memberService.searchMembers(keyword, memberSort,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    /** 백오피스에 로그인한 본인 정보. 게이트웨이가 JWT subject 를 X-Auth-User-Id 헤더로 넣어 준다. */
    @GetMapping("/me")
    public ResponseEntity<AdminMemberDetailDto> me(@RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(memberService.getMemberDetailByUserId(userId));
    }

    /** 백오피스에서 본인 정보를 수정한다. */
    @PutMapping("/me")
    public ResponseEntity<AdminMemberDetailDto> updateMe(@RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
                                                           @RequestBody UpdateProfileDto request) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(memberService.updateMyProfile(userId, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminMemberDetailDto> detail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(memberService.getMemberDetail(id));
    }
}
