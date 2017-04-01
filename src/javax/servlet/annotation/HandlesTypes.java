package javax.servlet.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to declare the class types that a
 * {@link javax.servlet.ServletContainerInitializer
 * ServletContainerInitializer} can handle.
 *
 * @see javax.servlet.ServletContainerInitializer
 *
 * @since Servlet 3.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HandlesTypes {

    /**
     * The classes in which a {@link javax.servlet.ServletContainerInitializer
     * ServletContainerInitializer} has expressed interest.
     *
     * <p>If an implementation of <tt>ServletContainerInitializer</tt> 
     * specifies this annotation, the Servlet container must pass the
     * <tt>Set</tt> of application classes that extend, implement, or have
     * been annotated with the class types listed by this annotation to
     * the {@link javax.servlet.ServletContainerInitializer#onStartup}
     * method of the ServletContainerInitializer (if no matching classes
     * are found, <tt>null</tt> must be passed instead)
     */
    Class<?>[] value();
}
