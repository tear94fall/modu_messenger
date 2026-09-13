package com.example.memberservice.member.repository;

import com.example.memberservice.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberCustomRepository {

    /**
     * 백오피스 회원 목록 한 페이지. keyword 가 있으면 email 또는 username 에 대소문자 구분 없이 포함되는 회원만 남긴다.
     * 정렬은 {@link MemberSort} 로만 정하고 Pageable 의 sort 는 쓰지 않는다 — 한글 우선 규칙을 Sort 로 표현할 수 없다.
     */
    Page<Member> searchForAdmin(String keyword, MemberSort sort, Pageable pageable);
}
