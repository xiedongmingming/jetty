package org.eclipse.jetty.util;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("serial")
public class ArrayUtil implements Cloneable, Serializable {

	public static <T> T[] removeFromArray(T[] array, Object item) {
		if (item == null || array == null) {
			return array;
		}
		for (int i = array.length; i-- > 0;) {
			if (item.equals(array[i])) {
				Class<?> c = array == null ? item.getClass() : array.getClass().getComponentType();
                @SuppressWarnings("unchecked")
                T[] na = (T[])Array.newInstance(c, Array.getLength(array)-1);
				if (i > 0) {
					System.arraycopy(array, 0, na, 0, i);
				}
				if (i + 1 < array.length) {
					System.arraycopy(array, i + 1, na, i, array.length - (i + 1));
				}
                return na;
            }
        }
        return array;
    }

	public static <T> T[] addToArray(T[] array, T item, Class<?> type) {// 将指定元素添加到数组中

		// 第一个参数表示数组(可能为空)
		// 第二个参数表示待加入的数据
		// 第三个参数表示待加入数据的类型(以防止数组为空)

		if (array == null) {

			if (type == null && item != null) {
				type = item.getClass();
			}

            @SuppressWarnings("unchecked")
			T[] na = (T[]) Array.newInstance(type, 1);

			na[0] = item;

            return na;
		} else {

			T[] na = Arrays.copyOf(array, array.length + 1);

			na[array.length] = item;

            return na;
        }
    }

	public static <T> T[] prependToArray(T item, T[] array, Class<?> type) {// 将元素添加到数组的开始位置

		// 第一个参数表示待加入的对象(加入到数组的开始位置)
		// 第二个参数表示加入到的数组
		// 第三个参数表示数组元素的类型(防止数组为空)

		if (array == null) {// 如果数组为空

			if (type == null && item != null) {// 采用ITEM的类型
				type = item.getClass();
			}

			@SuppressWarnings("unchecked")
			T[] na = (T[]) Array.newInstance(type, 1);// 生成一个数组

			na[0] = item;

            return na;

		} else {

			Class<?> c = array.getClass().getComponentType();// 获取数组的元素类型

			@SuppressWarnings("unchecked")
			T[] na = (T[]) Array.newInstance(c, Array.getLength(array) + 1);// 重新生成一个数组

            System.arraycopy(array, 0, na, 1, array.length);

			na[0] = item;

            return na;
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @param array Any array of object
     * @return A new <i>modifiable</i> list initialised with the elements from <code>array</code>.
     * @param <E> the array entry type
     */
	public static <E> List<E> asMutableList(E[] array) {
		if (array == null || array.length == 0) {
			return new ArrayList<E>();
		}
        return new ArrayList<E>(Arrays.asList(array));
    }
	public static <T> T[] removeNulls(T[] array) {
		for (T t : array) {
			if (t == null) {
                List<T> list = new ArrayList<>();
				for (T t2 : array) {
					if (t2 != null) {
						list.add(t2);
					}
				}
				return list.toArray(Arrays.copyOf(array, list.size()));
            }
        }
        return array;
    }
}

