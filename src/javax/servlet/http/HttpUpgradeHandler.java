package javax.servlet.http;

public interface HttpUpgradeHandler {
    public void init(WebConnection wc);
    public void destroy();
}
