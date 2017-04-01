package org.eclipse.jetty.util;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.jetty.util.resource.Resource;

public class Loader {// 加载器

	public static URL getResource(Class<?> loadClass, String name) {// 用于加载资源(如配置文件等)

		// 第一个参数表示函数被调用的所在类
		// 第二个参数表示资源路径

		URL url = null;

		ClassLoader context_loader = Thread.currentThread().getContextClassLoader();// 首先使用线程的上下文类加载器--动态加载

        if (context_loader != null) {
			url = context_loader.getResource(name);// 加载资源
		}

		if (url == null && loadClass != null) {// 静态加载

			ClassLoader load_loader = loadClass.getClassLoader();// 类所在的类加载器

            if (load_loader != null && load_loader != context_loader) {
				url = load_loader.getResource(name);
			}

        }

        if (url == null) {
			url = ClassLoader.getSystemResource(name);// 表示系统的类加载器
		}

        return url;
    }

	@SuppressWarnings("rawtypes")
	public static Class loadClass(Class loadClass, String name) throws ClassNotFoundException {// 用于加载类的

        ClassNotFoundException ex = null;

        Class<?> c = null;

        ClassLoader context_loader = Thread.currentThread().getContextClassLoader();

        if (context_loader != null ) {

            try { 
				c = context_loader.loadClass(name); 
			} catch (ClassNotFoundException e) {
				ex = e;
			}
		}

        if (c == null && loadClass != null) {

            ClassLoader load_loader = loadClass.getClassLoader();

            if (load_loader != null && load_loader != context_loader) {

                try { 
					c = load_loader.loadClass(name); 
				} catch (ClassNotFoundException e) {
					if (ex == null) {
						ex = e;
					}
				}
            }
        }

        if (c == null) {
            try { 
				c = Class.forName(name); 
			} catch (ClassNotFoundException e) {
                if (ex != null) {
                    throw ex;
				}
                throw e;
            }
		}

        return c;
    }

	public static ResourceBundle getResourceBundle(Class<?> loadClass, String name, boolean checkParents, Locale locale) throws MissingResourceException {

		MissingResourceException ex = null;

		ResourceBundle bundle = null;

		ClassLoader loader = Thread.currentThread().getContextClassLoader();

		while (bundle == null && loader != null) {

			try {
				bundle = ResourceBundle.getBundle(name, locale, loader); // 获取BUNDLE
			} catch (MissingResourceException e) {
				if (ex == null) {
					ex=e;
				}
			}

            loader = (bundle == null && checkParents) ? loader.getParent() : null;
		}

        loader = loadClass == null ? null : loadClass.getClassLoader();

        while (bundle == null && loader != null ) {

            try { 
				bundle = ResourceBundle.getBundle(name, locale, loader); 
			} catch (MissingResourceException e) {
				if (ex == null) {
					ex = e;
				}
			}
            loader = (bundle == null && checkParents) ? loader.getParent() : null;
        }       

        if (bundle == null) {
            try { 
				bundle = ResourceBundle.getBundle(name, locale); 
			} catch (MissingResourceException e) {
				if (ex == null)
					ex=e;
			}
		}

		if (bundle != null) {
			return bundle;
		}

        throw ex;
    }

	public static String getClassPath(ClassLoader loader) throws Exception {// 获取路径

        StringBuilder classpath = new StringBuilder();

        while (loader != null && (loader instanceof URLClassLoader)) {

			URL[] urls = ((URLClassLoader) loader).getURLs();// 返回搜索路径

			if (urls != null) {

                for (int i = 0; i < urls.length; i++) {

                    Resource resource = Resource.newResource(urls[i]);

                    File file = resource.getFile();

                    if (file != null && file.exists()) {

						if (classpath.length() > 0) {
							classpath.append(File.pathSeparatorChar);
						}

                        classpath.append(file.getAbsolutePath());
                    }
                }
            }

            loader = loader.getParent();
        }

        return classpath.toString();
    }
}

