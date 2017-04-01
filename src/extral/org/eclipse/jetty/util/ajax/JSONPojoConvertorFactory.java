package extral.org.eclipse.jetty.util.ajax;

import java.util.Map;

import org.eclipse.jetty.util.Loader;

import extral.org.eclipse.jetty.util.ajax.JSON.Convertor;
import extral.org.eclipse.jetty.util.ajax.JSON.Output;

public class JSONPojoConvertorFactory implements JSON.Convertor {
    private final JSON _json;
    private final boolean _fromJson;

	public JSONPojoConvertorFactory(JSON json) {
		if (json == null) {
            throw new IllegalArgumentException();
        }
        _json=json;
        _fromJson=true;
    }

	public JSONPojoConvertorFactory(JSON json, boolean fromJSON) {
		if (json == null) {
            throw new IllegalArgumentException();
        }
        _json=json;
        _fromJson=fromJSON;
    }
	@Override
	@SuppressWarnings("rawtypes")
	public void toJSON(Object obj, Output out) {
        String clsName=obj.getClass().getName();
        Convertor convertor=_json.getConvertorFor(clsName);
		if (convertor == null) {
			try {
                Class cls=Loader.loadClass(JSON.class,clsName);
                convertor=new JSONPojoConvertor(cls,_fromJson);
                _json.addConvertorFor(clsName, convertor);
			} catch (ClassNotFoundException e) {
                JSON.LOG.warn(e);
            }
        }
		if (convertor != null) {
            convertor.toJSON(obj, out);
        }
    }

	@SuppressWarnings("rawtypes")
	@Override
	public Object fromJSON(Map object) {
        Map map=object;
        String clsName=(String)map.get("class");
		if (clsName != null) {
            Convertor convertor=_json.getConvertorFor(clsName);
			if (convertor == null) {
				try {
                    Class cls=Loader.loadClass(JSON.class,clsName);
                    convertor=new JSONPojoConvertor(cls,_fromJson);
                    _json.addConvertorFor(clsName, convertor);
				} catch (ClassNotFoundException e) {
                    JSON.LOG.warn(e);
                }
            }
			if (convertor != null) {
                return convertor.fromJSON(object);
            }
        }
        return map;
    }
}
