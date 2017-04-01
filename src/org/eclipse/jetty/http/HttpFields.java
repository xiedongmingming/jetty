package org.eclipse.jetty.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.jetty.util.ArrayTernaryTrie;
import org.eclipse.jetty.util.QuotedStringTokenizer;
import org.eclipse.jetty.util.Trie;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class HttpFields implements Iterable<HttpField> {// 多个域的集合

    @Deprecated
    public static final String __separators = ", \t";

    private static final Logger LOG = Log.getLogger(HttpFields.class);

	private HttpField[] _fields;// 表示HTTP请求中携带的所有域
    private int _size;

	// ********************************************************************************
	public HttpFields() {
		_fields = new HttpField[20];
    }
	public HttpFields(int capacity) {
		_fields = new HttpField[capacity];
    }
	public HttpFields(HttpFields fields) {
		_fields = Arrays.copyOf(fields._fields, fields._fields.length + 10);
		_size = fields._size;
    }
	// ********************************************************************************
	public int size() {
        return _size;
    }
    
    @Override
	public Iterator<HttpField> iterator() {
        return new Itr();
    }
	public Set<String> getFieldNamesCollection() {
        final Set<String> set = new HashSet<>(_size);
		for (HttpField f : this) {
			if (f != null) {
				set.add(f.getName());
			}
        }
        return set;
    }

	public Enumeration<String> getFieldNames() {
        return Collections.enumeration(getFieldNamesCollection());
    }

	public HttpField getField(int index) {
		if (index >= _size) {
			throw new NoSuchElementException();
		}
        return _fields[index];
    }
	public HttpField getField(HttpHeader header) {
		for (int i = 0; i < _size; i++) {
            HttpField f=_fields[i];
			if (f.getHeader() == header) {
				return f;
			}
        }
        return null;
    }
	public HttpField getField(String name) {
		for (int i = 0; i < _size; i++) {
            HttpField f=_fields[i];
			if (f.getName().equalsIgnoreCase(name)) {
				return f;
			}
        }
        return null;
    }
	public boolean contains(HttpField field) {
		for (int i = _size; i-- > 0;) {
			HttpField f = _fields[i];
			if (f.isSameName(field) && f.contains(field.getValue())) {
				return true;
			}
        }
        return false;
    }
	public boolean contains(HttpHeader header, String value) {
		for (int i = _size; i-- > 0;) {
            HttpField f=_fields[i];
			if (f.getHeader() == header && f.contains(value)) {
				return true;
			}
        }
        return false;
    }
	public boolean contains(String name, String value) {
		for (int i = _size; i-- > 0;) {
            HttpField f=_fields[i];
			if (f.getName().equalsIgnoreCase(name) && f.contains(value)) {
				return true;
			}
        }
        return false;
    }
	public boolean contains(HttpHeader header) {
		for (int i = _size; i-- > 0;) {
            HttpField f=_fields[i];
			if (f.getHeader() == header) {
				return true;
			}
        }
        return false;
    }
	public boolean containsKey(String name) {
		for (int i = _size; i-- > 0;) {
            HttpField f=_fields[i];
			if (f.getName().equalsIgnoreCase(name)) {
				return true;
			}
        }
        return false;
    }

    @Deprecated
	public String getStringField(HttpHeader header) {
        return get(header);
    }
    
	public String get(HttpHeader header) {// 获取指定域的值
		for (int i = 0; i < _size; i++) {
			HttpField f = _fields[i];
			if (f.getHeader() == header) {
				return f.getValue();
			}
        }
        return null;
    }

    @Deprecated
	public String getStringField(String name) {
        return get(name);
    }
    
	public String get(String header) {// 比如--"Content-Length"
		for (int i = 0; i < _size; i++) {
			HttpField f = _fields[i];
			if (f.getName().equalsIgnoreCase(header)) {
				return f.getValue();
			}
        }
        return null;
    }

	public List<String> getValuesList(HttpHeader header) {
        final List<String> list = new ArrayList<>();
		for (HttpField f : this) {
			if (f.getHeader() == header) {
				list.add(f.getValue());
			}
		}
        return list;
    }
	public List<String> getValuesList(String name) {
        final List<String> list = new ArrayList<>();
		for (HttpField f : this) {
			if (f.getName().equalsIgnoreCase(name)) {
				list.add(f.getValue());
			}
		}
        return list;
    }
	public List<String> getCSV(HttpHeader header, boolean keepQuotes) {
        QuotedCSV values = new QuotedCSV(keepQuotes);
		for (HttpField f : this) {
			if (f.getHeader() == header) {
                values.addValue(f.getValue());
			}
		}
        return values.getValues();
    }

	public List<String> getCSV(String name, boolean keepQuotes) {
        QuotedCSV values = new QuotedCSV(keepQuotes);
		for (HttpField f : this) {
			if (f.getName().equalsIgnoreCase(name)) {
				values.addValue(f.getValue());
			}
		}
        return values.getValues();
    }

	public List<String> getQualityCSV(HttpHeader header) {
        QuotedQualityCSV values = new QuotedQualityCSV();
		for (HttpField f : this) {
			if (f.getHeader() == header) {
				values.addValue(f.getValue());
			}
		}
        return values.getValues();
    }

	public List<String> getQualityCSV(String name) {
        QuotedQualityCSV values = new QuotedQualityCSV();
		for (HttpField f : this) {
			if (f.getName().equalsIgnoreCase(name)) {
				values.addValue(f.getValue());
			}
		}
        return values.getValues();
    }

	public Enumeration<String> getValues(final String name) {
		for (int i = 0; i < _size; i++) {
            final HttpField f = _fields[i];
            
			if (f.getName().equalsIgnoreCase(name) && f.getValue() != null) {
                final int first=i;
				return new Enumeration<String>() {
                    HttpField field=f;
                    int i = first+1;

                    @Override
					public boolean hasMoreElements() {
						if (field == null) {
							while (i < _size) {
                                field=_fields[i++];
								if (field.getName().equalsIgnoreCase(name) && field.getValue() != null) {
									return true;
								}
                            }
                            field=null;
                            return false;
                        }
                        return true;
                    }

                    @Override
					public String nextElement() throws NoSuchElementException {
						if (hasMoreElements()) {
                            String value=field.getValue();
                            field=null;
                            return value;
                        }
                        throw new NoSuchElementException();
                    }
                };
            }
        }
        List<String> empty=Collections.emptyList();
        return Collections.enumeration(empty);
    }
    @Deprecated
	public Enumeration<String> getValues(String name, final String separators) {
        final Enumeration<String> e = getValues(name);
		if (e == null) {
			return null;
		}
		return new Enumeration<String>() {
            QuotedStringTokenizer tok = null;

            @Override
			public boolean hasMoreElements() {
                if (tok != null && tok.hasMoreElements()) return true;
				while (e.hasMoreElements()) {
                    String value = e.nextElement();
					if (value != null) {
                        tok = new QuotedStringTokenizer(value, separators, false, false);
						if (tok.hasMoreElements()) {
							return true;
						}
                    }
                }
                tok = null;
                return false;
            }

            @Override
			public String nextElement() throws NoSuchElementException {
				if (!hasMoreElements()) {
					throw new NoSuchElementException();
				}
                String next = (String) tok.nextElement();
				if (next != null) {
					next = next.trim();
				}
                return next;
            }
        };
    }

	public void put(HttpField field) {
        boolean put=false;
		for (int i = _size; i-- > 0;) {
            HttpField f=_fields[i];
			if (f.isSameName(field)) {
				if (put) {
                    System.arraycopy(_fields,i+1,_fields,i,--_size-i);
				} else {
                    _fields[i]=field;
                    put=true;
                }
            }
        }
		if (!put) {
			add(field);
		}
    }

	public void put(String name, String value) {
		if (value == null) {
			remove(name);
		} else {
			put(new HttpField(name, value));
		}
    }

	public void put(HttpHeader header, HttpHeaderValue value) {
        put(header,value.toString());
    }

	public void put(HttpHeader header, String value) {
		if (value == null) {
			remove(header);
		} else {
			put(new HttpField(header, value));
		}
    }

	public void put(String name, List<String> list) {
        remove(name);
		for (String v : list) {
			if (v != null) {
				add(name, v);
			}
		}
    }

	public void add(String name, String value) {
		if (value == null) {
			return;
		}
        HttpField field = new HttpField(name, value);
        add(field);
    }

	public void add(HttpHeader header, HttpHeaderValue value) {
        add(header,value.toString());
    }
	public void add(HttpHeader header, String value) {
		if (value == null) {
			throw new IllegalArgumentException("null value");
		}
        HttpField field = new HttpField(header, value);
        add(field);
    }

	public HttpField remove(HttpHeader name) {
        HttpField removed=null;
		for (int i = _size; i-- > 0;) {
			HttpField f = _fields[i];
			if (f.getHeader() == name) {
                removed=f;
                System.arraycopy(_fields,i+1,_fields,i,--_size-i);
            }
        }
        return removed;
    }

	public HttpField remove(String name) {
        HttpField removed=null;
		for (int i = _size; i-- > 0;) {
            HttpField f=_fields[i];
			if (f.getName().equalsIgnoreCase(name)) {
                removed=f;
                System.arraycopy(_fields,i+1,_fields,i,--_size-i);
            }
        }
        return removed;
    }

	public long getLongField(String name) throws NumberFormatException {
        HttpField field = getField(name);
        return field==null?-1L:field.getLongValue();
    }
	public long getDateField(String name) {
        HttpField field = getField(name);
		if (field == null) {
			return -1;
		}

        String val = valueParameters(field.getValue(), null);
		if (val == null) {
			return -1;
		}

        final long date = DateParser.parseDate(val);
		if (date == -1) {
			throw new IllegalArgumentException("Cannot convert date: " + val);
		}
        return date;
    }

	public void putLongField(HttpHeader name, long value) {
        String v = Long.toString(value);
        put(name, v);
    }

	public void putLongField(String name, long value) {
        String v = Long.toString(value);
        put(name, v);
    }

	public void putDateField(HttpHeader name, long date) {
        String d=DateGenerator.formatDate(date);
        put(name, d);
    }

	public void putDateField(String name, long date) {
        String d=DateGenerator.formatDate(date);
        put(name, d);
    }

	public void addDateField(String name, long date) {
        String d=DateGenerator.formatDate(date);
        add(name,d);
    }

    @Override
	public int hashCode() {
        int hash=0;
		for (HttpField field : _fields) {
			hash += field.hashCode();
		}
        return hash;
    }

    @Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof HttpFields)) {
			return false;
		}

        HttpFields that = (HttpFields)o;

        // Order is not important, so we cannot rely on List.equals().
		if (size() != that.size()) {
			return false;
		}

		loop: for (HttpField fi : this) {
			for (HttpField fa : that) {
				if (fi.equals(fa)) {
					continue loop;
				}
            }
            return false;
        }
        return true;
    }

    @Override
	public String toString() {
		try {
            StringBuilder buffer = new StringBuilder();
			for (HttpField field : this) {
				if (field != null) {
                    String tmp = field.getName();
					if (tmp != null) {
						buffer.append(tmp);
					}
                    buffer.append(": ");
                    tmp = field.getValue();
					if (tmp != null) {
						buffer.append(tmp);
					}
                    buffer.append("\r\n");
                }
            }
            buffer.append("\r\n");
            return buffer.toString();
		} catch (Exception e) {
            LOG.warn(e);
            return e.toString();
        }
    }

	public void clear() {
        _size=0;
    }
    
	public void add(HttpField field) {
		if (field != null) {
			if (_size == _fields.length) {
				_fields = Arrays.copyOf(_fields, _size * 2);
			}
            _fields[_size++]=field;
        }
    }

	public void addAll(HttpFields fields) {
		for (int i = 0; i < fields._size; i++) {
			add(fields._fields[i]);
		}
    }
	public void add(HttpFields fields) {
		if (fields == null) {
			return;
		}
        Enumeration<String> e = fields.getFieldNames();
		while (e.hasMoreElements()) {
            String name = e.nextElement();
            Enumeration<String> values = fields.getValues(name);
			while (values.hasMoreElements()) {
				add(name, values.nextElement());
			}
        }
    }
	public static String stripParameters(String value) {
		if (value == null) {
			return null;
		}

        int i = value.indexOf(';');
		if (i < 0) {
			return value;
		}
        return value.substring(0, i).trim();
    }

	public static String valueParameters(String value, Map<String, String> parameters) {

		if (value == null) {
			return null;
		}

        int i = value.indexOf(';');

		if (i < 0) {
			return value;
		}

		if (parameters == null) {
			return value.substring(0, i).trim();
		}

        StringTokenizer tok1 = new QuotedStringTokenizer(value.substring(i), ";", false, true);

		while (tok1.hasMoreTokens()) {

            String token = tok1.nextToken();

            StringTokenizer tok2 = new QuotedStringTokenizer(token, "= ");

			if (tok2.hasMoreTokens()) {

                String paramName = tok2.nextToken();

                String paramVal = null;

				if (tok2.hasMoreTokens()) {
					paramVal = tok2.nextToken();
				}
                parameters.put(paramName, paramVal);
            }
        }
        return value.substring(0, i).trim();
    }

    @Deprecated
    private static final Float __one = new Float("1.0");
    @Deprecated
    private static final Float __zero = new Float("0.0");
    @Deprecated
    private static final Trie<Float> __qualities = new ArrayTernaryTrie<>();
	static {
        __qualities.put("*", __one);
        __qualities.put("1.0", __one);
        __qualities.put("1", __one);
        __qualities.put("0.9", new Float("0.9"));
        __qualities.put("0.8", new Float("0.8"));
        __qualities.put("0.7", new Float("0.7"));
        __qualities.put("0.66", new Float("0.66"));
        __qualities.put("0.6", new Float("0.6"));
        __qualities.put("0.5", new Float("0.5"));
        __qualities.put("0.4", new Float("0.4"));
        __qualities.put("0.33", new Float("0.33"));
        __qualities.put("0.3", new Float("0.3"));
        __qualities.put("0.2", new Float("0.2"));
        __qualities.put("0.1", new Float("0.1"));
        __qualities.put("0", __zero);
        __qualities.put("0.0", __zero);
    }

    @Deprecated
	public static Float getQuality(String value) {
        if (value == null) return __zero;

        int qe = value.indexOf(";");
		if (qe++ < 0 || qe == value.length()) {
			return __one;
		}

		if (value.charAt(qe++) == 'q') {
            qe++;
            Float q = __qualities.get(value, qe, value.length() - qe);
			if (q != null) {
				return q;
			}
        }

        Map<String,String> params = new HashMap<>(4);
        valueParameters(value, params);
        String qs = params.get("q");
		if (qs == null) {
			qs = "*";
		}
        Float q = __qualities.get(qs);
		if (q == null) {
			try {
                q = new Float(qs);
			} catch (Exception e) {
                q = __one;
            }
        }
        return q;
    }
    @Deprecated
	public static List<String> qualityList(Enumeration<String> e) {
		if (e == null || !e.hasMoreElements()) {
			return Collections.emptyList();
		}
        QuotedQualityCSV values = new QuotedQualityCSV();
		while (e.hasMoreElements()) {
			values.addValue(e.nextElement());
		}
        return values.getValues();
    }
	private class Itr implements Iterator<HttpField> {
		int _cursor;// index of next element to return
		int _last = -1;

        @Override
		public boolean hasNext() {
            return _cursor != _size;
        }

        @Override
		public HttpField next() {
            int i = _cursor;
			if (i >= _size) {
				throw new NoSuchElementException();
			}
            _cursor = i + 1;
			return _fields[_last = i];
        }

        @Override
		public void remove() {
			if (_last < 0) {
				throw new IllegalStateException();
			}
			System.arraycopy(_fields, _last + 1, _fields, _last, --_size - _last);
			_cursor = _last;
			_last = -1;
        }
    }
}
