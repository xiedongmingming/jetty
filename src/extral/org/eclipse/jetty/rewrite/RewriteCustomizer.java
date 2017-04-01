package extral.org.eclipse.jetty.rewrite;

import java.io.IOException;

import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConfiguration.Customizer;
import org.eclipse.jetty.server.Request;

import extral.org.eclipse.jetty.rewrite.handler.RuleContainer;

public class RewriteCustomizer extends RuleContainer implements Customizer {// 表示重写的定制化(本身也是一个容器)

	// 所有定制化处理的地方: HttpChannel.handle

    @Override
	public void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
		try {
			matchAndApply(request.getPathInfo(), request, request.getResponse());// 父类的函数
		} catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
