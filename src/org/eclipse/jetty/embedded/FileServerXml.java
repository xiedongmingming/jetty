package org.eclipse.jetty.embedded;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;

import extral.org.eclipse.jetty.xml.XmlConfiguration;

public class FileServerXml {// 表示通过配置文件来启动服务器

	public static void main(String[] args) throws Exception {

        Resource fileserverXml = Resource.newSystemResource("fileserver.xml");

		XmlConfiguration configuration = new XmlConfiguration(fileserverXml.getInputStream());

        Server server = (Server) configuration.configure();

        server.start();
        server.join();
    }
}
