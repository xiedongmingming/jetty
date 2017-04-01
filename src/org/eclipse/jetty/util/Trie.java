package org.eclipse.jetty.util;

import java.nio.ByteBuffer;
import java.util.Set;

public interface Trie<V> {// 实现树形查找(接口)--T表示存放的对象

	// TRIE树:字典树
	// 又称单词查找树(树形结构--是一种哈希树的变种)

    public boolean put(String s, V v);
    public boolean put(V v);
    public V remove(String s);
    public V get(String s);
    public V get(String s,int offset,int len);
    public V get(ByteBuffer b);
    public V get(ByteBuffer b,int offset,int len);
    public V getBest(String s);
    public V getBest(String s,int offset,int len); 
    public V getBest(byte[] b,int offset,int len);
    public V getBest(ByteBuffer b,int offset,int len);
    public Set<String> keySet();
    public boolean isFull();
    public boolean isCaseInsensitive();
    public void clear();
}
