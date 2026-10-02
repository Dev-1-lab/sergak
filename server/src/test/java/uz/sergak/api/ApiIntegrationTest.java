package uz.sergak.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tashqi kalitlarsiz: server ishga tushadi, IOC va validatsiya ishlaydi, provayderlar o'chiq. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static final String H = "b".repeat(64);

    @Test
    void feedContainsSeededIocs() throws Exception {
        mvc.perform(get("/v1/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashes['db1d14d5246f2c8807c55084b74247dea6465285']").exists());
    }

    @Test
    void unknownHashWithoutProvidersIsUnknown() throws Exception {
        mvc.perform(post("/v1/hashes").header("X-Install-Id", "test-install-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":[\"" + "c".repeat(64) + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("UNKNOWN"));
    }

    @Test
    void adminIocMakesHashAndDomainMalicious() throws Exception {
        mvc.perform(post("/admin/ioc").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SHA256\",\"value\":\"" + H + "\",\"label\":\"TestStealer\",\"source\":\"test\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/ioc").header("X-Admin-Token", "test-admin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SHA256\",\"value\":\"" + H + "\",\"label\":\"TestStealer\",\"source\":\"test\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/admin/ioc").header("X-Admin-Token", "test-admin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DOMAIN\",\"value\":\"my-gov-uz.online\",\"label\":\"phishing\",\"source\":\"test\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/v1/hashes").header("X-Install-Id", "test-install-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":[\"" + H.toUpperCase() + "\"]}"))
                .andExpect(jsonPath("$.results[0].status").value("MALICIOUS"))
                .andExpect(jsonPath("$.results[0].label").value("TestStealer"));
        mvc.perform(post("/v1/url").header("X-Install-Id", "test-install-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://kompensatsiya.my-gov-uz.online/tolov?id=1\"}"))
                .andExpect(jsonPath("$.status").value("MALICIOUS"))
                .andExpect(jsonPath("$.key").value("https://kompensatsiya.my-gov-uz.online/tolov"));
    }

    @Test
    void validation() throws Exception {
        mvc.perform(post("/v1/hashes").header("X-Install-Id", "test-install-3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":[\"not-a-hash\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/url").header("X-Install-Id", "test-install-3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rateLimitPerInstall() throws Exception {
        String body = "{\"sha256\":[\"" + "d".repeat(64) + "\"]}";
        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/v1/hashes").header("X-Install-Id", "rate-limit-test").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/v1/hashes").header("X-Install-Id", "rate-limit-test").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void health() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
