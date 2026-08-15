package be.primatis.user;

import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectures {@code USER_READ} sur {@code AppUser} (DEV-05.4). Orchestration
 * Repository + mapping uniquement : ne modifie jamais {@code AppUser}, ne
 * touche à aucun statut, n'attribue aucun rôle, ne calcule aucune expiration
 * ni ne génère de {@code memberNumber} (hors périmètre USER_READ).
 *
 * {@code @PreAuthorize} au niveau Service, jamais au niveau Controller,
 * conformément à la convention établie par
 * {@code RbacMethodSecuritySampleService} (DEV-03.9) pour les futurs
 * Services métier réels.
 */
@Service
public class UserService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";

    private final AppUserRepository appUserRepository;

    public UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return appUserRepository.findAll(pageable).map(UserResponse::from);
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + id + "."));
        return UserResponse.from(user);
    }
}
