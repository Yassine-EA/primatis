package be.primatis.article.web;

import be.primatis.config.JwtProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat REST staff d'upload d'image Article (DEV-ARTICLES-MEDIA,
 * DEV-DEC-0079) : {@code POST /api/v1/staff/articles/media}. Même
 * précédent JWT signé manuellement que {@code StaffArticleControllerTests}.
 *
 * <p>{@code @DynamicPropertySource} + {@code @TempDir} (mission §25 :
 * jamais le vrai dossier {@code public/media/articles}) — remplace
 * {@code primatis.article-media.storage-path} par un répertoire temporaire
 * JUnit, propre à cette classe, avant le démarrage du contexte Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffArticleMediaControllerTests {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideArticleMediaStoragePath(DynamicPropertyRegistry registry) {
        registry.add("primatis.article-media.storage-path", tempDir::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    private static final byte[] SMALL_JPEG_CONTENT = "fake-image-bytes".getBytes();

    // ---------------------------------------------------------------
    // Sécurité (mission §25 point 9)
    // ---------------------------------------------------------------

    @Test
    void uploadWithoutJwtIsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", SMALL_JPEG_CONTENT);

        mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadWithRoleMemberIsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", SMALL_JPEG_CONTENT);

        mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadWithArticlePublishOnlyIsForbidden() throws Exception {
        // Mission §10 : ARTICLE_MANAGE requis, jamais ARTICLE_PUBLISH seul —
        // même précédent exact que create/update Article (DEV-11.6 §13).
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", SMALL_JPEG_CONTENT);

        mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", SMALL_JPEG_CONTENT);

        mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void uploadWithAdminAndArticleManageIsAuthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", SMALL_JPEG_CONTENT);

        mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Comportement (mission §25 points 7/8)
    // ---------------------------------------------------------------

    @Test
    void uploadReturnsAPublicUrlUnderMediaArticlesNeverAFilesystemPath() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", SMALL_JPEG_CONTENT);

        String responseBody = mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/media/articles/")))
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain(tempDir.toString()).doesNotContain("/home/").doesNotContain("C:\\");
    }

    @Test
    void uploadedFileIsActuallyWrittenToTheConfiguredTempStorageDirectory() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", SMALL_JPEG_CONTENT);

        String responseBody = mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(responseBody, "$.url");
        String filename = url.substring("/media/articles/".length());
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void uploadWithInvalidFileTypeReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", SMALL_JPEG_CONTENT);

        mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTICLE_MEDIA_INVALID_FILE_TYPE"));
    }

    @Test
    void uploadWithOversizedFileReturns400() throws Exception {
        // Volontairement entre la limite métier réelle (5 MiB = 5 242 880
        // octets, ArticleMediaService) et le plafond technique Spring
        // (spring.servlet.multipart.max-file-size = 6MB) : prouve que
        // c'est bien notre propre validation (message clair) qui rejette
        // ici, jamais le backstop générique Spring.
        byte[] tooLarge = new byte[5_500_000];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooLarge);

        mockMvc.perform(multipart("/api/v1/staff/articles/media").file(file)
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTICLE_MEDIA_FILE_TOO_LARGE"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private String signToken(Long userId, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
