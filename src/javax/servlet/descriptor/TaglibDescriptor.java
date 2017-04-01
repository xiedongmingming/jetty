package javax.servlet.descriptor;

/**
 * This interface provides access to the <code>&lt;taglib&gt;</code>
 * related configuration of a web application.
 *
 * <p>The configuration is aggregated from the <code>web.xml</code> and
 * <code>web-fragment.xml</code> descriptor files of the web application.
 *
 * @since Servlet 3.0
 */
public interface TaglibDescriptor {
  
    /**
     * Gets the unique identifier of the tag library represented by this
     * TaglibDescriptor.
     *  
     * @return the unique identifier of the tag library represented by this
     * TaglibDescriptor
     */
    public String getTaglibURI();

    /**
     * Gets the location of the tag library represented by this
     * TaglibDescriptor.
     *  
     * @return the location of the tag library represented by this
     * TaglibDescriptor
     */    
    public String getTaglibLocation();    
}
