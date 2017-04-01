package javax.servlet.http;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class Cookie implements Cloneable, Serializable {

    private static final long serialVersionUID = -6454587001725327448L;

    private static final String TSPECIALS;

	private static final String LSTRING_FILE = "javax.servlet.http.LocalStrings";

	private static ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

	static {// ?????
        if (Boolean.valueOf(System.getProperty("org.glassfish.web.rfc2109_cookie_names_enforced", "true"))) {
            TSPECIALS = "/()<>@,;:\\\"[]?={} \t";
        } else {
            TSPECIALS = ",; ";
        }
    }

	// ****************************************************
	// 一个COOKIE所具有的值
	private String name;// 必须
	private String value;
	private String comment;
	private String domain;
	private int maxAge = -1;
	private String path;
	private boolean secure;
	private int version = 0;
    private boolean isHttpOnly = false;
	// ****************************************************

    public Cookie(String name, String value) {
        if (name == null || name.length() == 0) {
			throw new IllegalArgumentException(lStrings.getString("err.cookie_name_blank"));
        }
        if (!isToken(name) ||
                name.equalsIgnoreCase("Comment") || // rfc2019
                name.equalsIgnoreCase("Discard") || // 2019++
                name.equalsIgnoreCase("Domain") ||
                name.equalsIgnoreCase("Expires") || // (old cookies)
                name.equalsIgnoreCase("Max-Age") || // rfc2019
                name.equalsIgnoreCase("Path") ||
                name.equalsIgnoreCase("Secure") ||
                name.equalsIgnoreCase("Version") ||
                name.startsWith("$")) {
            String errMsg = lStrings.getString("err.cookie_name_is_token");
            Object[] errArgs = new Object[1];
            errArgs[0] = name;
            errMsg = MessageFormat.format(errMsg, errArgs);
            throw new IllegalArgumentException(errMsg);
        }
        this.name = name;
        this.value = value;
    }
    public void setComment(String purpose) {
        comment = purpose;
    }
    public String getComment() {
        return comment;
    }
    public void setDomain(String domain) {
        this.domain = domain.toLowerCase(Locale.ENGLISH); // IE allegedly needs this
    }
    public String getDomain() {
        return domain;
    }
    public void setMaxAge(int expiry) {
        maxAge = expiry;
    }
    public int getMaxAge() {
        return maxAge;
    }
    public void setPath(String uri) {
        path = uri;
    }
    public String getPath() {
        return path;
    }
    public void setSecure(boolean flag) {
        secure = flag;
    }
    public boolean getSecure() {
        return secure;
    }
    public String getName() {
        return name;
    }
    public void setValue(String newValue) {
        value = newValue;
    }
    public String getValue() {
        return value;
    }
    public int getVersion() {
        return version;
    }
    public void setVersion(int v) {
        version = v;
    }
    private boolean isToken(String value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
			if (c < 0x20 || c >= 0x7f || TSPECIALS.indexOf(c) != -1) {// 特殊字符
                return false;
            }
        }
        return true;
    }
    @Override
	public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
    public void setHttpOnly(boolean isHttpOnly) {
        this.isHttpOnly = isHttpOnly;
    }
    public boolean isHttpOnly() {
        return isHttpOnly;
    }
}

