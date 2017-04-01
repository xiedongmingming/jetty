package org.eclipse.jetty.embedded;

import org.eclipse.jetty.util.resource.Resource;

import extral.org.eclipse.jetty.xml.XmlConfiguration;//只有此处用到

public class ExampleServerXml {
    public static void main( String[] args ) throws Exception {
        Resource serverXml = Resource.newSystemResource("exampleserver.xml");
        XmlConfiguration.main(serverXml.getFile().getAbsolutePath());
    }
}
