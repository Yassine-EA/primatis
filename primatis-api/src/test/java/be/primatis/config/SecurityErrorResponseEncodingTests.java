package be.primatis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEV-16.6 — preuve d'encodage réelle des réponses d'erreur 401/403
 * (rejets {@code SecurityFilterChain}, jamais interceptés par
 * {@code @RestControllerAdvice}, cf. {@link SecurityErrorHandlingTests}).
 *
 * Bug réel confirmé en conditions réelles (Full/{@code primatis_preview},
 * requête curl directe) : chaque message accentué renvoyé par un 401
 * ("expiré", "accéder") était corrompu côté client — l'octet ISO-8859-1
 * brut de "é"/"à" (0xE9/0xE0) était réinterprété comme UTF-8 invalide
 * (U+FFFD), car {@code SecurityErrorResponseWriter} écrivait via
 * {@code response.getWriter()}, dont l'encodage suit le
 * {@code characterEncoding} de la réponse — resté au défaut du VRAI
 * conteneur Servlet (Tomcat embarqué, ISO-8859-1) tant qu'aucun
 * {@code setCharacterEncoding("UTF-8")} explicite n'est posé.
 *
 * <b>Pourquoi ce test n'utilise PAS {@code MockMvc}</b> : mesuré
 * empiriquement (DEV-16.6), {@code MockHttpServletResponse.setContentType(
 * "application/json")} (sans paramètre charset) fait basculer son
 * {@code characterEncoding} par défaut sur UTF-8 — un comportement plus
 * "utile" que le VRAI Tomcat, qui reste sur ISO-8859-1 dans la même
 * situation. Un test {@code MockMvc} ne peut donc PAS reproduire ce bug,
 * quel que soit le code de production testé — d'où un test de bout en
 * bout sur un vrai port HTTP embarqué, seul moyen de vérifier les octets
 * réellement envoyés sur le réseau.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityErrorResponseEncodingTests {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void authenticationRequiredResponseBytesAreValidUtf8OnTheRealWire() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me/loans"))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(401);
        String decoded = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(decoded).doesNotContain("�");
        assertThat(decoded).contains("Une authentification est requise pour accéder à cette ressource.");
    }

    @Test
    void invalidTokenResponseBytesAreValidUtf8OnTheRealWire() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me/loans"))
                .header("Authorization", "Bearer not-a-jwt-at-all")
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(401);
        String decoded = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(decoded).doesNotContain("�");
        assertThat(decoded).contains("Le jeton d'authentification fourni est invalide ou a expiré.");
    }
}
