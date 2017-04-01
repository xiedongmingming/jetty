package javax.servlet.descriptor;

import java.util.Collection;

/**
 * This interface provides access to the <code>&lt;jsp-config&gt;</code>
 * related configuration of a web application.
 *
 * <p>The configuration is aggregated from the <code>web.xml</code> and
 * <code>web-fragment.xml</code> descriptor files of the web application.
 *
 * @since Servlet 3.0
 */
public interface JspConfigDescriptor {

    /**
     * Gets the <code>&lt;taglib&gt;</code> child elements of the
     * <code>&lt;jsp-config&gt;</code> element represented by this
     * <code>JspConfigDescriptor</code>.
     *
     * <p>Any changes to the returned <code>Collection</code> must not 
     * affect this <code>JspConfigDescriptor</code>.
     *
     * @return a (possibly empty) <code>Collection</code> of the
     * <code>&lt;taglib<&gt;/code> child elements of the
     * <code>&lt;jsp-config&gt;</code> element represented by this
     * <code>JspConfigDescriptor</code>
     */
    public Collection<TaglibDescriptor> getTaglibs();

    /**
     * Gets the <code>&lt;jsp-property-group&gt;</code> child elements
     * of the <code>&lt;jsp-config&gt;</code> element represented by this
     * <code>JspConfigDescriptor</code>.
     *
     * <p>Any changes to the returned <code>Collection</code> must not 
     * affect this <code>JspConfigDescriptor</code>.
     *
     * @return a (possibly empty) <code>Collection</code> of the
     * <code>&lt;jsp-property-group&gt;</code> child elements of the
     * <code>&lt;jsp-config&gt;</code> element represented by this
     * <code>JspConfigDescriptor</code>
     */
    public Collection<JspPropertyGroupDescriptor> getJspPropertyGroups();
}
