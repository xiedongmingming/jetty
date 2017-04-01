package javax.servlet;

import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import javax.servlet.annotation.ServletSecurity.TransportGuarantee;

public class HttpConstraintElement {

    private EmptyRoleSemantic emptyRoleSemantic;
    private TransportGuarantee transportGuarantee;
    private String[] rolesAllowed;

    public HttpConstraintElement() {
        this(EmptyRoleSemantic.PERMIT);
    }
    public HttpConstraintElement(EmptyRoleSemantic semantic) {
        this(semantic, TransportGuarantee.NONE, new String[0]);
    }
    public HttpConstraintElement(TransportGuarantee guarantee, String... roleNames) {
        this(EmptyRoleSemantic.PERMIT, guarantee, roleNames);
    }
    public HttpConstraintElement(EmptyRoleSemantic semantic, TransportGuarantee guarantee, String... roleNames) {
        if (semantic == EmptyRoleSemantic.DENY && roleNames.length > 0) {
            throw new IllegalArgumentException("Deny semantic with rolesAllowed");
        }
        this.emptyRoleSemantic = semantic;
        this.transportGuarantee = guarantee;
        this.rolesAllowed = copyStrings(roleNames);
    }

    public EmptyRoleSemantic getEmptyRoleSemantic() {
        return this.emptyRoleSemantic;
    }
    public TransportGuarantee getTransportGuarantee() {
        return this.transportGuarantee;
    }
    public String[] getRolesAllowed() {
        return copyStrings(this.rolesAllowed);
    }
    private String[] copyStrings(String[] strings) {
        String[] arr = null;
        if (strings != null) {
            int len = strings.length;
            arr = new String[len];
            if (len > 0) {
                System.arraycopy(strings, 0, arr, 0, len);
            }
        }
        return ((arr != null) ? arr : new String[0]);
    }
}
