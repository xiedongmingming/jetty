package extral.org.eclipse.jetty.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.StringTokenizer;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class XmlParser {

    private static final Logger LOG = Log.getLogger(XmlParser.class);

	private Map<String, URL> _redirectMap = new HashMap<String, URL>();
    private SAXParser _parser;
	private Map<String, ContentHandler> _observerMap;
	private Stack<ContentHandler> _observers = new Stack<ContentHandler>();// ????
    private String _xpath;
	private Object _xpaths;// ???
    private String _dtd;

	// *************************************************************************************************************
	public XmlParser() {

        SAXParserFactory factory = SAXParserFactory.newInstance();

        boolean validating_dft = factory.getClass().toString().startsWith("org.apache.xerces.");

        String validating_prop = System.getProperty("org.eclipse.jetty.xml.XmlParser.Validating", validating_dft ? "true" : "false");

        boolean validating = Boolean.valueOf(validating_prop).booleanValue();

        setValidating(validating);
    }
	public XmlParser(boolean validating) {
        setValidating(validating);
    }
	// *************************************************************************************************************

	public void setValidating(boolean validating) {
		try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(validating);
            _parser = factory.newSAXParser();

			try {
				if (validating) {
					_parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/schema", validating);
				}
			} catch (Exception e) {
				if (validating) {
					LOG.warn("Schema validation may not be supported: ", e);
				} else {
					LOG.ignore(e);
				}
            }

            _parser.getXMLReader().setFeature("http://xml.org/sax/features/validation", validating);
            _parser.getXMLReader().setFeature("http://xml.org/sax/features/namespaces", true);
            _parser.getXMLReader().setFeature("http://xml.org/sax/features/namespace-prefixes", false);
			try {
                if (validating)
                    _parser.getXMLReader().setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", validating);
			} catch (Exception e) {
                LOG.warn(e.getMessage());
            }
		} catch (Exception e) {
            LOG.warn(Log.EXCEPTION, e);
            throw new Error(e.toString());
        }
    }
	public boolean isValidating() {
        return _parser.isValidating();
    }
	public synchronized void redirectEntity(String name, URL entity) {
		if (entity != null) {
			_redirectMap.put(name, entity);
		}
    }
	public String getXpath() {
        return _xpath;
    }
	public void setXpath(String xpath) {
        _xpath = xpath;
        StringTokenizer tok = new StringTokenizer(xpath, "| ");
		while (tok.hasMoreTokens()) {
			_xpaths = LazyList.add(_xpaths, tok.nextToken());
		}
    }

	public String getDTD() {
        return _dtd;
    }
	public synchronized void addContentHandler(String trigger, ContentHandler observer) {
		if (_observerMap == null) {
			_observerMap = new HashMap<>();
		}
        _observerMap.put(trigger, observer);
    }

    /* ------------------------------------------------------------ */
	public synchronized Node parse(InputSource source) throws IOException, SAXException {// 解析配置(SAX)

		_dtd = null;

        Handler handler = new Handler();

        XMLReader reader = _parser.getXMLReader();

        reader.setContentHandler(handler);
        reader.setErrorHandler(handler);
        reader.setEntityResolver(handler);

        _parser.parse(source, handler);

		if (handler._error != null) {
			throw handler._error;
		}

        Node doc = (Node) handler._top.get(0);

        handler.clear();

        return doc;
    }

	public synchronized Node parse(String url) throws IOException, SAXException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("parse: " + url);
		}
        return parse(new InputSource(url));
    }

	public synchronized Node parse(File file) throws IOException, SAXException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("parse: " + file);
		}
        return parse(new InputSource(Resource.toURL(file).toString()));
    }

	public synchronized Node parse(InputStream in) throws IOException, SAXException {// 执行解析

		_dtd = null;// 表示DTD

		Handler handler = new Handler();// 处理器

		XMLReader reader = _parser.getXMLReader();// READER

        reader.setContentHandler(handler);
        reader.setErrorHandler(handler);
        reader.setEntityResolver(handler);

        _parser.parse(new InputSource(in), handler);
		if (handler._error != null) {
			throw handler._error;
		}

        Node doc = (Node) handler._top.get(0);

        handler.clear();

        return doc;
    }

	protected InputSource resolveEntity(String pid, String sid) {

		if (LOG.isDebugEnabled()) {
			LOG.debug("resolveEntity(" + pid + ", " + sid + ")");
		}

		if (sid != null && sid.endsWith(".dtd")) {
			_dtd = sid;
		}

        URL entity = null;
		if (pid != null) {
			entity = _redirectMap.get(pid);
		}
		if (entity == null) {
			entity = _redirectMap.get(sid);
		}
		if (entity == null) {
            String dtd = sid;
			if (dtd.lastIndexOf('/') >= 0) {
				dtd = dtd.substring(dtd.lastIndexOf('/') + 1);
			}

			if (LOG.isDebugEnabled()) {
				LOG.debug("Can't exact match entity in redirect map, trying " + dtd);
			}
            entity = _redirectMap.get(dtd);
        }

		if (entity != null) {
			try {
                InputStream in = entity.openStream();
				if (LOG.isDebugEnabled()) {
					LOG.debug("Redirected entity " + sid + " --> " + entity);
				}
                InputSource is = new InputSource(in);
                is.setSystemId(sid);
                return is;
			} catch (IOException e) {
                LOG.ignore(e);
            }
        }
        return null;
    }

	private class NoopHandler extends DefaultHandler {

        Handler _next;
        int _depth;

		NoopHandler(Handler next) {
            this._next = next;
        }

        @Override
		public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
            _depth++;
        }
        @Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if (_depth == 0) {
				_parser.getXMLReader().setContentHandler(_next);
			} else {
				_depth--;
			}
        }
    }

	private class Handler extends DefaultHandler {// 表示解析处理器

		Node _top = new Node(null, null, null);// 表示顶层的NODE(默认为空)

		SAXParseException _error;// 表示解析过程中出错了

		private Node _context = _top;// 用于表示当前解析节点的上下文

        private NoopHandler _noop;

		// *********************************************************************************************************
		Handler() {
            _noop = new NoopHandler(this);
        }
		// *********************************************************************************************************

		void clear() {
            _top = null;
            _error = null;
            _context = null;
        }

		// *********************************************************************************************************
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {

			String name = null;

			if (_parser.isNamespaceAware()) {// 是否理解命名空间(表示根配置)--Configure
				name = localName;
			}

			if (name == null || "".equals(name)) {
				name = qName;
			}

            Node node = new Node(_context, name, attrs);

			if (_xpaths != null) {
                String path = node.getPath();
                boolean match = false;
				for (int i = LazyList.size(_xpaths); !match && i-- > 0;) {
                    String xpath = (String) LazyList.get(_xpaths, i);
                    match = path.equals(xpath) || xpath.startsWith(path) && xpath.length() > path.length() && xpath.charAt(path.length()) == '/';
                }

				if (match) {
                    _context.add(node);
                    _context = node;
				} else {
                    _parser.getXMLReader().setContentHandler(_noop);
                }
			} else {
                _context.add(node);
                _context = node;
            }

            ContentHandler observer = null;
			if (_observerMap != null) {
				observer = _observerMap.get(name);
			}
            _observers.push(observer);

			for (int i = 0; i < _observers.size(); i++) {
				if (_observers.get(i) != null) {
					_observers.get(i).startElement(uri, localName, qName, attrs);
				}
			}
        }
        @Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
            _context = _context._parent;
			for (int i = 0; i < _observers.size(); i++) {
                if (_observers.get(i) != null){
                	_observers.get(i).endElement(uri, localName, qName);
                }
			}
            _observers.pop();
        }
        @Override
		public void ignorableWhitespace(char buf[], int offset, int len) throws SAXException {
			for (int i = 0; i < _observers.size(); i++) {
				if (_observers.get(i) != null) {
					_observers.get(i).ignorableWhitespace(buf, offset, len);
				}
			}
        }
        @Override
		public void characters(char buf[], int offset, int len) throws SAXException {// 标签中的内容
			_context.add(new String(buf, offset, len));
			for (int i = 0; i < _observers.size(); i++) {
				if (_observers.get(i) != null) {
					_observers.get(i).characters(buf, offset, len);
				}
			}
        }
        @Override
		public void warning(SAXParseException ex) {
            LOG.debug(Log.EXCEPTION, ex);
            LOG.warn("WARNING@" + getLocationString(ex) + " : " + ex.toString());
        }
        @Override
		public void error(SAXParseException ex) throws SAXException {
			if (_error == null) {
				_error = ex;
			}
            LOG.debug(Log.EXCEPTION, ex);
            LOG.warn("ERROR@" + getLocationString(ex) + " : " + ex.toString());
        }
        @Override
		public void fatalError(SAXParseException ex) throws SAXException {
            _error = ex;
            LOG.debug(Log.EXCEPTION, ex);
            LOG.warn("FATAL@" + getLocationString(ex) + " : " + ex.toString());
            throw ex;
        }
        @Override
		public InputSource resolveEntity(String pid, String sid) {
			return XmlParser.this.resolveEntity(pid, sid);
        }

		// *********************************************************************************************************
		private String getLocationString(SAXParseException ex) {
			return ex.getSystemId() + " line:" + ex.getLineNumber() + " col:" + ex.getColumnNumber();
		}
    }


	public static class Attribute {// 表示XML节点的属性

        private String _name;
        private String _value;

		Attribute(String n, String v) {
            _name = n;
            _value = v;
        }

		public String getName() {
            return _name;
        }
		public String getValue() {
            return _value;
        }
    }

	public static class Node extends AbstractList<Object> {// 表示XML中的一个NODE(各个NODE通过指针关联)

		Node _parent;// 表示父节点

        private ArrayList<Object> _list;

		private String _tag;// 表示当前节点的标识
		private Attribute[] _attrs;// 表当前节点的属性

		private boolean _lastString = false;// 结束标识

        private String _path;

		// *************************************************************************
		Node(Node parent, String tag, Attributes attrs) {

			// 第一个参数表示父节点
			// 第二个参数表示标识
			// 第三个参数

			_parent = parent;// 表示父节点
			_tag = tag;// 表示标签

			if (attrs != null) {

                _attrs = new Attribute[attrs.getLength()];

				for (int i = 0; i < attrs.getLength(); i++) {

                    String name = attrs.getLocalName(i);

					if (name == null || name.equals("")) {
						name = attrs.getQName(i);
					}

                    _attrs[i] = new Attribute(name, attrs.getValue(i));
                }
            }
        }

		// *************************************************************************
		public Node getParent() {
            return _parent;
        }
		public String getTag() {
            return _tag;
        }
		public String getPath() {
			if (_path == null) {
				if (getParent() != null && getParent().getTag() != null) {
					_path = getParent().getPath() + "/" + _tag;
				} else {
					_path = "/" + _tag;
				}
            }
            return _path;
        }
		public Attribute[] getAttributes() {
            return _attrs;
        }
		public String getAttribute(String name) {
            return getAttribute(name, null);
        }
		public String getAttribute(String name, String dft) {// 第二个参数表示默认值
			if (_attrs == null || name == null) {
				return dft;
			}
			for (int i = 0; i < _attrs.length; i++) {
				if (name.equals(_attrs[i].getName())) {
					return _attrs[i].getValue();
				}
			}
            return dft;
        }

		// *************************************************************************
        @Override
		public int size() {
			if (_list != null) {
				return _list.size();
			}
            return 0;
        }
        @Override
		public Object get(int i) {
			if (_list != null) {
				return _list.get(i);
			}
            return null;
        }
        @Override
		public void add(int i, Object o) {// 用于关联各个节点的关键
			if (_list == null) {
				_list = new ArrayList<Object>();
			}
			if (o instanceof String) {
				if (_lastString) {//
                    int last = _list.size() - 1;
                    _list.set(last, (String) _list.get(last) + o);
				} else {
					_list.add(i, o);
				}
                _lastString = true;
			} else {
                _lastString = false;
                _list.add(i, o);
            }
        }
        @Override
		public void clear() {
			if (_list != null) {
				_list.clear();
			}
            _list = null;
        }
		@Override
		public synchronized String toString() {
			return toString(true);
		}
		// *************************************************************************

		public Node get(String tag) {
			if (_list != null) {
				for (int i = 0; i < _list.size(); i++) {
					Object o = _list.get(i);
					if (o instanceof Node) {
						Node n = (Node) o;
						if (tag.equals(n._tag)) {
							return n;
						}
					}
				}
			}
			return null;
		}
		public String getString(String tag, boolean tags, boolean trim) {
            Node node = get(tag);
			if (node == null) {
				return null;
			}
            String s = node.toString(tags);
			if (s != null && trim) {
				s = s.trim();
			}
            return s;
        }
		public synchronized String toString(boolean tag) {
            StringBuilder buf = new StringBuilder();
            toString(buf, tag);
            return buf.toString();
        }
		public synchronized String toString(boolean tag, boolean trim) {
            String s = toString(tag);
			if (s != null && trim) {
				s = s.trim();
			}
            return s;
        }
		private synchronized void toString(StringBuilder buf, boolean tag) {
			if (tag) {
                buf.append("<");
                buf.append(_tag);
				if (_attrs != null) {
					for (int i = 0; i < _attrs.length; i++) {
                        buf.append(' ');
                        buf.append(_attrs[i].getName());
                        buf.append("=\"");
                        buf.append(_attrs[i].getValue());
                        buf.append("\"");
                    }
                }
            }
			if (_list != null) {
				if (tag) {
					buf.append(">");
				}
				for (int i = 0; i < _list.size(); i++) {
                    Object o = _list.get(i);
					if (o == null) {
						continue;
					}
					if (o instanceof Node) {
						((Node) o).toString(buf, tag);
					} else {
						buf.append(o.toString());
					}
                }
				if (tag) {
                    buf.append("</");
                    buf.append(_tag);
                    buf.append(">");
                }
			} else if (tag) {
				buf.append("/>");
			}
        }
		public Iterator<Node> iterator(final String tag) {
			return new Iterator<Node>() {
                int c = 0;
                Node _node;
                @Override
				public boolean hasNext() {
					if (_node != null) {
						return true;
					}
					while (_list != null && c < _list.size()) {
                        Object o = _list.get(c);
						if (o instanceof Node) {
                            Node n = (Node) o;
							if (tag.equals(n._tag)) {
                                _node = n;
                                return true;
                            }
                        }
                        c++;
                    }
                    return false;
                }
                @Override
				public Node next() {
					try {
						if (hasNext()) {
							return _node;
						}
                        throw new NoSuchElementException();
					} finally {
                        _node = null;
                        c++;
                    }
                }
                @Override
				public void remove() {
                    throw new UnsupportedOperationException("Not supported");
                }
            };
        }
    }
}
