package extral.org.eclipse.jetty.util.ajax;

import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import extral.org.eclipse.jetty.util.ajax.JSON.Output;

/* ------------------------------------------------------------ */
/**
 * Convert an {@link Enum} to JSON.
 * If fromJSON is true in the constructor, the JSON generated will
 * be of the form {class="com.acme.TrafficLight",value="Green"}
 * If fromJSON is false, then only the string value of the enum is generated.
 *
 *
 */
public class JSONEnumConvertor implements JSON.Convertor
{
    private static final Logger LOG = Log.getLogger(JSONEnumConvertor.class);
    private boolean _fromJSON;
    private Method _valueOf;
    {
        try
        {
            Class<?> e = Loader.loadClass(getClass(),"java.lang.Enum");
            _valueOf=e.getMethod("valueOf",Class.class,String.class);
        }
        catch(Exception e)
        {
            throw new RuntimeException("!Enums",e);
        }
    }

    public JSONEnumConvertor()
    {
        this(false);
    }

    public JSONEnumConvertor(boolean fromJSON)
    {
        _fromJSON=fromJSON;
    }

	@SuppressWarnings("rawtypes")
	@Override
	public Object fromJSON(Map map)
    {
        if (!_fromJSON)
            throw new UnsupportedOperationException();
        try
        {
            Class c=Loader.loadClass(getClass(),(String)map.get("class"));
            return _valueOf.invoke(null,c,map.get("value"));
        }
        catch(Exception e)
        {
            LOG.warn(e);
        }
        return null;
    }

	@SuppressWarnings("rawtypes")
	@Override
	public void toJSON(Object obj, Output out)
    {
        if (_fromJSON)
        {
            out.addClass(obj.getClass());
            out.add("value",((Enum)obj).name());
        }
        else
        {
            out.add(((Enum)obj).name());
        }
    }
}
