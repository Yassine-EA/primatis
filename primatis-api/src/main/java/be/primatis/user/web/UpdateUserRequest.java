package be.primatis.user.web;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.util.Set;

/**
 * Contrat REST de modification administrative d'un {@code AppUser}
 * (DEV-05.6, {@code USER_MANAGE}). Volontairement absent : {@code email}
 * (workflow de modification encore OPEN, DEV-05.1 §10), {@code
 * accountStatus}/{@code memberStatus} (transitions réservées à DEV-05.7),
 * {@code memberNumber} (immuable, jamais régénéré), {@code password}/{@code
 * passwordHash} (réservé au futur reset administratif, DEV-DEC-0015).
 *
 * <p><b>Sémantique PATCH sparse à trois états</b> — {@code ABSENT} /
 * {@code PRESENT(valeur)} / {@code PRESENT(null)} — distingués sans
 * bibliothèque externe (aucun {@code JsonNullable}/JSON Merge Patch dans le
 * projet) : classe JavaBean plutôt qu'un {@code record}, chaque setter
 * positionne un indicateur de présence dédié. Jackson n'invoque un setter
 * que pour une clé JSON réellement présente (valeur ou {@code null}) ;
 * une clé absente du JSON ne déclenche jamais l'appel du setter, laissant
 * l'indicateur à {@code false} — c'est cette distinction qui permet au
 * Service ({@code UserService.updateUser}) de choisir entre « conserver »
 * et « remplacer/effacer ».
 *
 * <p>Les indicateurs ({@code isXxxPresent()}) sont annotés {@code
 * @JsonIgnore} : jamais exposés en entrée (pas une clé JSON attendue) ni en
 * sortie/schéma OpenAPI (détail technique interne).
 *
 * <p>{@code roles} reste un cas particulier sans indicateur dédié :
 * absent et {@code null} explicite produisent intentionnellement le même
 * état ({@code roles == null} après désérialisation), tous deux traités
 * comme « rôles inchangés » — {@code roles} n'a pas de sémantique de
 * « clear » (un ensemble vide est toujours refusé), donc aucune ambiguïté
 * ne peut apparaître entre les deux cas.
 */
public class UpdateUserRequest {

    private String firstName;
    private boolean firstNamePresent;

    private String lastName;
    private boolean lastNamePresent;

    private String phoneNumber;
    private boolean phoneNumberPresent;

    private Set<String> roles;

    private LocalDate registrationDate;
    private boolean registrationDatePresent;

    private LocalDate memberExpirationDate;
    private boolean memberExpirationDatePresent;

    private String blockedReason;
    private boolean blockedReasonPresent;

    public UpdateUserRequest() {
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        this.firstNamePresent = true;
    }

    @JsonIgnore
    public boolean isFirstNamePresent() {
        return firstNamePresent;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        this.lastNamePresent = true;
    }

    @JsonIgnore
    public boolean isLastNamePresent() {
        return lastNamePresent;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.phoneNumberPresent = true;
    }

    @JsonIgnore
    public boolean isPhoneNumberPresent() {
        return phoneNumberPresent;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
        this.registrationDatePresent = true;
    }

    @JsonIgnore
    public boolean isRegistrationDatePresent() {
        return registrationDatePresent;
    }

    public LocalDate getMemberExpirationDate() {
        return memberExpirationDate;
    }

    public void setMemberExpirationDate(LocalDate memberExpirationDate) {
        this.memberExpirationDate = memberExpirationDate;
        this.memberExpirationDatePresent = true;
    }

    @JsonIgnore
    public boolean isMemberExpirationDatePresent() {
        return memberExpirationDatePresent;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
        this.blockedReasonPresent = true;
    }

    @JsonIgnore
    public boolean isBlockedReasonPresent() {
        return blockedReasonPresent;
    }
}
