package be.primatis.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * email est l'identifiant d'authentification (architecture.md §5.2).
     */
    Optional<AppUser> findByEmail(String email);
}
