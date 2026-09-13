package com.example.memberservice.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.memberservice.member.entity.Member;
import com.example.memberservice.member.entity.Role;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백오피스 회원 목록 정렬을 H2 에서 확인한다. 이름 규칙은 앱 친구 목록({@link FriendSort})과 같아야 한다:
 * 한글 이름이 먼저, 그 다음 영문·숫자·기호, 이름이 빈 회원은 항상 마지막.
 * 다른 테스트가 남긴 회원과 섞이지 않게 이메일에 심어 둔 표식으로 좁혀서 본다.
 */
@SpringBootTest
@Transactional
class MemberAdminSortTest {

    private static final String MARK = "sortcase";

    @Autowired MemberRepository memberRepository;

    private Member save(String userId, String username) {
        return save(userId, username, null);
    }

    private Member save(String userId, String username, Role role) {
        return memberRepository.save(Member.builder()
                .userId(MARK + "-" + userId)
                .email(MARK + "-" + userId + "@example.com")
                .username(username)
                .role(role)
                .profiles(new ArrayList<>()).chatRoomMembers(new ArrayList<>())
                .build());
    }

    private void saveAll() {
        save("1", "김철수");
        save("2", "Alice");
        save("3", "박영희");
        save("4", "bob");
        save("5", "3반 민수");
    }

    @Test
    void 기본은_이름_오름차순이고_한글_이름이_먼저다() {
        saveAll();

        Page<Member> page = memberRepository.searchForAdmin(MARK, MemberSort.NAME_ASC, Pageable.unpaged());

        assertThat(page.getContent()).extracting(Member::getUsername)
                .containsExactly("김철수", "박영희", "3반 민수", "Alice", "bob");
    }

    /** 숫자로 시작하는 이름은 한글이 아니므로 영문과 같은 그룹이고, 그 안에서는 코드포인트 순이다. */
    @Test
    void 내림차순은_그룹_순서만_뒤집고_이름은_역순이다() {
        saveAll();

        Page<Member> page = memberRepository.searchForAdmin(MARK, MemberSort.NAME_DESC, Pageable.unpaged());

        assertThat(page.getContent()).extracting(Member::getUsername)
                .containsExactly("bob", "Alice", "3반 민수", "박영희", "김철수");
    }

    @Test
    void 이름이_없거나_빈_회원은_방향과_상관없이_마지막이다() {
        saveAll();
        save("6", null);
        save("7", "");

        for (MemberSort sort : List.of(MemberSort.NAME_ASC, MemberSort.NAME_DESC)) {
            List<String> userIds = memberRepository.searchForAdmin(MARK, sort, Pageable.unpaged())
                    .getContent().stream().map(Member::getUserId).toList();
            // 이름이 빈 둘끼리의 순서는 DB 의 NULL 취급에 달려 있어 정하지 않는다. 둘 다 뒤로 밀렸는지만 본다.
            assertThat(userIds.subList(userIds.size() - 2, userIds.size()))
                    .containsExactlyInAnyOrder(MARK + "-6", MARK + "-7");
        }
    }

    @Test
    void 가입일_정렬도_고를_수_있다() {
        saveAll();

        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.CREATED_DESC, Pageable.unpaged()).getContent())
                .extracting(Member::getUsername)
                .containsExactly("3반 민수", "bob", "박영희", "Alice", "김철수");
        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.CREATED_ASC, Pageable.unpaged()).getContent())
                .extracting(Member::getUsername)
                .containsExactly("김철수", "Alice", "박영희", "bob", "3반 민수");
    }

    @Test
    void 검색어는_이메일이나_이름에_대소문자_구분_없이_걸린다() {
        saveAll();

        assertThat(memberRepository.searchForAdmin("ALICE", MemberSort.NAME_ASC, Pageable.unpaged()).getContent())
                .extracting(Member::getUsername).containsExactly("Alice");
        assertThat(memberRepository.searchForAdmin(MARK + "-4", MemberSort.NAME_ASC, Pageable.unpaged()).getContent())
                .extracting(Member::getUsername).containsExactly("bob");
    }

    @Test
    void 페이지를_잘라도_전체_건수가_맞는다() {
        saveAll();

        Page<Member> first = memberRepository.searchForAdmin(MARK, MemberSort.NAME_ASC, PageRequest.of(0, 2));
        Page<Member> second = memberRepository.searchForAdmin(MARK, MemberSort.NAME_ASC, PageRequest.of(1, 2));

        assertThat(first.getContent()).extracting(Member::getUsername).containsExactly("김철수", "박영희");
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(second.getContent()).extracting(Member::getUsername).containsExactly("3반 민수", "Alice");
    }

    /** 이메일과 사용자 ID 는 심어 둔 표식 덕분에 1~5 순서 그대로 줄선다. */
    @Test
    void 이메일_정렬도_고를_수_있다() {
        saveAll();

        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.EMAIL_ASC, Pageable.unpaged()).getContent())
                .extracting(Member::getEmail)
                .containsExactly(email("1"), email("2"), email("3"), email("4"), email("5"));
        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.EMAIL_DESC, Pageable.unpaged()).getContent())
                .extracting(Member::getEmail)
                .containsExactly(email("5"), email("4"), email("3"), email("2"), email("1"));
    }

    @Test
    void 사용자_ID_정렬도_고를_수_있다() {
        saveAll();

        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.USER_ID_ASC, Pageable.unpaged()).getContent())
                .extracting(Member::getUserId)
                .containsExactly(MARK + "-1", MARK + "-2", MARK + "-3", MARK + "-4", MARK + "-5");
        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.USER_ID_DESC, Pageable.unpaged()).getContent())
                .extracting(Member::getUserId)
                .containsExactly(MARK + "-5", MARK + "-4", MARK + "-3", MARK + "-2", MARK + "-1");
    }

    /** 권한은 저장된 이름 순이라 ROLE_ADMIN 이 ROLE_MEMBER 보다 앞이다. 같은 권한끼리는 id 로 고정된다. */
    @Test
    void 권한_정렬도_고를_수_있다() {
        save("1", "김철수", Role.ROLE_MEMBER);
        save("2", "Alice", Role.ROLE_ADMIN);
        save("3", "박영희", Role.ROLE_MEMBER);

        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.ROLE_ASC, Pageable.unpaged()).getContent())
                .extracting(Member::getUsername)
                .containsExactly("Alice", "김철수", "박영희");
        assertThat(memberRepository.searchForAdmin(MARK, MemberSort.ROLE_DESC, Pageable.unpaged()).getContent())
                .extracting(Member::getUsername)
                .containsExactly("박영희", "김철수", "Alice");
    }

    private static String email(String userId) {
        return MARK + "-" + userId + "@example.com";
    }

    @Test
    void 허용_목록에_없는_정렬_문자열은_받지_않는다() {
        assertThat(MemberSort.parse("name")).contains(MemberSort.NAME_ASC);
        assertThat(MemberSort.parse("name,desc")).contains(MemberSort.NAME_DESC);
        assertThat(MemberSort.parse("email,asc")).contains(MemberSort.EMAIL_ASC);
        assertThat(MemberSort.parse("email,desc")).contains(MemberSort.EMAIL_DESC);
        assertThat(MemberSort.parse("userId,asc")).contains(MemberSort.USER_ID_ASC);
        assertThat(MemberSort.parse("userId,desc")).contains(MemberSort.USER_ID_DESC);
        assertThat(MemberSort.parse("role,asc")).contains(MemberSort.ROLE_ASC);
        assertThat(MemberSort.parse("role,desc")).contains(MemberSort.ROLE_DESC);
        assertThat(MemberSort.parse("createdDate,desc")).contains(MemberSort.CREATED_DESC);
        assertThat(MemberSort.parse("createdDate,asc")).contains(MemberSort.CREATED_ASC);
        // 목록에 값이 보이지 않는 열은 여전히 못 고른다.
        assertThat(MemberSort.parse("statusMessage,asc")).isEmpty();
        assertThat(MemberSort.parse("name,sideways")).isEmpty();
        assertThat(MemberSort.parse("")).isEmpty();
        assertThat(MemberSort.parse(null)).isEmpty();
    }
}
