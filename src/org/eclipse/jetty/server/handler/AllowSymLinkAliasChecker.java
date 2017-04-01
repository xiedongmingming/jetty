package org.eclipse.jetty.server.handler;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.server.handler.ContextHandler.AliasCheck;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;

public class AllowSymLinkAliasChecker implements AliasCheck {

    private static final Logger LOG = Log.getLogger(AllowSymLinkAliasChecker.class);
    
    @Override
	public boolean check(String uri, Resource resource) {

		if (!(resource instanceof PathResource)) {
			return false;
		}
        
        PathResource pathResource = (PathResource)resource;

		try {
            Path path = pathResource.getPath();
            Path alias = pathResource.getAliasPath();

			if (path.equals(alias)) {
				return false;
			}

			if (Files.isSymbolicLink(path)) {
                alias = path.getParent().resolve(alias);
				if (Files.isSameFile(path, alias)) {
                    return true;
                }
            }

            boolean linked=true;
            Path target=path;
            int loops=0;
			while (linked) {
				if (++loops > 100) {
                    return false;
                }
                linked=false;
                Path d = target.getRoot();
				for (Path e : target) {
                    Path r=d.resolve(e);
                    d=r;

					while (Files.exists(d) && Files.isSymbolicLink(d)) {
                        Path link=Files.readSymbolicLink(d);    
						if (!link.isAbsolute()) {
							link = d.getParent().resolve(link);
						}
                        d=link;
                        linked=true;
                    }
                }
                target=d;
            }
            
			if (pathResource.getAliasPath().equals(target)) {
                return true;
            }
		} catch (Exception e) {
            LOG.ignore(e);
        }
        return false;
    }
}
