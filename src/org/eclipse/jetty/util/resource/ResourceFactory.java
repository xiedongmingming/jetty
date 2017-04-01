package org.eclipse.jetty.util.resource;

public interface ResourceFactory {// 顶层接口
	Resource getResource(String path);// 表示根据指定的字符串路径生成资源类
}
