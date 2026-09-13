package com.example.memberservice.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.memberservice.api.admin.dto.AdminMemberDetailDto;
import com.example.memberservice.api.admin.dto.AdminMemberSummaryDto;
import com.example.memberservice.member.dto.MemberDto;
import com.example.memberservice.member.dto.UpdateProfileDto;
import com.example.memberservice.member.entity.Role;
import com.example.memberservice.member.repository.MemberSort;
import com.example.memberservice.member.service.MemberService;
import org.springframework.http.MediaType;
import java.time.LocalDateTime;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MemberAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean MemberService memberService;

    @Test
    void withoutToken_is403() throws Exception {
        mockMvc.perform(get("/api-admin/member")).andExpect(status().isForbidden());
    }

    @Test
    void search_returnsPage() throws Exception {
        AdminMemberSummaryDto dto = new AdminMemberSummaryDto(1L, "u1", "Alice", "profile.jpg", "a@b.c", Role.ROLE_MEMBER, LocalDateTime.now(), null);
        when(memberService.searchMembers(eq("a"), any(), any())).thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api-admin/member").param("keyword", "a").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("Alice"))
                .andExpect(jsonPath("$.content[0].profileImage").value("profile.jpg"))
                .andExpect(jsonPath("$.content[0].email").value("a@b.c"))
                .andExpect(jsonPath("$.content[0].createdDate").value(matchesPattern("^\\d{4}-.*")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** 정렬을 안 주면 이름 가나다순(한글 먼저)이다. 예전 기본값이던 최신 가입순이 아니다. */
    @Test
    void list_defaultsToNameAsc() throws Exception {
        when(memberService.searchMembers(eq("a"), any(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api-admin/member").param("keyword", "a").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<MemberSort> sortCaptor = ArgumentCaptor.forClass(MemberSort.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberService).searchMembers(eq("a"), sortCaptor.capture(), pageableCaptor.capture());
        assertEquals(MemberSort.NAME_ASC, sortCaptor.getValue());
        // 한글 우선 규칙은 Sort 로 못 담아 QueryDSL 이 만든다. Pageable 에는 정렬을 싣지 않는다.
        assertEquals(Sort.unsorted(), pageableCaptor.getValue().getSort());
    }

    @Test
    void list_acceptsEachAllowedSort() throws Exception {
        when(memberService.searchMembers(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertSortParsedAs("name,asc", MemberSort.NAME_ASC);
        assertSortParsedAs("name,desc", MemberSort.NAME_DESC);
        assertSortParsedAs("email,asc", MemberSort.EMAIL_ASC);
        assertSortParsedAs("email,desc", MemberSort.EMAIL_DESC);
        assertSortParsedAs("userId,asc", MemberSort.USER_ID_ASC);
        assertSortParsedAs("userId,desc", MemberSort.USER_ID_DESC);
        assertSortParsedAs("role,asc", MemberSort.ROLE_ASC);
        assertSortParsedAs("role,desc", MemberSort.ROLE_DESC);
        assertSortParsedAs("createdDate,desc", MemberSort.CREATED_DESC);
        assertSortParsedAs("createdDate,asc", MemberSort.CREATED_ASC);
    }

    private void assertSortParsedAs(String param, MemberSort expected) throws Exception {
        org.mockito.Mockito.clearInvocations(memberService);
        mockMvc.perform(get("/api-admin/member").param("sort", param).header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk());
        ArgumentCaptor<MemberSort> captor = ArgumentCaptor.forClass(MemberSort.class);
        verify(memberService).searchMembers(any(), captor.capture(), any());
        assertEquals(expected, captor.getValue());
    }

    @Test
    void list_rejectsUnknownSort() throws Exception {
        // 목록에 값이 보이지 않는 열은 정렬할 수 없다.
        mockMvc.perform(get("/api-admin/member").param("sort", "statusMessage,asc").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api-admin/member").param("sort", "name,sideways").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());

        verify(memberService, never()).searchMembers(any(), any(), any());
    }

    @Test
    void me_withoutUserIdHeader_is401() throws Exception {
        mockMvc.perform(get("/api-admin/member/me").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withUserIdHeader_returnsSelf() throws Exception {
        MemberDto member = MemberDto.builder().username("Alice").build();
        AdminMemberDetailDto dto = new AdminMemberDetailDto(member, 3, LocalDateTime.now(), List.of());
        when(memberService.getMemberDetailByUserId("admin")).thenReturn(dto);

        mockMvc.perform(get("/api-admin/member/me")
                        .header("X-Internal-Token", "test-internal-token")
                        .header("X-Auth-User-Id", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.username").value("Alice"));

        verify(memberService, never()).getMemberDetail(any());
    }

    @Test
    void updateMe_withoutUserIdHeader_is401() throws Exception {
        mockMvc.perform(put("/api-admin/member/me")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_withUserIdHeader_returnsUpdatedSelf() throws Exception {
        MemberDto member = MemberDto.builder().username("Bob").build();
        AdminMemberDetailDto dto = new AdminMemberDetailDto(member, 2, LocalDateTime.now(), List.of());
        when(memberService.updateMyProfile(eq("admin"), any(UpdateProfileDto.class))).thenReturn(dto);

        mockMvc.perform(put("/api-admin/member/me")
                        .header("X-Internal-Token", "test-internal-token")
                        .header("X-Auth-User-Id", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.username").value("Bob"));
    }

    @Test
    void detail_includesFriendList() throws Exception {
        MemberDto member = MemberDto.builder().username("Alice").build();
        AdminMemberSummaryDto friend = new AdminMemberSummaryDto(
                50L, "demo-jiwoo", "김지우", "a.png", "jiwoo@modu.chat", Role.ROLE_MEMBER, LocalDateTime.now(), "지우야");
        when(memberService.getMemberDetail(1L))
                .thenReturn(new AdminMemberDetailDto(member, 1, LocalDateTime.now(), List.of(friend)));

        mockMvc.perform(get("/api-admin/member/1").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendCount").value(1))
                .andExpect(jsonPath("$.friends[0].username").value("김지우"))
                .andExpect(jsonPath("$.friends[0].userId").value("demo-jiwoo"))
                .andExpect(jsonPath("$.friends[0].friendName").value("지우야"));
    }

}
