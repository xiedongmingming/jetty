package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.servlet.DispatcherType;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Annotation used to declare a servlet filter.
 *
 * <p>This annotation is processed by the container at deployment time,
 * and the corresponding filter applied to the specified URL patterns,
 * servlets, and dispatcher types.
 * 
 * @see javax.servlet.Filter
 *
 * @since Servlet 3.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebFilter {

    /**
     * The description of the filter
     */
    String description() default "";
    
    /**
     * The display name of the filter
     */
    String displayName() default "";
    
    /**
     * The init parameters of the filter
     */
    WebInitParam[] initParams() default {};
    
    /**
     * The name of the filter
     */
    String filterName() default "";
    
    /**
     * The small-icon of the filter
     */
    String smallIcon() default "";

    /**
     * The large-icon of the filter
     */
    String largeIcon() default "";

    /**
     * The names of the servlets to which the filter applies.
     */
    String[] servletNames() default {};
    
    /**
     * The URL patterns to which the filter applies
     */
    String[] value() default {};

    /**
     * The URL patterns to which the filter applies
     */
    String[] urlPatterns() default {};

    /**
     * The dispatcher types to which the filter applies
     */
    DispatcherType[] dispatcherTypes() default {DispatcherType.REQUEST};
    
    /**
     * Declares whether the filter supports asynchronous operation mode.
     *
     * @see javax.servlet.ServletRequest#startAsync
     * @see javax.servlet.ServletRequest#startAsync(ServletRequest,
     * ServletResponse)
     */
    boolean asyncSupported() default false;

}
