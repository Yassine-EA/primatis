package be.primatis.security.web;

import be.primatis.security.AccessToken;
import be.primatis.security.AuthService;
import be.primatis.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void loginReturnsAccessTokenContract() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "member@example.com",
                        null,
                        List.of());

        Instant expiresAt = Instant.parse("2026-08-15T01:00:00Z");
        AccessToken accessToken =
                new AccessToken(
                        "signed-jwt",
                        "Bearer",
                        expiresAt,
                        3600);

        when(authService.login("member@example.com", "CorrectPassword123!"))
                .thenReturn(authentication);
        when(jwtService.generateAccessToken(authentication))
                .thenReturn(accessToken);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "CorrectPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-15T01:00:00Z"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(authService)
                .login("member@example.com", "CorrectPassword123!");
        verify(jwtService)
                .generateAccessToken(authentication);
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "member@example.com",
                        null,
                        List.of());

        when(authService.login("member@example.com", "CorrectPassword123!"))
                .thenReturn(authentication);
        when(jwtService.generateAccessToken(authentication))
                .thenReturn(new AccessToken(
                        "signed-jwt",
                        "Bearer",
                        Instant.parse("2026-08-15T01:00:00Z"),
                        3600));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "CorrectPassword123!"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void blankEmailIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "",
                                  "password": "CorrectPassword123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void invalidEmailIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "CorrectPassword123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void blankPasswordIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
