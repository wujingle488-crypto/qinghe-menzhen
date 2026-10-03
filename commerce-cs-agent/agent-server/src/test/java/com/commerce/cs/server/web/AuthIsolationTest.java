package com.commerce.cs.server.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "llm.enabled=false")
@AutoConfigureMockMvc
class AuthIsolationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void eachAccountOnlySeesItsOwnConsultHistory() throws Exception {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        String zhang = register("张" + suffix, "pass1234");
        String li = register("李" + suffix, "pass1234");

        MvcResult opened = mvc.perform(post("/api/consult/sessions")
                        .header("Authorization", "Bearer " + zhang))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = json.readTree(body(opened)).get("id").asLong();

        mvc.perform(get("/api/consult/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + li))
                .andExpect(status().isForbidden());

        JsonNode liSessions = json.readTree(body(mvc.perform(get("/api/consult/sessions")
                        .header("Authorization", "Bearer " + li))
                .andExpect(status().isOk())
                .andReturn()));
        for (JsonNode item : liSessions) {
            assertFalse(item.get("id").asLong() == sessionId);
        }

        JsonNode zhangSessions = json.readTree(body(mvc.perform(get("/api/consult/sessions")
                        .header("Authorization", "Bearer " + zhang))
                .andExpect(status().isOk())
                .andReturn()));
        boolean found = false;
        for (JsonNode item : zhangSessions) {
            if (item.get("id").asLong() == sessionId) {
                found = true;
            }
        }
        assertTrue(found);

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + zhang))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + zhang))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/consult/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    void visitCardsStayWithTheirOwner() throws Exception {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        String zhang = register("卡张" + suffix, "pass1234");
        String li = register("卡李" + suffix, "pass1234");
        MvcResult created = mvc.perform(post("/api/profile/cards")
                        .header("Authorization", "Bearer " + zhang)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long cardId = json.readTree(body(created)).get("id").asLong();

        JsonNode liCards = json.readTree(body(mvc.perform(get("/api/profile/cards")
                        .header("Authorization", "Bearer " + li))
                .andExpect(status().isOk())
                .andReturn()));
        for (JsonNode item : liCards) {
            assertFalse(item.get("id").asLong() == cardId);
        }
        mvc.perform(get("/api/profile").param("id", String.valueOf(cardId))
                        .header("Authorization", "Bearer " + li))
                .andExpect(status().isBadRequest());
        assertEquals("张三", json.readTree(body(mvc.perform(get("/api/profile")
                        .param("id", String.valueOf(cardId))
                        .header("Authorization", "Bearer " + zhang))
                .andExpect(status().isOk())
                .andReturn())).get("name").asText());
    }

    private String register(String username, String password) throws Exception {
        String payload = "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"confirm\":\"" + password + "\"}";
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(body(result)).get("token").asText();
    }

    private static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
