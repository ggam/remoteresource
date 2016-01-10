package es.guillermogonzalezdeaguero.remoteresource;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 *
 * @author Guillermo González de Agüero
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface RemoteResource {

    /**
     * The JNDI lookup to make in order to retrieve the external InitialContext
     *
     * @return
     */
    String externalContextLookup();

    /**
     * JNDI lookup to perform to retrieve the resource
     *
     * @return
     */
    String lookup();

    /**
     * Cache the value on deployment and do not lookup it again
     *
     * @return
     */
    boolean cache() default true;

    /**
     * Validate the presence and the type of the object
     *
     * @return
     */
    boolean validateOnDeployment() default true;
}
