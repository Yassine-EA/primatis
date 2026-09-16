package be.primatis.exception;

/**
 * Fichier média (image) invalide fourni pour insertion dans un Article
 * (DEV-ARTICLES-MEDIA) — type MIME non autorisé, fichier vide ou taille
 * excessive. Traduite en HTTP 400 par {@link GlobalExceptionHandler} :
 * il s'agit d'une requête structurellement invalide (le fichier envoyé ne
 * respecte pas le contrat d'upload), pas d'un conflit d'état métier —
 * distinct de {@link BusinessRuleException} (409) pour cette raison.
 */
public class InvalidMediaFileException extends ApiException {

    private static final String DEFAULT_CODE = "ARTICLE_MEDIA_INVALID_FILE";

    public InvalidMediaFileException(String message) {
        super(DEFAULT_CODE, message);
    }

    public InvalidMediaFileException(String code, String message) {
        super(code, message);
    }
}
