package org.eclipse.jetty.server.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.PathMap;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.IPAddressMap;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

@SuppressWarnings("deprecation")
public class IPAccessHandler extends HandlerWrapper {

    private static final Logger LOG = Log.getLogger(IPAccessHandler.class);

    PathMap<IPAddressMap<Boolean>> _white = new PathMap<IPAddressMap<Boolean>>(true);
    PathMap<IPAddressMap<Boolean>> _black = new PathMap<IPAddressMap<Boolean>>(true);
    boolean _whiteListByPath = false;

	public IPAccessHandler() {
        super();
    }
	public IPAccessHandler(String[] white, String[] black) {
        super();

		if (white != null && white.length > 0) {
			setWhite(white);
		}
		if (black != null && black.length > 0) {
			setBlack(black);
		}
    }

	public void addWhite(String entry) {
        add(entry, _white);
    }

	public void addBlack(String entry) {
        add(entry, _black);
    }

	public void setWhite(String[] entries) {
        set(entries, _white);
    }

	public void setBlack(String[] entries) {
        set(entries, _black);
    }

	public void setWhiteListByPath(boolean whiteListByPath) {
        this._whiteListByPath = whiteListByPath;
    }

    @Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

        HttpChannel channel = baseRequest.getHttpChannel();
		if (channel != null) {
            EndPoint endp=channel.getEndPoint();
			if (endp != null) {
                InetSocketAddress address = endp.getRemoteAddress();
				if (address != null && !isAddrUriAllowed(address.getHostString(), baseRequest.getPathInfo())) {
                    response.sendError(HttpStatus.FORBIDDEN_403);
                    baseRequest.setHandled(true);
                    return;
                }
            }
        }
        getHandler().handle(target,baseRequest, request, response);
    }

	protected void add(String entry, PathMap<IPAddressMap<Boolean>> patternMap) {
		if (entry != null && entry.length() > 0) {
            boolean deprecated = false;
            int idx;
			if (entry.indexOf('|') > 0) {
                idx = entry.indexOf('|');
			} else {
                idx = entry.indexOf('/');
                deprecated = (idx >= 0);
            }

            String addr = idx > 0 ? entry.substring(0,idx) : entry;
            String path = idx > 0 ? entry.substring(idx) : "/*";

			if (addr.endsWith(".")) {
				deprecated = true;
			}
			if (path != null && (path.startsWith("|") || path.startsWith("/*."))) {
				path = path.substring(1);
			}

            IPAddressMap<Boolean> addrMap = patternMap.get(path);
			if (addrMap == null) {
                addrMap = new IPAddressMap<Boolean>();
                patternMap.put(path,addrMap);
            }
			if (addr != null && !"".equals(addr)) {
				addrMap.put(addr, true);
			}

			if (deprecated) {
				LOG.debug(toString() + " - deprecated specification syntax: " + entry);
			}
        }
    }

	protected void set(String[] entries, PathMap<IPAddressMap<Boolean>> patternMap) {
        patternMap.clear();

		if (entries != null && entries.length > 0) {
			for (String addrPath : entries) {
                add(addrPath, patternMap);
            }
        }
    }

	protected boolean isAddrUriAllowed(String addr, String path) {

		if (_white.size() > 0) {

            boolean match = false;
            boolean matchedByPath = false;

			for (Map.Entry<String, IPAddressMap<Boolean>> entry : _white.getMatches(path)) {
				matchedByPath = true;
                IPAddressMap<Boolean> addrMap = entry.getValue();
				if ((addrMap != null && (addrMap.size() == 0 || addrMap.match(addr) != null))) {
					match = true;
                    break;
                }
            }
            
			if (_whiteListByPath) {
				if (matchedByPath && !match) {
					return false;
				}
			} else {
				if (!match) {
					return false;
				}
            }
        }

		if (_black.size() > 0) {
			for (Map.Entry<String, IPAddressMap<Boolean>> entry : _black.getMatches(path)) {
                IPAddressMap<Boolean> addrMap = entry.getValue();
				if (addrMap != null && (addrMap.size() == 0 || addrMap.match(addr) != null)) {
					return false;
				}
            }
            
        }

        return true;
    }

    @Override
	public String dump() {

        StringBuilder buf = new StringBuilder();

        buf.append(toString());
        buf.append(" WHITELIST:\n");
        dump(buf, _white);
        buf.append(toString());
        buf.append(" BLACKLIST:\n");
        dump(buf, _black);

        return buf.toString();
    }

	protected void dump(StringBuilder buf, PathMap<IPAddressMap<Boolean>> patternMap) {
		for (String path : patternMap.keySet()) {
			for (String addr : patternMap.get(path).keySet()) {
                buf.append("# ");
                buf.append(addr);
                buf.append("|");
                buf.append(path);
                buf.append("\n");
            }
        }
    }
 }
