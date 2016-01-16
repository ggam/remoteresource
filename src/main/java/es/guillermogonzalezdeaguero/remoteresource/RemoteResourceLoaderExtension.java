package es.guillermogonzalezdeaguero.remoteresource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 *
 * @author Guillermo González de Agüero
 */
public class RemoteResourceLoaderExtension implements Extension {

    private static final Logger logger = Logger.getLogger(RemoteResourceLoaderExtension.class.getName());

    public <T> void initializePropertyLoading(final @Observes ProcessInjectionTarget<T> pit) {
        AnnotatedType<T> at = pit.getAnnotatedType();

        boolean anyMatch = at.getFields().
                stream().
                anyMatch(f -> f.isAnnotationPresent(RemoteResource.class));
        if (!anyMatch) {
            return;
        }

        Map<String, Object> cachedLookups = new HashMap<>();
        at.getFields().stream().
                filter(f -> f.isAnnotationPresent(RemoteResource.class)).
                forEach(annotatedField -> {
                    RemoteResource remoteResource = annotatedField.getAnnotation(RemoteResource.class);
                    Field field = annotatedField.getJavaMember();

                    if (remoteResource.validateOnDeployment()) {
                        try {
                            lookupAndValidateAndCache(remoteResource, field, cachedLookups);
                        } catch (Exception e) {
                            pit.addDefinitionError(new InjectionException(e));
                        }
                    }

                });

        final InjectionTarget<T> it = pit.getInjectionTarget();
        InjectionTarget<T> wrapped = new InjectionTarget<T>() {
            @Override
            @SuppressWarnings("unchecked")
            public void inject(T instance, CreationalContext<T> ctx) {
                it.inject(instance, ctx);

                for (Field field : instance.getClass().getDeclaredFields()) {
                    if (field.isAnnotationPresent(RemoteResource.class)) {
                        RemoteResource annotation = field.getAnnotation(RemoteResource.class);

                        field.setAccessible(true);

                        try {
                            T value = lookupAndValidateAndCache(annotation, field, cachedLookups);
                            field.set(instance, value);
                        } catch (Throwable e) {
                            throw new InjectionException(e);
                        }
                    }
                }
            }

            @Override
            public void postConstruct(T instance) {
                it.postConstruct(instance);
            }

            @Override
            public void preDestroy(T instance) {
                it.dispose(instance);
            }

            @Override
            public void dispose(T instance) {
                it.dispose(instance);
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return it.getInjectionPoints();
            }

            @Override
            public T produce(CreationalContext<T> ctx) {
                return it.produce(ctx);
            }
        };

        pit.setInjectionTarget(wrapped);
    }

    private <T> T lookupAndValidateAndCache(RemoteResource annotation, Field field, Map<String, Object> cache) throws NamingException {
        String key = annotation.externalContextLookup() + annotation.lookup();

        T value;
        if (annotation.cache()) {
            value = (T) cache.get(key);
            if (value == null) {
                value = performLookup(annotation);
                validateValue(field, value);
                cache.put(key, value);
            }
        } else {
            value = performLookup(annotation);
            validateValue(field, value);
        }

        return value;
    }

    private <T> T performLookup(RemoteResource annotation) throws NamingException {
        String externalContextLookup = annotation.externalContextLookup();
        String jndiLookup = annotation.lookup();

        InitialContext context = InitialContext.doLookup(externalContextLookup);

        return (T) context.lookup(jndiLookup);
    }

    private <T> void validateValue(Field field, T value) {
        if (value == null) {
            throw new IllegalArgumentException("No value found for field " + field.toString());
        }

        if (!field.getType().isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("Incompatible value type for field " + field.toString());
        }
    }
}
