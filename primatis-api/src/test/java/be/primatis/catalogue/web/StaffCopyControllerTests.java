package be.primatis.catalogue.web;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleRepository;
import be.primatis.catalogue.TitleStatus;
import be.primatis.config.JwtProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat REST staff des {@code Copy} (DEV-06.6) : {@code GET}/{@code POST}/
 * {@code PATCH /api/v1/staff/titles/{titleId}/copies(/{copyId}(/availability))}.
 * Même stratégie de JWT signés manuellement que {@code StaffTitleControllerTests}.
 * Fixtures POST toujours structurellement valides pour les scénarios 403 —
 * même précaution que le correctif gate réel #2 (DEV-06.5, body invalide
 * masquant le contrat de permission visé).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffCopyControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TitleRepository titleRepository;

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> createdTitleIds = new ArrayList<>();

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtureTitles() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Long titleId : createdTitleIds) {
                entityManager.createQuery("DELETE FROM Copy c WHERE c.title.id = :titleId")
                        .setParameter("titleId", titleId).executeUpdate();
                titleRepository.deleteById(titleId);
            }
        });
        createdTitleIds.clear();
    }

    // ---------------------------------------------------------------
    // Sécurité — GET liste
    // ---------------------------------------------------------------

    @Test
    void listCopiesWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistTitle("Copy List Security Anonymous CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies", title.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listCopiesWithoutCopyReadIsForbidden() throws Exception {
        Title title = persistTitle("Copy List Security Member CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listCopiesWithLibrarianAndCopyReadIsAuthorized() throws Exception {
        Title title = persistTitle("Copy List Security Librarian CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("COPY_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void listCopiesWithAdminAndCopyReadIsAuthorized() throws Exception {
        Title title = persistTitle("Copy List Security Admin CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void listCopiesForNonExistentTitleReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TITLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Sécurité — GET détail
    // ---------------------------------------------------------------

    @Test
    void getCopyByIdWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistTitle("Copy Detail Security Anonymous CRT");
        Copy copy = persistCopy(title, "CRT-DETAIL-SEC-ANON-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCopyByIdWithoutCopyReadIsForbidden() throws Exception {
        Title title = persistTitle("Copy Detail Security Member CRT");
        Copy copy = persistCopy(title, "CRT-DETAIL-SEC-MEMBER-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCopyByIdWithLibrarianAndCopyReadIsAuthorized() throws Exception {
        Title title = persistTitle("Copy Detail Security Librarian CRT");
        Copy copy = persistCopy(title, "CRT-DETAIL-SEC-LIB-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("COPY_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(copy.getId()));
    }

    @Test
    void getCopyByIdWithAdminAndCopyReadIsAuthorized() throws Exception {
        Title title = persistTitle("Copy Detail Security Admin CRT");
        Copy copy = persistCopy(title, "CRT-DETAIL-SEC-ADMIN-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void getCopyByIdForWrongTitleOrCopyReturns404() throws Exception {
        Title owningTitle = persistTitle("Copy Detail Wrong Owning CRT");
        Title otherTitle = persistTitle("Copy Detail Wrong Other CRT");
        Copy copy = persistCopy(owningTitle, "CRT-DETAIL-WRONG-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}/copies/{copyId}", otherTitle.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COPY_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Sécurité — POST
    // ---------------------------------------------------------------

    @Test
    void createCopyWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistTitle("Copy Create Security Anonymous CRT");

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCopyJson("CRT-CREATE-SEC-ANON-1", "GOOD", "AVAILABLE")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCopyWithoutCopyManageIsForbidden() throws Exception {
        Title title = persistTitle("Copy Create Security Member CRT");

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCopyJson("CRT-CREATE-SEC-MEMBER-1", "GOOD", "AVAILABLE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCopyWithLibrarianAndCopyManageIsAuthorized() throws Exception {
        Title title = persistTitle("Copy Create Security Librarian CRT");

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCopyJson("CRT-CREATE-SEC-LIB-1", "GOOD", "AVAILABLE")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void createCopyWithAdminAndCopyManageIsAuthorized() throws Exception {
        Title title = persistTitle("Copy Create Security Admin CRT");

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCopyJson("CRT-CREATE-SEC-ADMIN-1", "GOOD", "AVAILABLE")))
                .andExpect(status().isCreated());
    }

    @Test
    void createCopyWithInvalidBodyReturns400() throws Exception {
        Title title = persistTitle("Copy Create Invalid Body CRT");

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createCopyWithDuplicateInventoryCodeReturns409() throws Exception {
        Title title = persistTitle("Copy Create Duplicate CRT");
        persistCopy(title, "CRT-CREATE-DUP-CTRL-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCopyJson("CRT-CREATE-DUP-CTRL-1", "GOOD", "AVAILABLE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createCopyWithOnLoanReturns409() throws Exception {
        Title title = persistTitle("Copy Create On Loan Ctrl CRT");

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCopyJson("CRT-CREATE-ON-LOAN-CTRL-1", "GOOD", "ON_LOAN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    @Test
    void createCopyWithReservedReturns409() throws Exception {
        Title title = persistTitle("Copy Create Reserved Ctrl CRT");

        mockMvc.perform(post("/api/v1/staff/titles/{titleId}/copies", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCopyJson("CRT-CREATE-RESERVED-CTRL-1", "GOOD", "RESERVED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    // ---------------------------------------------------------------
    // Sécurité — PATCH
    // ---------------------------------------------------------------

    @Test
    void updateCopyWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistTitle("Copy Update Security Anonymous CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-SEC-ANON-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCopyWithoutCopyManageIsForbidden() throws Exception {
        Title title = persistTitle("Copy Update Security Member CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-SEC-MEMBER-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCopyWithCopyManageIsAuthorized() throws Exception {
        Title title = persistTitle("Copy Update Security Authorized CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-SEC-AUTH-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"location":"Rayon E5"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Rayon E5"));
    }

    @Test
    void updateCopyForWrongTitleOrCopyReturns404() throws Exception {
        Title owningTitle = persistTitle("Copy Update Wrong Owning CRT");
        Title otherTitle = persistTitle("Copy Update Wrong Other CRT");
        Copy copy = persistCopy(owningTitle, "CRT-UPDATE-WRONG-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}", otherTitle.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COPY_NOT_FOUND"));
    }

    @Test
    void updateCopyConditionLostReturnsUnavailable() throws Exception {
        Title title = persistTitle("Copy Update Condition Lost CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-COND-LOST-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}", title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"copyCondition":"LOST"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copyCondition").value("LOST"))
                .andExpect(jsonPath("$.availabilityStatus").value("UNAVAILABLE"));
    }

    // ---------------------------------------------------------------
    // Sécurité — PATCH availability
    // ---------------------------------------------------------------

    @Test
    void updateAvailabilityWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistTitle("Copy Availability Security Anonymous CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-SEC-ANON-1", CopyCondition.GOOD, AvailabilityStatus.UNAVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}/availability",
                        title.getId(), copy.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"AVAILABLE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAvailabilityWithoutCopyManageIsForbidden() throws Exception {
        Title title = persistTitle("Copy Availability Security Member CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-SEC-MEMBER-1", CopyCondition.GOOD, AvailabilityStatus.UNAVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}/availability",
                        title.getId(), copy.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"AVAILABLE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAvailabilityWithCopyManageIsAuthorized() throws Exception {
        Title title = persistTitle("Copy Availability Security Authorized CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-SEC-AUTH-1", CopyCondition.GOOD, AvailabilityStatus.UNAVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}/availability",
                        title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"AVAILABLE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"));
    }

    @Test
    void updateAvailabilityToAvailableWithLostConditionReturns409() throws Exception {
        Title title = persistTitle("Copy Availability Lost Ctrl CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-LOST-CTRL-1", CopyCondition.LOST, AvailabilityStatus.UNAVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}/availability",
                        title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"AVAILABLE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_CONDITION_REQUIRES_UNAVAILABLE"));
    }

    @Test
    void updateAvailabilityToOnLoanReturns409() throws Exception {
        Title title = persistTitle("Copy Availability On Loan Ctrl CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-ON-LOAN-CTRL-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}/availability",
                        title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ON_LOAN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    @Test
    void updateAvailabilityToReservedReturns409() throws Exception {
        Title title = persistTitle("Copy Availability Reserved Ctrl CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-RESERVED-CTRL-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/copies/{copyId}/availability",
                        title.getId(), copy.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("COPY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RESERVED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private String createCopyJson(String inventoryCode, String copyCondition, String availabilityStatus) {
        return """
                {"inventoryCode":"%s","copyCondition":"%s","availabilityStatus":"%s"}
                """.formatted(inventoryCode, copyCondition, availabilityStatus);
    }

    private String signToken(List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Title persistTitle(String title) {
        Title[] holder = new Title[1];
        transactionTemplate().executeWithoutResult(status -> {
            Title entity = new Title();
            entity.setTitle(title);
            entity.setLanguage(Language.EN);
            entity.setTitleStatus(TitleStatus.ACTIVE);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            titleRepository.save(entity);
            holder[0] = entity;
        });
        createdTitleIds.add(holder[0].getId());
        return holder[0];
    }

    private Copy persistCopy(
            Title title, String inventoryCode, CopyCondition copyCondition, AvailabilityStatus availabilityStatus) {
        Copy[] holder = new Copy[1];
        transactionTemplate().executeWithoutResult(status -> {
            Copy copy = new Copy();
            copy.setTitle(entityManager.getReference(Title.class, title.getId()));
            copy.setInventoryCode(inventoryCode);
            copy.setCopyCondition(copyCondition);
            copy.setAvailabilityStatus(availabilityStatus);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            holder[0] = copy;
        });
        return holder[0];
    }
}
