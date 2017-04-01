package org.eclipse.jetty.http.pathmap;

import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;

@ManagedObject("Mapped Resource")
public class MappedResource<E> implements Comparable<MappedResource<E>> {
    private final PathSpec pathSpec;
    private final E resource;

	public MappedResource(PathSpec pathSpec, E resource) {
        this.pathSpec = pathSpec;
        this.resource = resource;
    }
    @Override
	public int compareTo(MappedResource<E> other) {
        return this.pathSpec.compareTo(other.pathSpec);
    }

    @Override
	public boolean equals(Object obj) {
		if (this == obj) {
            return true;
        }
		if (obj == null) {
            return false;
        }
		if (getClass() != obj.getClass()) {
            return false;
        }
        MappedResource<?> other = (MappedResource<?>)obj;
		if (pathSpec == null) {
			if (other.pathSpec != null) {
                return false;
            }
		} else if (!pathSpec.equals(other.pathSpec)) {
            return false;
        }
        return true;
    }

    @ManagedAttribute(value = "path spec", readonly = true)
	public PathSpec getPathSpec() {
        return pathSpec;
    }

    @ManagedAttribute(value = "resource", readonly = true)
	public E getResource() {
        return resource;
    }

    @Override
	public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((pathSpec == null) ? 0 : pathSpec.hashCode());
        return result;
    }

    @Override
	public String toString() {
        return String.format("MappedResource[pathSpec=%s,resource=%s]",pathSpec,resource);
    }
}