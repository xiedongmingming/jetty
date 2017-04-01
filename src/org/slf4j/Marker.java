package org.slf4j;

import java.io.Serializable;
import java.util.Iterator;

public interface Marker extends Serializable {

    public final String ANY_MARKER = "*";
    public final String ANY_NON_NULL_MARKER = "+";

    public String getName();
    public void add(Marker reference);
    public boolean remove(Marker reference);

    /**
     * @deprecated Replaced by {@link #hasReferences()}.
     */
    public boolean hasChildren();
    public boolean hasReferences();
    public Iterator<Marker> iterator();
    public boolean contains(Marker other);
    public boolean contains(String name);
    public boolean equals(Object o);
    public int hashCode();
}
