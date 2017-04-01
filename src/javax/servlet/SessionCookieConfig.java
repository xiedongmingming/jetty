package javax.servlet;

public interface SessionCookieConfig {

    public void setName(String name);
    public String getName();

    public void setDomain(String domain);
    public String getDomain();

    public void setPath(String path);
    public String getPath();

    public void setComment(String comment);
    public String getComment();

    public void setHttpOnly(boolean httpOnly);
    public boolean isHttpOnly();

    public void setSecure(boolean secure);
    public boolean isSecure();

    public void setMaxAge(int maxAge);
    public int getMaxAge();
}
