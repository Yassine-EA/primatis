package be.primatis.user.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valide un numéro de téléphone via {@link PhoneNumberConstraintValidator}
 * (DEV-05.9). {@code null}/blanc sont toujours valides pour cette contrainte
 * — {@code UpdateMeProfileRequest.phoneNumber} absent/vide n'est jamais une
 * erreur de validation (DEV-05.9-DEC-06/DEC-13), c'est {@link
 * be.primatis.user.MeProfileService} qui décide alors de ne rien modifier.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberConstraintValidator.class)
public @interface ValidPhoneNumber {

    String message() default "Numéro de téléphone invalide.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
