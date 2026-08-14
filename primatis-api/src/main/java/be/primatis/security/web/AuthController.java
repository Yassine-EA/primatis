package be.primatis.security.web;

import be.primatis.security.AccessToken;
import be.primatis.security.AuthService;
import be.primatis.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point d'entrée REST de l'authentification PRIMATIS.
 *
 * Le Controller orchestre uniquement le contrat HTTP :
 * AuthService reste responsable de l'authentification et du lockout,
 * JwtService reste responsable de la génération du JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication =
                authService.login(request.email(), request.password());

        AccessToken accessToken =
                jwtService.generateAccessToken(authentication);

        return LoginResponse.from(accessToken);
    }
}
