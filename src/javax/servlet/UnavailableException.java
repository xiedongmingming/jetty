package javax.servlet;

public class UnavailableException extends ServletException {

	private static final long serialVersionUID = 5622686609215003468L;

	private Servlet servlet;

    private boolean permanent;
    private int seconds;

    public UnavailableException(Servlet servlet, String msg) {
		super(msg);
		this.servlet = servlet;
		permanent = true;
    }
    public UnavailableException(int seconds, Servlet servlet, String msg) {
		super(msg);
		this.servlet = servlet;
		if (seconds <= 0)
			this.seconds = -1;
		else
			this.seconds = seconds;
		permanent = false;
    }
    public UnavailableException(String msg) {
		super(msg);
		permanent = true;
    }
    public UnavailableException(String msg, int seconds) {
		super(msg);
		if (seconds <= 0)
			this.seconds = -1;
		else
			this.seconds = seconds;
		permanent = false;
    }
    public boolean isPermanent() {
		return permanent;
    }
    public Servlet getServlet() {
		return servlet;
    }
    public int getUnavailableSeconds() {
		return permanent ? -1 : seconds;
    }
}
