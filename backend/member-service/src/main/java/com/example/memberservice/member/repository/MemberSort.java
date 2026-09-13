package com.example.memberservice.member.repository;

import static com.example.memberservice.member.entity.QMember.member;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.NumberExpression;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 백오피스 회원 목록에서 고를 수 있는 정렬. {@link FriendSort} 와 같은 이유로 따로 둔다 —
 * Pageable 의 sort 를 그대로 열면 아무 컬럼이나 지정할 수 있고 "한글 먼저" 같은 복합 규칙도 담을 수 없다.
 * 이름 정렬 규칙은 앱 친구 목록({@link FriendSort})과 같아야 한다. 같은 회원이 화면마다 다른 자리에 있으면 헷갈린다.
 * 목록에 값이 보이는 열은 모두 여기에 있다. 백오피스는 머리글을 눌러 정렬한다.
 */
public enum MemberSort {
    NAME_ASC, NAME_DESC,
    EMAIL_ASC, EMAIL_DESC,
    USER_ID_ASC, USER_ID_DESC,
    ROLE_ASC, ROLE_DESC,
    CREATED_DESC, CREATED_ASC;

    /** 백오피스 기본값. 이름 가나다순이 목록에서 사람을 찾기 쉽다. */
    public static final MemberSort DEFAULT = NAME_ASC;

    /** '힣'(U+D7A3) 다음 문자. '가' <= 이름 < 이 값이면 한글 음절로 시작하는 이름이다. */
    private static final String AFTER_LAST_HANGUL_SYLLABLE = "힤";

    /** "name", "name,asc", "createdDate,desc" 처럼 필드[,방향] 형태만 받는다. 방향이 없으면 asc. */
    public static Optional<MemberSort> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.trim().toLowerCase(Locale.ROOT).split(",", -1);
        if (parts.length > 2) {
            return Optional.empty();
        }
        String direction = parts.length == 2 ? parts[1] : "asc";
        if (!direction.equals("asc") && !direction.equals("desc")) {
            return Optional.empty();
        }
        boolean asc = direction.equals("asc");
        return switch (parts[0]) {
            case "name" -> Optional.of(asc ? NAME_ASC : NAME_DESC);
            case "email" -> Optional.of(asc ? EMAIL_ASC : EMAIL_DESC);
            case "userid" -> Optional.of(asc ? USER_ID_ASC : USER_ID_DESC);
            case "role" -> Optional.of(asc ? ROLE_ASC : ROLE_DESC);
            case "createddate" -> Optional.of(asc ? CREATED_ASC : CREATED_DESC);
            default -> Optional.empty();
        };
    }

    /** 이 정렬이 요구하는 ORDER BY. 마지막 키는 항상 id 라서 같은 값끼리도 순서가 고정된다. */
    public List<OrderSpecifier<?>> orders() {
        return switch (this) {
            case NAME_ASC -> List.of(nameGroup(0, 1).asc(), nameKey().asc(), member.id.asc());
            case NAME_DESC -> List.of(nameGroup(1, 0).asc(), nameKey().desc(), member.id.desc());
            case EMAIL_ASC -> List.of(member.email.lower().asc(), member.id.asc());
            case EMAIL_DESC -> List.of(member.email.lower().desc(), member.id.desc());
            case USER_ID_ASC -> List.of(member.userId.lower().asc(), member.id.asc());
            case USER_ID_DESC -> List.of(member.userId.lower().desc(), member.id.desc());
            // role 은 EnumType.STRING 이라 저장된 이름("ROLE_ADMIN" < "ROLE_MEMBER") 순으로 줄선다.
            case ROLE_ASC -> List.of(member.role.asc(), member.id.asc());
            case ROLE_DESC -> List.of(member.role.desc(), member.id.desc());
            case CREATED_DESC -> List.of(member.createdDate.desc(), member.id.desc());
            case CREATED_ASC -> List.of(member.createdDate.asc(), member.id.asc());
        };
    }

    /**
     * 이름을 세 그룹으로 나눈다. 이름이 비었거나 없는 회원은 방향과 상관없이 항상 마지막(2) —
     * 빈칸이 목록 맨 위에 몰려 있으면 회원을 찾는 데 방해만 된다.
     * 한글 음절로 시작하는 이름과 그 외(영문·숫자·기호) 이름의 순서는 인자로 받는다.
     * 범위 비교라 DB 콜레이션과 무관하다.
     */
    private static NumberExpression<Integer> nameGroup(int hangulRank, int otherRank) {
        return new CaseBuilder()
                .when(member.username.isNull().or(member.username.eq(""))).then(2)
                .when(member.username.goe("가").and(member.username.lt(AFTER_LAST_HANGUL_SYLLABLE))).then(hangulRank)
                .otherwise(otherRank);
    }

    /** 소문자로 비교해 대소문자 구분이 다른 H2/MySQL 에서 같은 순서가 나오게 한다. 이메일·아이디도 같은 이유로 lower 다. */
    private static ComparableExpressionBase<String> nameKey() {
        return member.username.lower();
    }
}
