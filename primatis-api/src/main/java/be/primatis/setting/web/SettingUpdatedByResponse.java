package be.primatis.setting.web;

import be.primatis.user.AppUser;

import java.util.Objects;

/**
 * Représentation compacte d'un {@code AppUser} référencé par {@code
 * updatedByUser} dans {@link SettingResponse} (DEV-12.2). Forme identique à
 * {@code article.dto.ArticleUserResponse} ({@code id}/{@code firstName}/
 * {@code lastName}) mais type distinct, local à {@code be.primatis.setting} :
 * réutiliser directement un DTO d'un autre domaine aurait introduit un
 * couplage {@code setting → article} sans rapport avec Application Settings
 * (package-by-feature, `backend.md`). N'expose ni {@code email}, ni {@code
 * phoneNumber}, ni {@code AccountStatus}/{@code MemberStatus}, ni aucune
 * donnée RBAC — jamais l'Entity {@code AppUser} exposée directement.
 */
public record SettingUpdatedByResponse(Long id, String firstName, String lastName) {

    public static SettingUpdatedByResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new SettingUpdatedByResponse(appUser.getId(), appUser.getFirstName(), appUser.getLastName());
    }
}
