package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used on a Servlet or Filter implementation class
 * to specify an initialization parameter.
 * 
 * @since Servlet 3.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebInitParam {

    /**
     * Name of the initialization parameter
     */
    String name();
    
    /**
     * Value of the initialization parameter
     */    
    String value();
    
    /**
     * Description of the initialization parameter
     */
    String description() default "";
}
