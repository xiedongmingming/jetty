package javax.servlet.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that may be specified on a {@link javax.servlet.Servlet}
 * class, indicating that instances of the <tt>Servlet</tt> expect requests
 * that conform to the <tt>multipart/form-data</tt> MIME type.
 *
 * <p>Servlets annotated with <tt>MultipartConfig</tt> may retrieve the
 * {@link javax.servlet.http.Part} components of a given
 * <tt>multipart/form-data</tt> request by calling 
 * {@link javax.servlet.http.HttpServletRequest#getPart getPart} or
 * {@link javax.servlet.http.HttpServletRequest#getParts getParts}.
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MultipartConfig {

    /**
     * The directory location where files will be stored
     */
    String location() default "";

    /**
     * The maximum size allowed for uploaded files.
     * 
     * <p>The default is <tt>-1L</tt>, which means unlimited.
     */
    long maxFileSize() default -1L;

    /**
     * The maximum size allowed for <tt>multipart/form-data</tt>
     * requests
     * 
     * <p>The default is <tt>-1L</tt>, which means unlimited.
     */
    long maxRequestSize() default -1L;

    /**
     * The size threshold after which the file will be written to disk
     */
    int fileSizeThreshold() default 0;
}
