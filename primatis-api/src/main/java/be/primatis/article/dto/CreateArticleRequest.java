package be.primatis.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contrat REST de création staff d'un {@code Article} (préparation
 * DEV-11.3, exposition HTTP différée à DEV-11.6, {@code ARTICLE_MANAGE}).
 * Volontairement absent : {@code id}, {@code slug} (généré backend,
 * business-rules.md §7.8, jamais fourni par le client), {@code
 * articleStatus} (toujours {@code DRAFT} à la création, imposé backend,
 * business-rules.md §7.2), {@code authorUser} (dérivé de l'utilisateur
 * authentifié, jamais fourni par le client, DEV-DEC-0059), {@code
 * lastModifiedByUser} ({@code null} à la création, DEV-DEC-0059), {@code
 * publishedAt}/{@code createdAt}/{@code updatedAt} (jamais fournis par le
 * client).
 *
 * <p>{@code tagIds} n'est volontairement pas inclus ici : DEV-DEC-0060
 * fixe qu'un Article n'associe que des Tags déjà existants, mais
 * l'association elle-même est traitée par DEV-11.9 (CRUD Tag +
 * association/dissociation), pas prématurément ici (current-task.md,
 * mission DEV-11.3 §7/§9 : ne pas anticiper le périmètre DEV-11.9). Une
 * association de Tags à la création pourra être ajoutée à ce contrat en
 * DEV-11.9 sans remettre en cause les champs présents ici.
 *
 * <p>{@code title} : {@code @Size(max = 255)} reflète strictement {@code
 * article.title VARCHAR(255)} (V001) — même précédent que {@code
 * user.web.UpdateResidenceRequest} (alignement explicite sur une contrainte
 * DB réelle), contrairement à {@code catalogue.dto.CreateTitleRequest.title}
 * qui n'a jamais porté cette annotation malgré une colonne de même
 * longueur ; aucune contradiction, les deux styles coexistent déjà dans le
 * projet et {@code database-model.md}/mission DEV-11.3 §10 demandent
 * explicitement l'alignement sur la contrainte DB réelle ici. {@code
 * content} : {@code TEXT} en base, aucune limite de longueur ne doit être
 * inventée. {@code summary} : {@code TEXT} nullable, facultatif, aucune
 * contrainte de longueur.
 *
 * <p>La règle « {@code content} reste fonctionnellement non vide après
 * sanitization » (business-rules.md §7.6, architecture.md §18)
 * n'appartient pas à ce DTO : elle est contrôlée après sanitization par le
 * futur {@code ArticleService} (DEV-11.4+), pas exprimable en Bean
 * Validation simple sans dépendance au résultat de la sanitization.
 */
public record CreateArticleRequest(@NotBlank @Size(max = 255) String title, @NotBlank String content, String summary) {
}
