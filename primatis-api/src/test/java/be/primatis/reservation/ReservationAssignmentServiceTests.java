package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie directement {@link ReservationAssignmentService} (DEV-08.6,
 * OD-DEV08-08) pour la branche {@code releaseCopy} qui préserve
 * {@code AvailabilityStatus.UNAVAILABLE} sur un Copy {@code LOST}/
 * {@code OUT_OF_SERVICE} plutôt que de le rendre {@code AVAILABLE}
 * (mission DEV-08.6 §14/§17).
 *
 * <p><b>Pourquoi un test direct sur le composant, jamais via un workflow
 * complet</b> : la contrainte PostgreSQL {@code ck_copy_condition_availability}
 * (V001, DEV-06.6) impose en permanence {@code copy_condition IN ('LOST',
 * 'OUT_OF_SERVICE') ⇒ availability_status = 'UNAVAILABLE'} — pas seulement
 * au moment d'une transition. Un Copy {@code LOST}/{@code OUT_OF_SERVICE}
 * ne peut donc jamais avoir été {@code ON_LOAN} ou {@code RESERVED}
 * juste avant l'appel de cette primitive : aucun appelant réel
 * ({@code LoanService.registerReturn}, {@code ReservationService} lors
 * d'une annulation {@code READY}) ne peut légitimement produire cet état
 * d'entrée. Fabriquer ce scénario via un workflow métier complet serait
 * donc impossible et interdit (mission §18 : « ne pas fabriquer un test
 * artificiel impossible métier »). Ce test appelle en conséquence
 * directement la primitive avec un Copy déjà conforme à la contrainte
 * ({@code LOST}/{@code OUT_OF_SERVICE} + {@code UNAVAILABLE}), pour
 * prouver que la branche {@code releaseCopy} sélectionne bien
 * {@code UNAVAILABLE} — et non {@code AVAILABLE} — sur la base de
 * {@code copy_condition}, en miroir différentiel du cas {@code GOOD}
 * déjà couvert par {@code ReservationServiceTests} (Copy rendu
 * {@code AVAILABLE} quand aucun candidat WAITING n'existe).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationAssignmentServiceTests {

    @Autowired
    private ReservationAssignmentService reservationAssignmentService;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void releaseCopyKeepsALostCopyUnavailableWhenNoWaitingCandidateExists() {
        Copy copy = persistCopy(CopyCondition.LOST);

        reservationAssignmentService.assignNextAdmissibleWaitingReservationOrMakeAvailable(copy, clock.instant());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void releaseCopyKeepsAnOutOfServiceCopyUnavailableWhenNoWaitingCandidateExists() {
        Copy copy = persistCopy(CopyCondition.OUT_OF_SERVICE);

        reservationAssignmentService.assignNextAdmissibleWaitingReservationOrMakeAvailable(copy, clock.instant());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    private Copy persistCopy(CopyCondition condition) {
        Title title = new Title();
        title.setTitle("Titre de test — assignation");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(Instant.now());
        title.setUpdatedAt(Instant.now());
        entityManager.persist(title);

        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode("ASSIGN-" + condition.name() + "-" + System.nanoTime());
        copy.setCopyCondition(condition);
        copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);

        entityManager.flush();
        return copy;
    }
}
