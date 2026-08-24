package be.primatis.article.dto;

import be.primatis.user.AppUser;

import java.util.Objects;

/**
 * Représentation compacte d'un {@code AppUser} référencé par un {@code
 * Article} (DEV-11.3), utilisée à la fois pour {@code authorUser} et
 * {@code lastModifiedByUser} dans {@link ArticleResponse} — un seul type
 * partagé, jamais deux quasi-identiques, les deux champs référençant le
 * même concept (une identité d'auteur/éditeur) avec exactement la même
 * forme, contrairement à {@code loan.dto.LoanBorrowerResponse}/{@code
 * reservation.dto.ReservationMemberResponse} qui n'ont chacun besoin que
 * d'un seul rôle utilisateur par domaine.
 *
 * <p>Expose uniquement {@code id}/{@code firstName}/{@code lastName}.
 * {@code memberNumber} est délibérément omis ici, contrairement à {@code
 * LoanBorrowerResponse}/{@code ReservationMemberResponse} : ces deux
 * précédents l'exposent pour distinguer des homonymes emprunteurs/
 * réservataires dans des écrans Staff opérationnels. Un auteur/éditeur
 * d'Article est typiquement `ROLE_LIBRARIAN`/`ROLE_ADMIN`
 * (`ARTICLE_MANAGE`/`ARTICLE_PUBLISH`, V002) et n'a le plus souvent aucune
 * adhésion associée (`memberNumber` nullable, `database-model.md` §8.2) :
 * l'exposer ici n'apporterait aucune valeur d'identification et serait
 * presque toujours {@code null}. N'expose ni {@code email}, ni {@code
 * phoneNumber}, ni {@code AccountStatus}/{@code MemberStatus}, ni aucune
 * donnée RBAC — jamais l'Entity {@code AppUser} exposée directement.
 */
public record ArticleUserResponse(Long id, String firstName, String lastName) {

    public static ArticleUserResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new ArticleUserResponse(appUser.getId(), appUser.getFirstName(), appUser.getLastName());
    }
}
