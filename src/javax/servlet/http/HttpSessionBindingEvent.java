package javax.servlet.http;

public class HttpSessionBindingEvent extends HttpSessionEvent {

    private static final long serialVersionUID = 7308000419984825907L;

    /* The name to which the object is being bound or unbound */
    private String name;
    
    /* The object is being bound or unbound */
    private Object value;

    public HttpSessionBindingEvent(HttpSession session, String name) {
        super(session);
        this.name = name;
    }

    public HttpSessionBindingEvent(HttpSession session, String name, Object value) {
        super(session);
        this.name = name;
        this.value = value;
    }
    @Override
    public HttpSession getSession () { 
        return super.getSession();
    }

    public String getName() {
        return name;
    }
    public Object getValue() {
        return this.value;   
    }
}
