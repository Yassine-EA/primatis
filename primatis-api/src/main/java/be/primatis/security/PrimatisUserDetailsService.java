package be.primatis.security;

import be.primatis.access.RoleAndPermissionCode;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Chargement d'un utilisateur PRIMATIS par email pour Spring Security,
 * conformément à la chaîne RBAC autoritaire AppUser → UserRole → Role →
 * RolePermission → Permission (DEV-03.5).
 *
 * Ne distingue jamais, dans une exception ou un message, un email inconnu
 * d'un mot de passe incorrect (cette homogénéisation appartient au workflow
 * login de DEV-03.6) : seul {@link UsernameNotFoundException} est levée ici,
 * sans détail exploitable.
 */
@Service
public class PrimatisUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public PrimatisUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable."));

        List<RoleAndPermissionCode> rows = appUserRepository.findRoleAndPermissionCodesByUserId(user.getId());

        return new PrimatisUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getAccountStatus() == AccountStatus.ACTIVE,
                buildAuthorities(rows));
    }

    /**
     * Les codes de rôle persistés sont déjà préfixés {@code ROLE_} (ex.
     * {@code ROLE_MEMBER}) : aucun préfixage n'est ajouté ici. Les codes de
     * permission sont utilisés tels quels, jamais préfixés {@code ROLE_}.
     * Un {@link TreeSet} garantit un résultat déterministe (ordre stable),
     * indépendant de l'ordre de retour de la requête.
     */
    private Set<GrantedAuthority> buildAuthorities(List<RoleAndPermissionCode> rows) {
        Set<GrantedAuthority> authorities = new TreeSet<>(Comparator.comparing(GrantedAuthority::getAuthority));
        for (RoleAndPermissionCode row : rows) {
            authorities.add(new SimpleGrantedAuthority(row.roleCode()));
            if (row.permissionCode() != null) {
                authorities.add(new SimpleGrantedAuthority(row.permissionCode()));
            }
        }
        return authorities;
    }
}
