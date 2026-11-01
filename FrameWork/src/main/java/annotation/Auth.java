package annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation permettant de restreindre l'accès à une méthode ou à un contrôleur
 * aux utilisateurs connectés en session.
 *
 * Exemples d'utilisation :
 * - @Auth : nécessite uniquement que l'utilisateur soit connecté.
 * - @Auth("admin") : nécessite que l'utilisateur possède le rôle "admin".
 * - @Auth({"admin", "manager"}) : nécessite l'un des rôles indiqués.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {
    String[] value() default {};
    String[] role() default {};
}
