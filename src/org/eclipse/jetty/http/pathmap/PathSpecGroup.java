package org.eclipse.jetty.http.pathmap;

public enum PathSpecGroup
{
    // NOTE: Order of enums determines order of Groups.

    /**
     * For exactly defined path specs, no glob.
     */
    EXACT,
    /**
     * For path specs that have a hardcoded prefix and suffix with wildcard glob in the middle.
     * 
     * <pre>
     *   "^/downloads/[^/]*.zip$"  - regex spec
     *   "/a/{var}/c"              - uri-template spec
     * </pre>
     * 
     * Note: there is no known servlet spec variant of this kind of path spec
     */
    MIDDLE_GLOB,
    /**
     * For path specs that have a hardcoded prefix and a trailing wildcard glob.
     * <p>
     * 
     * <pre>
     *   "/downloads/*"          - servlet spec
     *   "/api/*"                - servlet spec
     *   "^/rest/.*$"            - regex spec
     *   "/bookings/{guest-id}"  - uri-template spec
     *   "/rewards/{vip-level}"  - uri-template spec
     * </pre>
     */
    PREFIX_GLOB,
    /**
     * For path specs that have a wildcard glob with a hardcoded suffix
     * 
     * <pre>
     *   "*.do"        - servlet spec
     *   "*.css"       - servlet spec
     *   "^.*\.zip$"   - regex spec
     * </pre>
     * 
     * Note: there is no known uri-template spec variant of this kind of path spec
     */
    SUFFIX_GLOB,
    /**
     * The root spec for accessing the Root behavior.
     * 
     * <pre>
     *   ""           - servlet spec       (Root Servlet)
     *   null         - servlet spec       (Root Servlet)
     * </pre>
     * 
     * Note: there is no known uri-template spec variant of this kind of path spec
     */
    ROOT,
    /**
     * The default spec for accessing the Default path behavior.
     * 
     * <pre>
     *   "/"           - servlet spec      (Default Servlet)
     *   "/"           - uri-template spec (Root Context)
     *   "^/$"         - regex spec        (Root Context)
     * </pre>
     * 
     * Per Servlet Spec, pathInfo is always null for these specs.
     * If nothing above matches, then default will match.
     */
    DEFAULT,
}