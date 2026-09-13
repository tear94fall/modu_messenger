package com.example.memberservice.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommonDataAdminControllerTest {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String TOKEN = "test-internal-token";

    @Autowired MockMvc mockMvc;

    private void putValue(String key, String value, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api-admin/common/" + key)
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void 토큰이_없으면_403_이다() throws Exception {
        mockMvc.perform(get("/api-admin/common")).andExpect(status().isForbidden());
    }

    @Test
    void PUT_으로_만들면_GET_으로_읽힌다() throws Exception {
        putValue("version", "1.0.0", 200);

        mockMvc.perform(get("/api-admin/common/version").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("version"))
                .andExpect(jsonPath("$.value").value("1.0.0"));
    }

    @Test
    void 같은_키에_다시_PUT_하면_덮어쓴다() throws Exception {
        putValue("version", "1.0.0", 200);

        mockMvc.perform(put("/api-admin/common/version")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"2.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("2.0.0"));

        mockMvc.perform(get("/api-admin/common/version").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("2.0.0"));
    }

    @Test
    void 목록은_저장한_키를_담는다() throws Exception {
        putValue("version", "3.0.0", 200);

        mockMvc.perform(get("/api-admin/common").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").value("version"))
                .andExpect(jsonPath("$[0].value").value("3.0.0"));
    }

    @Test
    void 아직_없는_키는_404_다() throws Exception {
        mockMvc.perform(get("/api-admin/common/not-set-yet").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void 규칙에_어긋나는_키는_400_이다() throws Exception {
        putValue("Version", "1.0.0", 400);
        putValue("9lives", "1.0.0", 400);
    }

    @Test
    void 값이_비면_400_이다() throws Exception {
        putValue("version", "   ", 400);

        mockMvc.perform(put("/api-admin/common/version")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
