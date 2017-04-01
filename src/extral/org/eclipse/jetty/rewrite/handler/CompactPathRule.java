package extral.org.eclipse.jetty.rewrite.handler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class CompactPathRule extends Rule implements Rule.ApplyURI {// 表示一种重写规则

	private static final Logger LOG = Log.getLogger(CompactPathRule.class);

	// *********************************************************************************************
	public CompactPathRule() {
        _handling = false;
        _terminating = false;
    }

	// *********************************************************************************************
    @Override
	public void applyURI(Request request, String oldURI, String newURI) throws IOException {

        String uri = request.getRequestURI();

		LOG.info("测试: " + uri);

		if (uri.startsWith("/")) {
			uri = URIUtil.compactPath(uri);// ?????
		}

        request.setURIPathQuery(uri);
    }
    @Override
	public String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (target.startsWith("/")) {
			return URIUtil.compactPath(target);
		}
        return target;
    }
	// *********************************************************************************************
}
