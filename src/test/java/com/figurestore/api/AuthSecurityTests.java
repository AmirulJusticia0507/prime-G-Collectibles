package com.figurestore.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.admin.username=admin",
        "app.admin.password=test-password",
        "app.payment-expiry.interval-ms=3600000"
})
@AutoConfigureMockMvc
class AuthSecurityTests {
    @Autowired MockMvc mvc;

    @Test
    void adminLoginReturnsJwtThatCanAccessAdminEndpoint() throws Exception {
        String login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login, "$.accessToken");

        mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"JWT Product","fullPrice":1000000,"dpPrice":200000,
                                 "stockSlot":1,"status":"PO_OPEN"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidLoginUsesConsistentErrorResponse() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Username atau password salah"));
    }

    @Test
    void malformedLoginBodyReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Body request tidak valid"));
    }

    @Test
    void publicRequestGetsRequestIdAndProtectedEndpointRejectsAnonymous() throws Exception {
        mvc.perform(get("/api/products").header("X-Request-ID", "test-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "test-request-123"));

        mvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"));
    }
}
