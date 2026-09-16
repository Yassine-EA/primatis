package be.primatis.article;

import be.primatis.config.ArticleMediaProperties;
import be.primatis.exception.InvalidMediaFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage filesystem des images destinées à être insérées dans le
 * contenu HTML riche d'un Article (DEV-ARTICLES-MEDIA, DEV-DEC-0079) —
 * jamais en base (aucun blob, aucun base64), jamais une nouvelle Entity
 * {@code Media}/table dédiée (business-rules.md §7.9, portée strictement
 * limitée par DEV-DEC-0079). Responsabilité unique : valider un fichier
 * (taille, {@code Content-Type} réel — jamais l'extension seule),
 * générer un nom sûr et unique ({@code UUID}, jamais le nom fourni par le
 * client), l'écrire dans {@link ArticleMediaProperties#storagePath()}, et
 * retourner l'URL publique correspondante ({@link
 * ArticleMediaProperties#PUBLIC_PATH_PREFIX}) — jamais un chemin
 * filesystem. Même précédent structurel que {@code ArticleSanitizer} :
 * composant technique pur, {@code @PreAuthorize} porté ici (Service),
 * jamais sur le Controller (`.claude/rules/backend.md`).
 *
 * <p>Suppression des fichiers explicitement hors périmètre (mission §21,
 * DEV-DEC-0079) : une image peut rester référencée dans un Article
 * encore actif — aucun mécanisme de nettoyage n'est introduit ici.
 */
@Service
public class ArticleMediaService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMediaService.class);

    /**
     * 5 MiB — valeur centralisée (mission §7) : seule occurrence du
     * calcul dans le backend, jamais recopiée ailleurs.
     */
    static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    /**
     * Formats autorisés (mission §6) : {@code Content-Type} réel du
     * multipart, jamais l'extension du nom de fichier fourni par le
     * client (non digne de confiance). Extension normalisée associée,
     * utilisée pour le nom généré — jamais dérivée du nom original.
     */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private static final String FILE_EMPTY_CODE = "ARTICLE_MEDIA_FILE_EMPTY";
    private static final String FILE_TOO_LARGE_CODE = "ARTICLE_MEDIA_FILE_TOO_LARGE";
    private static final String INVALID_FILE_TYPE_CODE = "ARTICLE_MEDIA_INVALID_FILE_TYPE";

    private final Path storageDirectory;

    public ArticleMediaService(ArticleMediaProperties properties) {
        this.storageDirectory = Path.of(properties.storagePath()).toAbsolutePath().normalize();
    }

    /**
     * Valide puis stocke {@code file}, et retourne son URL publique
     * relative ({@code /media/articles/<uuid>.<ext>}). {@code
     * ARTICLE_MANAGE} exigé — même permission que le reste de la gestion
     * staff des Articles (mission §10 : aucune permission dédiée
     * introduite).
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaFileException(FILE_EMPTY_CODE, "Aucun fichier fourni.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidMediaFileException(
                    FILE_TOO_LARGE_CODE, "Le fichier dépasse la taille maximale autorisée (5 Mio).");
        }

        String contentType = file.getContentType();
        String extension = contentType != null ? EXTENSION_BY_CONTENT_TYPE.get(contentType) : null;
        if (extension == null) {
            throw new InvalidMediaFileException(INVALID_FILE_TYPE_CODE,
                    "Type de fichier non autorisé (JPEG, PNG ou WebP uniquement).");
        }

        String generatedFilename = UUID.randomUUID() + extension;
        Path target = storageDirectory.resolve(generatedFilename).normalize();
        if (!target.getParent().equals(storageDirectory)) {
            // Défense en profondeur : generatedFilename est un UUID que nous
            // générons nous-mêmes (jamais dérivé d'une entrée utilisateur) —
            // structurellement impossible, garde-fou explicite néanmoins
            // contre tout traversal (mission §8).
            throw new InvalidMediaFileException(INVALID_FILE_TYPE_CODE, "Nom de fichier généré invalide.");
        }

        try {
            Files.createDirectories(storageDirectory);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Écriture de l'image Article impossible.", ex);
        }

        log.info("Image Article stockée : {} ({} octets, {})", generatedFilename, file.getSize(), file.getContentType());
        return ArticleMediaProperties.PUBLIC_PATH_PREFIX + generatedFilename;
    }
}
