package extral.org.eclipse.jetty.xml;

import java.net.URL;

public interface ConfigurationProcessor {
	public void init(URL url, XmlParser.Node root, XmlConfiguration configuration);// 初始化该处理器
	public Object configure(Object obj) throws Exception;// 进行配置运算
    public Object configure() throws Exception;
}
