package org.eclipse.jetty.security;

import org.eclipse.jetty.util.security.Constraint;

public class ConstraintMapping {
    String _method;
    String[] _methodOmissions;

    String _pathSpec;

    Constraint _constraint;

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the constraint.
     */
    public Constraint getConstraint() {
        return _constraint;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param constraint The constraint to set.
     */
    public void setConstraint(Constraint constraint)
    {
        this._constraint = constraint;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the method.
     */
    public String getMethod()
    {
        return _method;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param method The method to set.
     */
    public void setMethod(String method)
    {
        this._method = method;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the pathSpec.
     */
    public String getPathSpec()
    {
        return _pathSpec;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param pathSpec The pathSpec to set.
     */
    public void setPathSpec(String pathSpec)
    {
        this._pathSpec = pathSpec;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param omissions The http-method-omission
     */
    public void setMethodOmissions(String[] omissions)
    {
        _methodOmissions = omissions;
    }
    
    /* ------------------------------------------------------------ */
    public String[] getMethodOmissions()
    {
        return _methodOmissions;
    }
}
