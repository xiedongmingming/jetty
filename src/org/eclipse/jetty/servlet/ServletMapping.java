package org.eclipse.jetty.servlet;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;

@ManagedObject("Servlet Mapping")
public class ServletMapping {

	private String[] _pathSpecs;// 对应的路径
	private String _servletName;// SERVLET实现的类名
    private boolean _default;
    
	public ServletMapping() {
    }
    
	@ManagedAttribute(value = "url patterns", readonly = true)
	public String[] getPathSpecs() {
        return _pathSpecs;
    }
	@ManagedAttribute(value = "servlet name", readonly = true)
	public String getServletName() {
        return _servletName;
    }

	public void setPathSpecs(String[] pathSpecs) {
        _pathSpecs = pathSpecs;
    }

	public boolean containsPathSpec(String pathSpec) {
		if (_pathSpecs == null || _pathSpecs.length == 0) {
			return false;
		}
		for (String p : _pathSpecs) {
			if (p.equals(pathSpec)) {
				return true;
			}
        }
        return false;
    }

	public void setPathSpec(String pathSpec) {
        _pathSpecs = new String[]{pathSpec};
    }

	public void setServletName(String servletName) {
        _servletName = servletName;
    }

	@ManagedAttribute(value = "default", readonly = true)
	public boolean isDefault() {
        return _default;
    }

	public void setDefault(boolean fromDefault) {
        _default = fromDefault;
    }
    @Override
	public String toString() {
		return (_pathSpecs == null ? "[]" : Arrays.asList(_pathSpecs).toString()) + "=>" + _servletName;
    }

	public void dump(Appendable out, String indent) throws IOException {
        out.append(String.valueOf(this)).append("\n");
    }
}
