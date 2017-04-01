package extral.org.eclipse.jetty.util.ajax;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jetty.util.Loader;

public class JSONCollectionConvertor implements JSON.Convertor {
	@SuppressWarnings("rawtypes")
	@Override
	public void toJSON(Object obj, JSON.Output out) {
        out.addClass(obj.getClass());
        out.add("list", ((Collection)obj).toArray());
    }

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object fromJSON(Map object) {
		try {
            Collection result = (Collection)Loader.loadClass(getClass(), (String)object.get("class")).newInstance();
            Collections.addAll(result, (Object[])object.get("list"));
            return result;
		} catch (Exception x) {
			if (x instanceof RuntimeException) {
				throw (RuntimeException) x;
			}
            throw new RuntimeException(x);
        }
    }
}
