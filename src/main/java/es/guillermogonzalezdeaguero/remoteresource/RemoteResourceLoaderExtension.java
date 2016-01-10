package es.guillermogonzalezdeaguero.remoteresource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.AnnotatedField;
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

        Map<AnnotatedField, Object> fieldValues = new HashMap<>();
        at.getFields().stream().
                filter(f -> f.isAnnotationPresent(RemoteResource.class)).
                forEach(annotatedField -> {
                    RemoteResource remoteResource = annotatedField.getAnnotation(RemoteResource.class);
                    Field field = annotatedField.getJavaMember();

                    if (remoteResource.validateOnDeployment()) {
                        // Only check non-runtime fields
                        try {
                            T value = performLookup(remoteResource);
                            validateValue(field, value);

                            if (remoteResource.cache()) {
                                // Store the value only if caching is enabled
                                fieldValues.put(annotatedField, value);
                            }
                        } catch (Exception e) {
                            pit.addDefinitionError(new InjectionException(e));
                        }
                    } else {
                        fieldValues.put(annotatedField, null);
                    }

                });

        final InjectionTarget<T> it = pit.getInjectionTarget();
        InjectionTarget<T> wrapped = new InjectionTarget<T>() {
            @Override
            public void inject(T instance, CreationalContext<T> ctx) {
                it.inject(instance, ctx);

                for (Entry<AnnotatedField, Object> object : fieldValues.entrySet()) {
                    Field field = object.getKey().getJavaMember();
                    field.setAccessible(true);

                    T value = (T) object.getValue();

                    if (value == null) {
                        try {
                            RemoteResource annotation = object.getKey().getAnnotation(RemoteResource.class);

                            value = performLookup(object.getKey().getAnnotation(RemoteResource.class));
                            validateValue(field, value);

                            if (annotation.cache()) {
                                // Cache the value if needed
                                fieldValues.put(object.getKey(), value);
                            }
                        } catch (Exception e) {
                            throw new InjectionException(e);
                        }
                    }

                    try {
                        field.set(instance, value);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new InjectionException(e);
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
