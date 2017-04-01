package extral.org.eclipse.jetty.xml;

public interface ConfigurationProcessorFactory {
    ConfigurationProcessor getConfigurationProcessor(String dtd, String tag);
}
