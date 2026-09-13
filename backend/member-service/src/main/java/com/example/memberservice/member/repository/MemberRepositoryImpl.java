package com.example.memberservice.member.repository;

import static com.example.memberservice.member.entity.QMember.member;

import com.example.memberservice.member.entity.Member;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Member> searchForAdmin(String keyword, MemberSort sort, Pageable pageable) {
        BooleanExpression predicate = keywordPredicate(keyword);

        JPAQuery<Member> query = queryFactory
                .selectFrom(member)
                .where(predicate)
                .orderBy(sort.orders().toArray(new OrderSpecifier<?>[0]));

        if (pageable.isPaged()) {
            query.offset(pageable.getOffset()).limit(pageable.getPageSize());
        }
        List<Member> content = query.fetch();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(content);
        }
        // 첫 페이지에 다 들어오면 count 질의를 생략한다.
        if (pageable.getOffset() == 0 && content.size() < pageable.getPageSize()) {
            return new PageImpl<>(content, pageable, content.size());
        }
        Long total = queryFactory.select(member.count()).from(member).where(predicate).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /** keyword 가 비어 있으면 null 을 돌려준다. QueryDSL 은 null where 조건을 조건 없음으로 본다. */
    private static BooleanExpression keywordPredicate(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return member.email.containsIgnoreCase(keyword).or(member.username.containsIgnoreCase(keyword));
    }
}
