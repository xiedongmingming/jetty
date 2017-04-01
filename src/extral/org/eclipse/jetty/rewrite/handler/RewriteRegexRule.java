package extral.org.eclipse.jetty.rewrite.handler;

import java.io.IOException;
import java.util.regex.Matcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.annotation.Name;

public class RewriteRegexRule extends RegexRule implements Rule.ApplyURI {

    private String _replacement;
    private String _query;
    private boolean _queryGroup;

	// *******************************************************************************************************
	public RewriteRegexRule() {
		this(null, null);
    }
	public RewriteRegexRule(@Name("regex") String regex, @Name("replacement") String replacement) {
        setHandling(false);
        setTerminating(false);
        setRegex(regex);
        setReplacement(replacement);
    }
	// *******************************************************************************************************
	public void setReplacement(String replacement) {
		if (replacement == null) {
			_replacement = null;
			_query = null;
			_queryGroup = false;
		} else {
			String[] split = replacement.split("\\?", 2);
            _replacement = split[0];
			_query = split.length == 2 ? split[1] : null;
			_queryGroup = _query != null && _query.contains("$Q");
        }
    }
	// *******************************************************************************************************
    @Override
	public String apply(String target, HttpServletRequest request, HttpServletResponse response, Matcher matcher) throws IOException {

		target = _replacement;

		String query = _query;

		for (int g = 1; g <= matcher.groupCount(); g++) {
			String group = matcher.group(g);
			if (group == null) {
				group = "";
			} else {
				group = Matcher.quoteReplacement(group);
			}
			target = target.replaceAll("\\$" + g, group);
			if (query != null) {
				query = query.replaceAll("\\$" + g, group);
			}
        }

		if (query != null) {
			if (_queryGroup) {
				query = query.replace("$Q", request.getQueryString() == null ? "" : request.getQueryString());
			}
			request.setAttribute("org.eclipse.jetty.rewrite.handler.RewriteRegexRule.Q", query);
        }

        return target;
    }
    @Override
	public void applyURI(Request request, String oldURI, String newURI) throws IOException {

		if (_query == null) {
            request.setURIPathQuery(newURI);
		} else {

			String query = (String) request.getAttribute("org.eclipse.jetty.rewrite.handler.RewriteRegexRule.Q");
            
			if (!_queryGroup && request.getQueryString() != null) {
				query = request.getQueryString() + "&" + query;
			}
            request.setURIPathQuery(newURI);
            request.setQueryString(query);
        }
    }
    @Override
	public String toString() {
		return super.toString() + "[" + _replacement + "]";
    }
	// *******************************************************************************************************
}
