package be.primatis.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

/**
 * Représentation Spring Security d'un {@code AppUser} PRIMATIS. Encapsule
 * uniquement ce qui est nécessaire à l'authentification/autorisation ;
 * {@code AppUser} (l'Entity JPA) n'implémente pas directement
 * {@link UserDetails} pour ne pas exposer l'Entity comme contrat de
 * sécurité.
 *
 * {@code enabled} reflète exclusivement {@code AccountStatus} : ACTIVE →
 * true, DISABLED → false. {@code MemberStatus} (y compris BLOCKED)
 * n'intervient jamais ici — AccountStatus et MemberStatus restent des
 * dimensions strictement distinctes (architecture.md, DEV-03 DO NOT
 * CONTRADICT).
 *
 * Le verrouillage temporaire ({@code lockedUntil}, 3 échecs → 15 minutes)
 * n'est pas encore implémenté (DEV-03.6) : {@code isAccountNonLocked()}
 * renvoie {@code true} sans condition à ce stade, volontairement, pour ne
 * pas introduire de logique temporelle prématurée.
 */
public class PrimatisUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<GrantedAuthority> authorities;

    public PrimatisUserPrincipal(
            Long userId, String email, String passwordHash, boolean enabled, Set<GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
