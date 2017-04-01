package extral.org.eclipse.jetty.rewrite.handler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.ArrayUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class RuleContainer extends Rule {// 重写的所有规则(规则容器)

    public static final String ORIGINAL_QUERYSTRING_ATTRIBUTE_SUFFIX = ".QUERYSTRING";

    private static final Logger LOG = Log.getLogger(RuleContainer.class);

	protected Rule[] _rules;// 包含的所有规则
    
	protected String _originalPathAttribute;// 控制是否需要将原始请求路径设置到REQUEST中(作为一个属性)
	protected String _originalQueryStringAttribute;// 上面属性加上.QUERYSTRING

	protected boolean _rewriteRequestURI = true;// 重写URI
	protected boolean _rewritePathInfo = true;//

	// ******************************************************************************************
	public Rule[] getRules() {
        return _rules;
    }
	public void setRules(Rule[] rules) {
        _rules = rules;
    }
	public void addRule(Rule rule) {//
		_rules = ArrayUtil.addToArray(_rules, rule, Rule.class);
    }
	public boolean isRewriteRequestURI() {
        return _rewriteRequestURI;
    }
	public void setRewriteRequestURI(boolean rewriteRequestURI) {
		_rewriteRequestURI = rewriteRequestURI;
    }
	public boolean isRewritePathInfo() {
        return _rewritePathInfo;
    }
	public void setRewritePathInfo(boolean rewritePathInfo) {
		_rewritePathInfo = rewritePathInfo;
    }
	public String getOriginalPathAttribute() {
        return _originalPathAttribute;
    }
	public void setOriginalPathAttribute(String originalPathAttribte) {
		_originalPathAttribute = originalPathAttribte;
		_originalQueryStringAttribute = originalPathAttribte + ORIGINAL_QUERYSTRING_ATTRIBUTE_SUFFIX;
    }
	// ******************************************************************************************
    @Override
    public String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return apply(target, request, response);
    }
	// ******************************************************************************************
	protected String apply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {

		// 第一个参数表示请求URL的路径部分(主机名后面的部分)

		boolean original_set = _originalPathAttribute == null;
        
		if (_rules == null) {
			return target;
		}
        
		for (Rule rule : _rules) {// 遍历每个重写规则

			String applied = rule.matchAndApply(target, request, response);

			if (applied != null) {

				if (!original_set) {// 是否设置过(没有设置)
					original_set = true;
                    request.setAttribute(_originalPathAttribute, target);
                    
                    String query = request.getQueryString();
					if (query != null) {
						request.setAttribute(_originalQueryStringAttribute, query);
					}
                }     

				if (_rewriteRequestURI) {
					String encoded = URIUtil.encodePath(applied);
					if (rule instanceof Rule.ApplyURI) {
						((Rule.ApplyURI) rule).applyURI((Request) request, ((Request) request).getRequestURI(), encoded);
					} else {
						((Request) request).setURIPathQuery(encoded);
					}
                }

				if (_rewritePathInfo) {
					((Request) request).setPathInfo(applied);
				}

				target = applied;

				if (rule.isHandling()) {
					LOG.debug("handling {}", rule);
                    Request.getBaseRequest(request).setHandled(true);
                }

				if (rule.isTerminating()) {
					LOG.debug("terminating {}", rule);
                    break;
                }
            }
        }
        return target;
    }
	// ******************************************************************************************
}
