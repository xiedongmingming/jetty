package extral.org.eclipse.jetty.rewrite.handler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.PathMap;

@SuppressWarnings("deprecation")
public abstract class PatternRule extends Rule {// 使用的是SERVLET的模式语法

    protected String _pattern;

	protected PatternRule() {
    }
	protected PatternRule(String pattern) {
        this();
        setPattern(pattern);
    }
	public String getPattern() {
        return _pattern;
    }

	public void setPattern(String pattern) {
        _pattern = pattern;
    }

	/*
	 * @see
	 * org.eclipse.jetty.server.server.handler.rules.RuleBase#matchAndApply(
	 * javax.servlet.http.HttpServletRequest,
	 * javax.servlet.http.HttpServletResponse)
	 */
    @Override
	public String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		if (PathMap.match(_pattern, target)) {
            return apply(target,request, response);
        }
        return null;
    }

    protected abstract String apply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException;

    @Override
	public String toString() {
		return super.toString() + "[" + _pattern + "]";
    }
}
