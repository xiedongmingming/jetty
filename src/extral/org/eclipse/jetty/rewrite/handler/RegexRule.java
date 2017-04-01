package extral.org.eclipse.jetty.rewrite.handler;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class RegexRule extends Rule {// 正则表达式处理

    protected Pattern _regex; 
    
	// **********************************************************************************************
	protected RegexRule() {

    }
	protected RegexRule(String pattern) {
        setRegex(pattern);
    }
	// **********************************************************************************************
	public void setRegex(String regex) {
		_regex = regex == null ? null : Pattern.compile(regex);
    }
	public String getRegex() {
		return _regex == null ? null : _regex.pattern();
    }
	// **********************************************************************************************
    @Override
	public String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Matcher matcher=_regex.matcher(target);
        boolean matches = matcher.matches();
		if (matches) {
			return apply(target, request, response, matcher);
		}
        return null;
    }
    @Override
	public String toString() {
		return super.toString() + "[" + _regex + "]";
    }
	// **********************************************************************************************
	protected abstract String apply(String target, HttpServletRequest request, HttpServletResponse response, Matcher matcher) throws IOException;
	// **********************************************************************************************
}
