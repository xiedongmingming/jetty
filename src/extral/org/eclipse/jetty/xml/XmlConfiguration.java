package extral.org.eclipse.jetty.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.ArrayQueue;
import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlConfiguration {// 表示通过配置文件的方式来配置服务

    private static final Logger LOG = Log.getLogger(XmlConfiguration.class);
    
	private static final Class<?>[] __primitives = { 
			Boolean.TYPE,
			Character.TYPE,
			Byte.TYPE,
			Short.TYPE,
			Integer.TYPE,
			Long.TYPE,
			Float.TYPE,
			Double.TYPE,
			Void.TYPE };
    private static final Class<?>[] __boxedPrimitives = {
    		Boolean.class, 
    		Character.class, 
    		Byte.class, 
    		Short.class, 
    		Integer.class, 
    		Long.class, 
    		Float.class, 
    		Double.class, 
    		Void.class};
    private static final Class<?>[] __supportedCollections = {
    		ArrayList.class, 
    		ArrayQueue.class, 
    		HashSet.class, 
    		Queue.class, 
    		List.class, 
    		Set.class, 
    		Collection.class};
    
    private static final Iterable<ConfigurationProcessorFactory> __factoryLoader = ServiceLoader.load(ConfigurationProcessorFactory.class);
    
    private static final XmlParser __parser = initParser();

	private static XmlParser initParser() {

        XmlParser parser = new XmlParser();

		URL config60 = Loader.getResource(XmlConfiguration.class, "extral/org/eclipse/jetty/xml/configure_6_0.dtd");
		URL config76 = Loader.getResource(XmlConfiguration.class, "extral/org/eclipse/jetty/xml/configure_7_6.dtd");
		URL config90 = Loader.getResource(XmlConfiguration.class, "extral/org/eclipse/jetty/xml/configure_9_0.dtd");
		URL config93 = Loader.getResource(XmlConfiguration.class, "extral/org/eclipse/jetty/xml/configure_9_3.dtd");

		parser.redirectEntity("configure.dtd", config90);

		parser.redirectEntity("configure_1_0.dtd", config60);
		parser.redirectEntity("configure_1_1.dtd", config60);
		parser.redirectEntity("configure_1_2.dtd", config60);
		parser.redirectEntity("configure_1_3.dtd", config60);
		parser.redirectEntity("configure_6_0.dtd", config60);
		parser.redirectEntity("configure_7_6.dtd", config76);
		parser.redirectEntity("configure_9_0.dtd", config90);
		parser.redirectEntity("configure_9_3.dtd", config93);

		parser.redirectEntity("http://jetty.mortbay.org/configure.dtd", config93);

		parser.redirectEntity("http://jetty.eclipse.org/configure.dtd", config93);

		parser.redirectEntity("http://www.eclipse.org/jetty/configure.dtd", config93);

		parser.redirectEntity("-//Mort Bay Consulting//DTD Configure//EN", config93);

		parser.redirectEntity("-//Jetty//Configure//EN", config93);

        return parser;
    }

	private final Map<String, Object> _idMap = new HashMap<>();// ????

    private final Map<String, String> _propertyMap = new HashMap<>();

    private final URL _url;

    private final String _dtd;

	private ConfigurationProcessor _processor;//

	// *************************************************************************************************************
	// 构造函数
	public XmlConfiguration(URL configuration) throws SAXException, IOException {
		synchronized (__parser) {
			_url = configuration;
            setConfig(__parser.parse(configuration.toString()));
			_dtd = __parser.getDTD();
        }
    }
	public XmlConfiguration(String configuration) throws SAXException, IOException {

		configuration = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE Configure PUBLIC \"-//Jetty//Configure//EN\" \"http://eclipse.org/jetty/configure.dtd\">"
                + configuration;

		InputSource source = new InputSource(new StringReader(configuration));

		synchronized (__parser) {
			_url = null;
            setConfig( __parser.parse(source));
			_dtd = __parser.getDTD();
        }
    }
	public XmlConfiguration(InputStream configuration) throws SAXException, IOException {

		InputSource source = new InputSource(configuration);// SAX解析

		synchronized (__parser) {
			_url = null;
            setConfig(__parser.parse(source));
			_dtd = __parser.getDTD();// http://www.eclipse.org/jetty/configure_9_3.dtd
        }
    }

	// *************************************************************************************************************
	private void setConfig(XmlParser.Node config) {// 参数为解析出来后的NODE

		if ("Configure".equals(config.getTag())) {

			_processor = new JettyXmlConfiguration();

		} else if (__factoryLoader != null) {

			for (ConfigurationProcessorFactory factory : __factoryLoader) {

                _processor = factory.getConfigurationProcessor(_dtd, config.getTag());

				if (_processor != null) {
					break;
				}

            }
			if (_processor == null) {
				throw new IllegalStateException("Unknown configuration type: " + config.getTag() + " in " + this);
			}
		} else {
			throw new IllegalArgumentException("Unknown XML tag:" + config.getTag());
        }

		_processor.init(_url, config, this);// 初始化处理器
    }

	public Map<String, Object> getIdMap() {
        return _idMap;
    }
	public Map<String, String> getProperties() {
        return _propertyMap;
    }

	// *******************************************************************************************
	public Object configure(Object obj) throws Exception {
        return _processor.configure(obj);
    }
	public Object configure() throws Exception {
        return _processor.configure();
    }
	// *******************************************************************************************
    
	public void initializeDefaults(Object object) {

    }
    
	private static class JettyXmlConfiguration implements ConfigurationProcessor {// 解析器--processor
        
        private String _url;

        XmlParser.Node _root;

        XmlConfiguration _configuration;

        @Override
		public void init(URL url, XmlParser.Node root, XmlConfiguration configuration) {
			_url = url == null ? null : url.toString();
			_root = root;
			_configuration = configuration;
        }

        @Override
		public Object configure(Object obj) throws Exception {
            // Check the class of the object
            Class<?> oClass = nodeClass(_root);
			if (oClass != null && !oClass.isInstance(obj)) {
                String loaders = (oClass.getClassLoader()==obj.getClass().getClassLoader())?"":"Object Class and type Class are from different loaders.";
                throw new IllegalArgumentException("Object of class '"+obj.getClass().getCanonicalName()+"' is not of type '" + oClass.getCanonicalName()+"'. "+loaders+" in "+_url);
            }
            String id=_root.getAttribute("id");
			if (id != null) {
				_configuration.getIdMap().put(id, obj);
			}
            configure(obj,_root,0);
            return obj;
        }

        @Override
		public Object configure() throws Exception {// 执行过程

			Class<?> oClass = nodeClass(_root);// org.eclipse.jetty.server.Server

            String id = _root.getAttribute("id");

			Object obj = id == null ? null : _configuration.getIdMap().get(id);

            int index = 0;

			if (obj == null && oClass != null) {

				index = _root.size();// 表示根节点下面的成员

                Map<String, Object> namedArgMap = new HashMap<>();

                List<Object> arguments = new LinkedList<>();

				for (int i = 0; i < _root.size(); i++) {

                    Object o = _root.get(i);

					if (o instanceof String) {
                        continue;
                    }

					XmlParser.Node node = (XmlParser.Node) o;

					if (!(node.getTag().equals("Arg"))) {
                        index = i;
                        break;
					} else {
                        String namedAttribute = node.getAttribute("name");
                        Object value=value(obj,(XmlParser.Node)o);
						if (namedAttribute != null) {
							namedArgMap.put(namedAttribute, value);
						}
                        arguments.add(value);
                    }
                }

				try {
					if (namedArgMap.size() > 0) {
						obj = TypeUtil.construct(oClass, arguments.toArray(), namedArgMap);
					} else {
						obj = TypeUtil.construct(oClass, arguments.toArray());
					}
				} catch (NoSuchMethodException x) {
                    throw new IllegalStateException(String.format("No constructor %s(%s,%s) in %s",oClass,arguments,namedArgMap,_url));
                }
            }
			if (id != null) {
				_configuration.getIdMap().put(id, obj);
			}
                
			_configuration.initializeDefaults(obj);// 服务器

            configure(obj, _root, index);

            return obj;
        }

		private static Class<?> nodeClass(XmlParser.Node node) throws ClassNotFoundException {
            String className = node.getAttribute("class");
			if (className == null) {
				return null;
			}
			return Loader.loadClass(XmlConfiguration.class, className);
        }

		public void configure(Object obj, XmlParser.Node cfg, int i) throws Exception {// 递归配置

			for (; i < cfg.size(); i++) {

                Object o = cfg.get(i);

				if (o instanceof String) {
					continue;
				}

				XmlParser.Node node = (XmlParser.Node) o;

				if ("Arg".equals(node.getTag())) {
                    LOG.warn("Ignored arg: "+node);
                    continue;
                }

                break;
            }

			for (; i < cfg.size(); i++) {

                Object o = cfg.get(i);

				if (o instanceof String) {
					continue;
				}

				XmlParser.Node node = (XmlParser.Node) o;

				try {
                    String tag = node.getTag();
					switch (tag) {
                        case "Set":
                            set(obj, node);
                            break;
                        case "Put":
                            put(obj, node);
                            break;
                        case "Call":
                            call(obj, node);
                            break;
                        case "Get":
                            get(obj, node);
                            break;
                        case "New":
                            newObj(obj, node);
                            break;
                        case "Array":
                            newArray(obj, node);
                            break;
                        case "Map":
                            newMap(obj,node);
                            break;
                        case "Ref":
                            refObj(obj, node);
                            break;
                        case "Property":
                            propertyObj(node);
                            break;
                        case "SystemProperty":
                            systemPropertyObj(node);
                            break;
                        case "Env":
                            envObj(node);
                            break;
                        default:
                            throw new IllegalStateException("Unknown tag: " + tag + " in " + _url);
                    }
				} catch (Exception e) {
                    LOG.warn("Config error at " + node,e.toString()+" in "+_url);
                    throw e;
                }
            }
        }

		private void set(Object obj, XmlParser.Node node) throws Exception {
            String attr = node.getAttribute("name");
            String name = "set" + attr.substring(0,1).toUpperCase(Locale.ENGLISH) + attr.substring(1);
            Object value = value(obj,node);
			Object[] arg = { value };

            Class<?> oClass = nodeClass(node);
			if (oClass != null) {
				obj = null;
			} else {
				oClass = obj.getClass();
			}

			Class<?>[] vClass = { Object.class };
			if (value != null) {
				vClass[0] = value.getClass();
			}

			if (LOG.isDebugEnabled()) {
				LOG.debug("XML " + (obj != null ? obj.toString() : oClass.getName()) + "." + name + "(" + value + ")");
			}

            // Try for trivial match
            try
            {
                Method set = oClass.getMethod(name,vClass);
                set.invoke(obj,arg);
                return;
            }
            catch (IllegalArgumentException | IllegalAccessException | NoSuchMethodException e)
            {
                LOG.ignore(e);
            }

            // Try for native match
            try
            {
                Field type = vClass[0].getField("TYPE");
                vClass[0] = (Class<?>)type.get(null);
                Method set = oClass.getMethod(name,vClass);
                set.invoke(obj,arg);
                return;
            }
            catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException | NoSuchMethodException e)
            {
                LOG.ignore(e);
            }

            // Try a field
            try
            {
                Field field = oClass.getField(attr);
                if (Modifier.isPublic(field.getModifiers()))
                {
                    field.set(obj,value);
                    return;
                }
            }
            catch (NoSuchFieldException e)
            {
                LOG.ignore(e);
            }

            // Search for a match by trying all the set methods
            Method[] sets = oClass.getMethods();
            Method set = null;
            for (int s = 0; sets != null && s < sets.length; s++)
            {
                Class<?>[] paramTypes = sets[s].getParameterTypes();
                if (name.equals(sets[s].getName()) && paramTypes.length == 1)
                {
                    // lets try it
                    try
                    {
                        set = sets[s];
                        sets[s].invoke(obj,arg);
                        return;
                    }
                    catch (IllegalArgumentException | IllegalAccessException e)
                    {
                        LOG.ignore(e);
                    }

                    try
                    {
                        for (Class<?> c : __supportedCollections)
                            if (paramTypes[0].isAssignableFrom(c))
                            {
                                sets[s].invoke(obj,convertArrayToCollection(value,c));
                                return;
                            }
                    }
                    catch (IllegalAccessException e)
                    {
                        LOG.ignore(e);
                    }
                }
            }

            // Try converting the arg to the last set found.
            if (set != null)
            {
                try
                {
                    Class<?> sClass = set.getParameterTypes()[0];
                    if (sClass.isPrimitive())
                    {
                        for (int t = 0; t < __primitives.length; t++)
                        {
                            if (sClass.equals(__primitives[t]))
                            {
                                sClass = __boxedPrimitives[t];
                                break;
                            }
                        }
                    }
                    Constructor<?> cons = sClass.getConstructor(vClass);
                    arg[0] = cons.newInstance(arg);
                    _configuration.initializeDefaults(arg[0]);
                    set.invoke(obj,arg);
                    return;
                }
                catch (NoSuchMethodException | IllegalAccessException | InstantiationException e)
                {
                    LOG.ignore(e);
                }
            }

            // No Joy
            throw new NoSuchMethodException(oClass + "." + name + "(" + vClass[0] + ")");
        }

        /**
         * @param array the array to convert
         * @param collectionType the desired collection type
         * @return a collection of the desired type if the array can be converted
         */
        private static Collection<?> convertArrayToCollection(Object array, Class<?> collectionType)
        {
            Collection<?> collection = null;
            if (array.getClass().isArray())
            {
                if (collectionType.isAssignableFrom(ArrayList.class))
                    collection = convertArrayToArrayList(array);
                else if (collectionType.isAssignableFrom(HashSet.class))
                    collection = new HashSet<>(convertArrayToArrayList(array));
                else if (collectionType.isAssignableFrom(ArrayQueue.class))
                {
                    ArrayQueue<Object> q= new ArrayQueue<>();
                    q.addAll(convertArrayToArrayList(array));
                    collection=q;
                }
            }
            if (collection==null)
                throw new IllegalArgumentException("Can't convert \"" + array.getClass() + "\" to " + collectionType);
            return collection;
        }

        private static ArrayList<Object> convertArrayToArrayList(Object array)
        {
            int length = Array.getLength(array);
            ArrayList<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++)
                list.add(Array.get(array,i));
            return list;
        }

        /*
         * Call a put method.
         *
         * @param obj @param node
         */
        private void put(Object obj, XmlParser.Node node) throws Exception
        {
            if (!(obj instanceof Map))
                throw new IllegalArgumentException("Object for put is not a Map: " + obj);
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>)obj;

            String name = node.getAttribute("name");
            Object value = value(obj, node);
            map.put(name,value);
            if (LOG.isDebugEnabled())
                LOG.debug("XML " + obj + ".put(" + name + "," + value + ")");
        }

        /*
         * Call a get method. Any object returned from the call is passed to the configure method to consume the remaining elements. @param obj @param node
         *
         * @return @exception Exception
         */
        private Object get(Object obj, XmlParser.Node node) throws Exception
        {
            Class<?> oClass = nodeClass(node);
            if (oClass != null)
                obj = null;
            else
                oClass = obj.getClass();

            String name = node.getAttribute("name");
            String id = node.getAttribute("id");
            if (LOG.isDebugEnabled())
                LOG.debug("XML get " + name);

            try
            {
                // try calling a getXxx method.
                Method method = oClass.getMethod("get" + name.substring(0,1).toUpperCase(Locale.ENGLISH) + name.substring(1),(java.lang.Class[])null);
                obj = method.invoke(obj,(java.lang.Object[])null);
                if (id!=null)
                    _configuration.getIdMap().put(id,obj);
                configure(obj,node,0);
            }
            catch (NoSuchMethodException nsme)
            {
                try
                {
                    Field field = oClass.getField(name);
                    obj = field.get(obj);
                    configure(obj,node,0);
                }
                catch (NoSuchFieldException nsfe)
                {
                    throw nsme;
                }
            }
            return obj;
        }

        /*
         * Call a method. A method is selected by trying all methods with matching names and number of arguments. Any object returned from the call is passed to
         * the configure method to consume the remaining elements. Note that if this is a static call we consider only methods declared directly in the given
         * class. i.e. we ignore any static methods in superclasses. @param obj
         *
         * @param node @return @exception Exception
         */
		private Object call(Object obj, XmlParser.Node node) throws Exception {

			AttrOrElementNode aoeNode = new AttrOrElementNode(obj, node, "Id", "Name", "Class", "Arg");

            String id = aoeNode.getString("Id");
            String name = aoeNode.getString("Name");
            String clazz = aoeNode.getString("Class");
            List<Object> args = aoeNode.getList("Arg");

			Class<?> oClass;
            
			if (clazz != null) {
				oClass = Loader.loadClass(XmlConfiguration.class, clazz);
				obj = null;
			} else if (obj != null) {
                oClass = obj.getClass();
			} else {
				throw new IllegalArgumentException(node.toString());
            }
           
			if (LOG.isDebugEnabled()) {
				LOG.debug("XML call " + name);
			}

			try {
				Object nobj = TypeUtil.call(oClass, name, obj, args.toArray(new Object[args.size()]));
				if (id != null) {
					_configuration.getIdMap().put(id, nobj);
				}
				configure(nobj, node, aoeNode.getNext());// 递归
                return nobj;
			} catch (NoSuchMethodException e) {
                IllegalStateException ise = new IllegalStateException("No Method: " + node + " on " + oClass);
                ise.initCause(e);
                throw ise;
            }
        }

        /*
         * Create a new value object.
         *
         * @param obj
         * @param node
         *
         * @return @exception Exception
         */
        private Object newObj(Object obj, XmlParser.Node node) throws Exception
        {
            AttrOrElementNode aoeNode=new AttrOrElementNode(obj,node,"Id","Class","Arg");
            String id = aoeNode.getString("Id");
            String clazz = aoeNode.getString("Class");
            List<XmlParser.Node> argNodes = aoeNode.getNodes("Arg");

            if (LOG.isDebugEnabled())
                LOG.debug("XML new " + clazz);
            
            Class<?> oClass = Loader.loadClass(XmlConfiguration.class,clazz);
            
            // Find the <Arg> elements
            Map<String, Object> namedArgMap = new HashMap<>();
            List<Object> arguments = new LinkedList<>();
            for (XmlParser.Node child : argNodes)
            {
                String namedAttribute = child.getAttribute("name");
                Object value=value(obj,child);
                if (namedAttribute != null)
                {
                    // named arguments
                    namedArgMap.put(namedAttribute,value);
                }
                // raw arguments
                arguments.add(value);
            }

            Object nobj;
            try
            {
                if (namedArgMap.size() > 0)
                {
                   LOG.debug("using named mapping");
                   nobj = TypeUtil.construct(oClass, arguments.toArray(), namedArgMap);
                }
                else
                {
                    LOG.debug("using normal mapping");
                    nobj = TypeUtil.construct(oClass, arguments.toArray());
                }
            }
            catch (NoSuchMethodException e)
            {
                throw new IllegalStateException("No suitable constructor: " + node + " on " + obj);
            }

            if (id != null)
                _configuration.getIdMap().put(id, nobj);
            
            _configuration.initializeDefaults(nobj);
            configure(nobj,node,aoeNode.getNext());
            return nobj;
        }

        /*
         * Reference an id value object.
         *
         * @param obj @param node @return @exception NoSuchMethodException @exception ClassNotFoundException @exception InvocationTargetException
         */
        private Object refObj(Object obj, XmlParser.Node node) throws Exception
        {
            String refid = node.getAttribute("refid");
            if (refid==null)
                refid = node.getAttribute("id");
            obj = _configuration.getIdMap().get(refid);
            if (obj == null && node.size()>0)
                throw new IllegalStateException("No object for refid=" + refid);
            configure(obj,node,0);
            return obj;
        }

        /*
         * Create a new array object.
         */
        private Object newArray(Object obj, XmlParser.Node node) throws Exception
        {
            AttrOrElementNode aoeNode=new AttrOrElementNode(obj,node,"Id","Type","Item");
            String id = aoeNode.getString("Id");
            String type = aoeNode.getString("Type");
            List<XmlParser.Node> items = aoeNode.getNodes("Item");
            
            // Get the type
            Class<?> aClass = java.lang.Object.class;
            if (type != null)
            {
                aClass = TypeUtil.fromName(type);
                if (aClass == null)
                {
                    switch (type)
                    {
                        case "String":
                            aClass = String.class;
                            break;
                        case "URL":
                            aClass = URL.class;
                            break;
                        case "InetAddress":
                            aClass = InetAddress.class;
                            break;
                        default:
                            aClass = Loader.loadClass(XmlConfiguration.class, type);
                            break;
                    }
                }
            }
            
            Object al = null;

            for (XmlParser.Node item : items)
            {
                String nid = item.getAttribute("id");
                Object v = value(obj,item);
                al = LazyList.add(al,(v == null && aClass.isPrimitive())?0:v);
                if (nid != null)
                    _configuration.getIdMap().put(nid,v);
            }

            Object array = LazyList.toArray(al,aClass);
            if (id != null)
                _configuration.getIdMap().put(id,array);
            return array;
        }

        /*
         * Create a new map object.
         */
        private Object newMap(Object obj, XmlParser.Node node) throws Exception
        {
            AttrOrElementNode aoeNode=new AttrOrElementNode(node,"Id","Entry");
            String id = aoeNode.getString("Id");
            List<XmlParser.Node> entries = aoeNode.getNodes("Entry");

            Map<Object, Object> map = new HashMap<>();
            if (id != null)
                _configuration.getIdMap().put(id, map);

            for (XmlParser.Node entry : entries)
            {
                if (!entry.getTag().equals("Entry"))
                    throw new IllegalStateException("Not an Entry");

                XmlParser.Node key = null;
                XmlParser.Node value = null;

                for (Object object : entry)
                {
                    if (object instanceof String)
                        continue;
                    XmlParser.Node item = (XmlParser.Node)object;
                    if (!item.getTag().equals("Item"))
                        throw new IllegalStateException("Not an Item");
                    if (key == null)
                        key = item;
                    else
                        value = item;
                }

                if (key == null || value == null)
                    throw new IllegalStateException("Missing Item in Entry");
                String kid = key.getAttribute("id");
                String vid = value.getAttribute("id");

                Object k = value(obj,key);
                Object v = value(obj,value);
                map.put(k,v);

                if (kid != null)
                    _configuration.getIdMap().put(kid,k);
                if (vid != null)
                    _configuration.getIdMap().put(vid,v);
            }

            return map;
        }

        /*
         * Get a Property.
         *
         * @param node
         * @return
         * @exception Exception
         */
        private Object propertyObj(XmlParser.Node node) throws Exception
        {
            AttrOrElementNode aoeNode=new AttrOrElementNode(node,"Id","Name","Deprecated","Default");
            String id = aoeNode.getString("Id");
            String name = aoeNode.getString("Name",true);
            List<Object> deprecated = aoeNode.getList("Deprecated");
            String dftValue = aoeNode.getString("Default");

            // Look for a value
            Map<String,String> properties = _configuration.getProperties();
            String value = properties.get(name);
            
            // Look for a deprecated name value

            String alternate=null;
            if (!deprecated.isEmpty())
            {
                for (Object d : deprecated)
                { 
                    String v = properties.get(StringUtil.valueOf(d));
                    if (v!=null)
                    {
                        if (value==null)
                            LOG.warn("Property '{}' is deprecated, use '{}' instead", d, name);
                        else
                            LOG.warn("Property '{}' is deprecated, value from '{}' used", d, name);
                    }
                    if (alternate==null)
                        alternate=v;;
                }
            }

            // use alternate from deprecated
            if (value==null)
                value=alternate;
            
            // use default value
            if (value==null)
                value=dftValue;

            // Set value if ID set
            if (id != null)
                _configuration.getIdMap().put(id, value);
            return value;
        }

        /*
         * Get a SystemProperty.
         *
         * @param node
         * @return
         * @exception Exception
         */
        private Object systemPropertyObj(XmlParser.Node node) throws Exception
        {
            AttrOrElementNode aoeNode=new AttrOrElementNode(node,"Id","Name","Deprecated","Default");
            String id = aoeNode.getString("Id");
            String name = aoeNode.getString("Name",true);
            List<Object> deprecated = aoeNode.getList("Deprecated");
            String dftValue = aoeNode.getString("Default");

            // Look for a value
            String value = System.getProperty(name);
            
            // Look for a deprecated name value
            String alternate=null;
            if (!deprecated.isEmpty())
            {
                for (Object d : deprecated)
                { 
                    String v = System.getProperty(StringUtil.valueOf(d));
                    if (v!=null)
                    {
                        if (value==null)
                            LOG.warn("SystemProperty '{}' is deprecated, use '{}' instead", d, name);
                        else
                            LOG.warn("SystemProperty '{}' is deprecated, value from '{}' used", d, name);
                    }
                    if (alternate==null)
                        alternate=v;;
                }
            }

            // use alternate from deprecated
            if (value==null)
                value=alternate;
            
            // use default value
            if (value==null)
                value=dftValue;

            // Set value if ID set
            if (id != null)
                _configuration.getIdMap().put(id, value);

            return value;
        }
        
        /*
         * Get a Environment Property.
         *
         * @param node
         * @return
         * @exception Exception
         */
        private Object envObj(XmlParser.Node node) throws Exception
        {
            AttrOrElementNode aoeNode=new AttrOrElementNode(node,"Id","Name","Deprecated","Default");
            String id = aoeNode.getString("Id");
            String name = aoeNode.getString("Name",true);
            List<Object> deprecated = aoeNode.getList("Deprecated");
            String dftValue = aoeNode.getString("Default");

            // Look for a value
            String value = System.getenv(name);
            
            // Look for a deprecated name value
            if (value==null && !deprecated.isEmpty())
            {
                for (Object d : deprecated)
                {
                    value = System.getenv(StringUtil.valueOf(d));
                    if (value!=null)
                    {
                        LOG.warn("Property '{}' is deprecated, use '{}' instead", d, name);
                        break;
                    }
                }
            }
            
            // use default value
            if (value==null)
                value=dftValue;

            // Set value if ID set
            if (id != null)
                _configuration.getIdMap().put(id, value);

            return value;
        }

        /*
         * Get the value of an element. If no value type is specified, then white space is trimmed out of the value. If it contains multiple value elements they
         * are added as strings before being converted to any specified type. @param node
         */
        private Object value(Object obj, XmlParser.Node node) throws Exception
        {
            Object value;

            // Get the type
            String type = node.getAttribute("type");

            // Try a ref lookup
            String ref = node.getAttribute("ref");
            if (ref != null)
            {
                value = _configuration.getIdMap().get(ref);
            }
            else
            {
                // handle trivial case
                if (node.size() == 0)
                {
                    if ("String".equals(type))
                        return "";
                    return null;
                }

                // Trim values
                int first = 0;
                int last = node.size() - 1;

                // Handle default trim type
                if (type == null || !"String".equals(type))
                {
                    // Skip leading white
                    Object item;
                    while (first <= last)
                    {
                        item = node.get(first);
                        if (!(item instanceof String))
                            break;
                        item = ((String)item).trim();
                        if (((String)item).length() > 0)
                            break;
                        first++;
                    }

                    // Skip trailing white
                    while (first < last)
                    {
                        item = node.get(last);
                        if (!(item instanceof String))
                            break;
                        item = ((String)item).trim();
                        if (((String)item).length() > 0)
                            break;
                        last--;
                    }

                    // All white, so return null
                    if (first > last)
                        return null;
                }

				if (first == last) { // Single Item value
                    value = itemValue(obj,node.get(first));
				} else {
                    // Get the multiple items as a single string
                    StringBuilder buf = new StringBuilder();
					for (int i = first; i <= last; i++) {
                        Object item = node.get(i);
                        buf.append(itemValue(obj,item));
                    }
                    value = buf.toString();
                }
            }

            // Untyped or unknown
			if (value == null) {
				if ("String".equals(type)) {
					return "";
				}
                return null;
            }

            // Try to type the object
			if (type == null) {
				if (value instanceof String) {
					return ((String) value).trim();
				}
                return value;
            }

			if (isTypeMatchingClass(type, String.class)) {
				return value.toString();
			}

            Class<?> pClass = TypeUtil.fromName(type);
			if (pClass != null) {
				return TypeUtil.valueOf(pClass, value.toString());
			}

			if (isTypeMatchingClass(type, URL.class)) {
				if (value instanceof URL) {
					return value;
				}
				try {
                    return new URL(value.toString());
				} catch (MalformedURLException e) {
                    throw new InvocationTargetException(e);
                }
            }

			if (isTypeMatchingClass(type, InetAddress.class)) {
				if (value instanceof InetAddress) {
					return value;
				}
				try {
                    return InetAddress.getByName(value.toString());
				} catch (UnknownHostException e) {
                    throw new InvocationTargetException(e);
                }
            }

			for (Class<?> collectionClass : __supportedCollections) {
                if (isTypeMatchingClass(type,collectionClass))
                    return convertArrayToCollection(value,collectionClass);
            }

            throw new IllegalStateException("Unknown type " + type);
        }

		private static boolean isTypeMatchingClass(String type, Class<?> classToMatch) {
            return classToMatch.getSimpleName().equalsIgnoreCase(type) || classToMatch.getName().equals(type);
        }

        /*
         * Get the value of a single element. @param obj @param item @return @exception Exception
         */
		private Object itemValue(Object obj, Object item) throws Exception {
            // String value
            if (item instanceof String)
                return item;

            XmlParser.Node node = (XmlParser.Node)item;
            String tag = node.getTag();
            if ("Call".equals(tag))
                return call(obj,node);
            if ("Get".equals(tag))
                return get(obj,node);
            if ("New".equals(tag))
                return newObj(obj,node);
            if ("Ref".equals(tag))
                return refObj(obj,node);
            if ("Array".equals(tag))
                return newArray(obj,node);
            if ("Map".equals(tag))
                return newMap(obj,node);
            if ("Property".equals(tag))
                return propertyObj(node);
            if ("SystemProperty".equals(tag))
                return systemPropertyObj(node);
            if ("Env".equals(tag))
                return envObj(node);

            LOG.warn("Unknown value tag: " + node,new Throwable());
            return null;
        }
        

		private class AttrOrElementNode {
            final Object _obj;
            final XmlParser.Node _node;
            final Set<String> _elements = new HashSet<>();
            final int _next;

			AttrOrElementNode(XmlParser.Node node, String... elements) {
                this(null,node,elements);
            }
            
			AttrOrElementNode(Object obj, XmlParser.Node node, String... elements) {
                _obj=obj;
                _node=node;
				for (String e : elements) {
					_elements.add(e);
				}
                
                int next=0;
				for (Object o : _node) {
					if (o instanceof String) {
						if (((String) o).trim().length() == 0) {
                            next++;
                            continue;
                        }
                        break;
                    }
                    
					if (!(o instanceof XmlParser.Node)) {
						break;
					}
                    
                    XmlParser.Node n = (XmlParser.Node)o;
					if (!_elements.contains(n.getTag())) {
						break;
					}
                    
                    next++;
                }
                _next=next;
            }

			public int getNext() {
                return _next;
            }

			public String getString(String elementName) throws Exception {
                return StringUtil.valueOf(get(elementName,false));
            }
            
			public String getString(String elementName, boolean manditory) throws Exception {
                return StringUtil.valueOf(get(elementName,manditory));
            }
            
			public Object get(String elementName, boolean manditory) throws Exception {
                String attrName=StringUtil.asciiToLowerCase(elementName);
                String attr = _node.getAttribute(attrName);
                Object value=attr;
                
				for (int i = 0; i < _next; i++) {
                    Object o = _node.get(i);
					if (!(o instanceof XmlParser.Node)) {
						continue;
					}
                    XmlParser.Node n = (XmlParser.Node)o;
					if (elementName.equals(n.getTag())) {
						if (attr != null) {
							throw new IllegalStateException(
									"Cannot have attr '" + attrName + "' and element '" + elementName + "'");
						}

                        value=value(_obj,n);
                        break;
                    }
                }
                
				if (manditory && value == null) {
					throw new IllegalStateException(
							"Must have attr '" + attrName + "' or element '" + elementName + "'");
				}
                
                return value;
            }

			public List<Object> getList(String elementName) throws Exception {
                return getList(elementName,false);
            }
            
			public List<Object> getList(String elementName, boolean manditory) throws Exception {
                String attrName=StringUtil.asciiToLowerCase(elementName);
                final List<Object> values=new ArrayList<>();
                
                String attr = _node.getAttribute(attrName);
				if (attr != null) {
					values.addAll(StringUtil.csvSplit(null, attr, 0, attr.length()));
				}


				for (int i = 0; i < _next; i++) {
                    Object o = _node.get(i);
					if (!(o instanceof XmlParser.Node)) {
						continue;
					}
                    XmlParser.Node n = (XmlParser.Node)o;
                    
					if (elementName.equals(n.getTag())) {
						if (attr != null) {
							throw new IllegalStateException(
									"Cannot have attr '" + attrName + "' and element '" + elementName + "'");
						}

                        values.add(value(_obj,n));
                    }
                }
                
				if (manditory && values.isEmpty()) {
					throw new IllegalStateException(
							"Must have attr '" + attrName + "' or element '" + elementName + "'");
				}

                return values;
            }
            
			public List<XmlParser.Node> getNodes(String elementName) throws Exception {
                String attrName=StringUtil.asciiToLowerCase(elementName);
                final List<XmlParser.Node> values=new ArrayList<>();
                
                String attr = _node.getAttribute(attrName);
				if (attr != null) {
					for (String a : StringUtil.csvSplit(null, attr, 0, attr.length())) {

                        XmlParser.Node n = new XmlParser.Node(null,elementName,null);
                        n.add(a);
                        values.add(n);
                    }
                }

				for (int i = 0; i < _next; i++) {
                    Object o = _node.get(i);
					if (!(o instanceof XmlParser.Node)) {
						continue;
					}
                    XmlParser.Node n = (XmlParser.Node)o;
                    
					if (elementName.equals(n.getTag())) {
						if (attr != null) {
							throw new IllegalStateException(
									"Cannot have attr '" + attrName + "' and element '" + elementName + "'");
						}

                        values.add(n);
                    }
                }

                return values;
            }
        }
    }

    public static void main(final String... args) throws Exception {
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
			public Object run() {
                try {
                    Properties properties = null;
                    try {//look for properties from start.jar
                        Class<?> config = XmlConfiguration.class.getClassLoader().loadClass("org.eclipse.jetty.start.Config");
                        properties = (Properties)config.getMethod("getProperties").invoke(null);
                        LOG.debug("org.eclipse.jetty.start.Config properties = {}", properties);
                    } catch (NoClassDefFoundError | ClassNotFoundException e) {
                        LOG.ignore(e);
                    } catch (Exception e) {
                        LOG.warn(e);
                    }
                    if (properties == null) {//if no start.config properties, use clean slate
                        properties = new Properties();//Add System Properties
                        properties.putAll(System.getProperties());
                    }
                    for (String arg : args) {//for all arguments, load properties
                        if (arg.indexOf('=') >= 0) {
                            int i = arg.indexOf('=');
                            properties.put(arg.substring(0, i), arg.substring(i + 1));
                        } else if (arg.toLowerCase(Locale.ENGLISH).endsWith(".properties")) {
                            properties.load(Resource.newResource(arg).getInputStream());
						}
                    }
                    XmlConfiguration last = null;// For all arguments, parse XMLs
                    Object[] obj = new Object[args.length];
                    for (int i = 0; i < args.length; i++) {
						if (!args[i].toLowerCase(Locale.ENGLISH).endsWith(".properties")
								&& (args[i].indexOf('=') < 0)) {
                            XmlConfiguration configuration = new XmlConfiguration(Resource.newResource(args[i]).getURI().toURL());
							if (last != null) {
								configuration.getIdMap().putAll(last.getIdMap());
							}
							if (properties.size() > 0) {
                                Map<String, String> props = new HashMap<>();
								for (Object key : properties.keySet()) {
                                    props.put(key.toString(),String.valueOf(properties.get(key)));
                                }
                                configuration.getProperties().putAll(props);
                            }
                            obj[i] = configuration.configure();
                            last = configuration;
                        }
                    }

                    for (int i = 0; i < args.length; i++) {
                        if (obj[i] instanceof LifeCycle) {
                            LifeCycle lc = (LifeCycle)obj[i];
							if (!lc.isRunning()) {
								lc.start();// 其中包括了服务器
							}
                        }
                    }
                } catch (Exception e) {
                    LOG.debug(Log.EXCEPTION,e);
                    exception.set(e);
                }
                return null;
            }
        });

        Throwable th = exception.get();

        if (th != null) {
            if (th instanceof RuntimeException) {
                throw (RuntimeException)th;
            } else if (th instanceof Exception) {
                throw (Exception)th;
			} else if (th instanceof Error) {
                throw (Error)th;
			}
            throw new Error(th);
        }
    }
}
