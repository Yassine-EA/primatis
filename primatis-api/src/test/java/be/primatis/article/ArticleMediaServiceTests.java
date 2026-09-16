package be.primatis.article;

import be.primatis.config.ArticleMediaProperties;
import be.primatis.exception.InvalidMediaFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie {@link ArticleMediaService#storeImage(org.springframework.web.multipart.MultipartFile)}
 * (DEV-ARTICLES-MEDIA, DEV-DEC-0079) : validation (taille, {@code
 * Content-Type} réel), nommage ({@code UUID}, jamais le nom fourni),
 * écriture réelle sur disque, URL publique retournée. Test unitaire pur —
 * aucun Spring, aucun {@code @PreAuthorize} exercé (composant technique,
 * même précédent que {@code ArticleSanitizerTests}) : la sécurité de
 * l'endpoint est couverte séparément par {@code StaffArticleMediaControllerTests}.
 *
 * <p>{@code @TempDir} (mission §25 : « Ne jamais écrire les tests dans le
 * vrai dossier public/media/articles ») — jamais
 * {@code ArticleMediaProperties} par défaut.
 */
class ArticleMediaServiceTests {

    @TempDir
    Path tempDir;

    private ArticleMediaService service(Path storageDir) {
        return new ArticleMediaService(new ArticleMediaProperties(storageDir.toString()));
    }

    private static final byte[] SMALL_CONTENT = "fake-image-bytes".getBytes();

    // ---------------------------------------------------------------
    // Formats acceptés
    // ---------------------------------------------------------------

    @Test
    void storesValidJpegAndReturnsPublicUrl() {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", SMALL_CONTENT);

        String url = service.storeImage(file);

        assertThat(url).startsWith("/media/articles/").endsWith(".jpg");
    }

    @Test
    void storesValidPngAndReturnsPublicUrl() {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", SMALL_CONTENT);

        String url = service.storeImage(file);

        assertThat(url).startsWith("/media/articles/").endsWith(".png");
    }

    @Test
    void storesValidWebpAndReturnsPublicUrl() {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", SMALL_CONTENT);

        String url = service.storeImage(file);

        assertThat(url).startsWith("/media/articles/").endsWith(".webp");
    }

    // ---------------------------------------------------------------
    // Nommage — jamais le nom fourni par le client
    // ---------------------------------------------------------------

    @Test
    void generatesAUuidFilenameNeverTheOriginalClientFilename() {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.jpg", "image/jpeg", SMALL_CONTENT);

        String url = service.storeImage(file);

        assertThat(url).doesNotContain("etc").doesNotContain("passwd").doesNotContain("..");
        String filename = url.substring("/media/articles/".length(), url.length() - ".jpg".length());
        assertThat(filename).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // ---------------------------------------------------------------
    // Écriture réelle sur disque (mission §25 point 8)
    // ---------------------------------------------------------------

    @Test
    void writesTheFileToTheConfiguredStorageDirectory() throws IOException {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", SMALL_CONTENT);

        String url = service.storeImage(file);

        String filename = url.substring("/media/articles/".length());
        Path written = tempDir.resolve(filename);
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readAllBytes(written)).isEqualTo(SMALL_CONTENT);
    }

    @Test
    void createsTheStorageDirectoryWhenItDoesNotExistYet() {
        Path nestedDir = tempDir.resolve("does-not-exist-yet");
        ArticleMediaService service = service(nestedDir);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", SMALL_CONTENT);

        service.storeImage(file);

        assertThat(Files.isDirectory(nestedDir)).isTrue();
    }

    // ---------------------------------------------------------------
    // Refus — taille (mission §7/§25 point 4)
    // ---------------------------------------------------------------

    @Test
    void rejectsFileLargerThanFiveMebibytes() {
        ArticleMediaService service = service(tempDir);
        byte[] tooLarge = new byte[(int) ArticleMediaService.MAX_FILE_SIZE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooLarge);

        assertThatThrownBy(() -> service.storeImage(file))
                .isInstanceOf(InvalidMediaFileException.class)
                .satisfies(ex -> assertThat(((InvalidMediaFileException) ex).getCode())
                        .isEqualTo("ARTICLE_MEDIA_FILE_TOO_LARGE"));
    }

    @Test
    void acceptsFileExactlyAtFiveMebibytes() {
        ArticleMediaService service = service(tempDir);
        byte[] exactSize = new byte[(int) ArticleMediaService.MAX_FILE_SIZE_BYTES];
        MockMultipartFile file = new MockMultipartFile("file", "exact.jpg", "image/jpeg", exactSize);

        String url = service.storeImage(file);

        assertThat(url).startsWith("/media/articles/");
    }

    // ---------------------------------------------------------------
    // Refus — type MIME (mission §6/§25 point 5)
    // ---------------------------------------------------------------

    @Test
    void rejectsNonImageContentTypeEvenWithAnImageExtension() {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.jpg", "text/html", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> service.storeImage(file))
                .isInstanceOf(InvalidMediaFileException.class)
                .satisfies(ex -> assertThat(((InvalidMediaFileException) ex).getCode())
                        .isEqualTo("ARTICLE_MEDIA_INVALID_FILE_TYPE"));
    }

    @Test
    void rejectsSvgContentType() {
        // image/svg+xml peut embarquer du script — jamais dans l'allowlist
        // (mission §6, seuls JPEG/PNG/WebP sont autorisés).
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile(
                "file", "vector.svg", "image/svg+xml", "<svg onload=\"alert(1)\"></svg>".getBytes());

        assertThatThrownBy(() -> service.storeImage(file)).isInstanceOf(InvalidMediaFileException.class);
    }

    @Test
    void rejectsMissingContentType() {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "unknown.jpg", null, SMALL_CONTENT);

        assertThatThrownBy(() -> service.storeImage(file)).isInstanceOf(InvalidMediaFileException.class);
    }

    // ---------------------------------------------------------------
    // Refus — fichier vide
    // ---------------------------------------------------------------

    @Test
    void rejectsEmptyFile() {
        ArticleMediaService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.storeImage(file))
                .isInstanceOf(InvalidMediaFileException.class)
                .satisfies(ex -> assertThat(((InvalidMediaFileException) ex).getCode())
                        .isEqualTo("ARTICLE_MEDIA_FILE_EMPTY"));
    }
}
