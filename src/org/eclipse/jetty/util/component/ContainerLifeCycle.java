package org.eclipse.jetty.util.component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ContainerLifeCycle extends AbstractLifeCycle implements Container, Destroyable, Dumpable {// 容器中可以存放容器(即其中的BEAN可以是容器)
    
	private static final Logger LOG = Log.getLogger(ContainerLifeCycle.class);
    
	private final List<Bean> _beans = new CopyOnWriteArrayList<>();// 表示该容器管理的所有BEAN

	private final List<Container.Listener> _listeners = new CopyOnWriteArrayList<>();// 表示对该容器所产生的事件进行监听的所有监听器????

	private boolean _doStarted = false;// 表示已经开始

	// ****************************************************************************
	public ContainerLifeCycle() {

    }

	// ****************************************************************************
	// 生命周期中的接口函数
    @Override
	protected void doStart() throws Exception {// 启动该容器中包含的所有BEAN(被该容器管理的BEAN)

		_doStarted = true;// 标志

		for (Bean b : _beans) {

			if (b._bean instanceof LifeCycle) {// 启动包含的所有生命周期对象

				LifeCycle l = (LifeCycle) b._bean;// 包括线程池、服务、处理器等

				switch (b._managed) {// 只处理下面两种类型的BEAN
					case MANAGED:// 表示需要被该容器管理的BEAN
					if (!l.isRunning()) {
						start(l);
					}
					break;
					case AUTO:// 自动状态(根据该BEAN的生命周期状态来判断是否需要改容器来管理)
					if (l.isRunning()) {// 不受该容器的管理
						unmanage(b);
					} else {
						manage(b);
						start(l);
					}
					break;
					default:
					break;
                }
            }
        }

        super.doStart();
    }
    @Override
	protected void doStop() throws Exception {

        _doStarted = false;

        super.doStop();

        List<Bean> reverse = new ArrayList<>(_beans);

		Collections.reverse(reverse);// 倒序

		for (Bean b : reverse) {// 只停止被该容器管理的BEAN
            if (b._managed == Managed.MANAGED && b._bean instanceof LifeCycle) {
				LifeCycle l = (LifeCycle) b._bean;
                stop(l);
            }
        }
    }

	// ****************************************************************************
	protected void start(LifeCycle l) throws Exception {// 启动指定的组件
		l.start();
	}
	protected void stop(LifeCycle l) throws Exception {// 停止指定的组件
		l.stop();
	}

	// ****************************************************************************
	// 容器中的接口函数
    @Override
	public boolean addBean(Object o) {// 表示为该容器添加一个BEAN

        if (o instanceof LifeCycle) {

			LifeCycle l = (LifeCycle) o;// 包括:QueuedThreadPool、ScheduledExecutorScheduler、HttpConnectionFactory、ServerConnector、ServletHolder、ServletContextHandler、DefaultHandler

			return addBean(o, l.isRunning() ? Managed.UNMANAGED : Managed.AUTO);//
        }

		return addBean(o, Managed.POJO);// (非生命周期组件为POJO)包括:HttpConfiguration、ServerSocketChannelImpl
    }
	@Override
	public Collection<Object> getBeans() {// 获取所有组件
		return getBeans(Object.class);
	}
	@Override
	public <T> Collection<T> getBeans(Class<T> clazz) {// 获取指定类或其之类的组件集合

		ArrayList<T> beans = new ArrayList<>();

		for (Bean b : _beans) {

			if (clazz.isInstance(b._bean)) {// 判断参数是不是目标类的类型或其子类型
				beans.add(clazz.cast(b._bean));// 将参数转换成目标类
			}
		}

		return beans;
	}
	@Override
	public <T> T getBean(Class<T> clazz) {// 找到对应类型的BEAN--找到的第一个

		for (Bean b : _beans) {

			if (clazz.isInstance(b._bean)) {
				return clazz.cast(b._bean);
			}
		}

		return null;
	}
	@Override
	public boolean removeBean(Object o) {

		Bean b = getBean(o);

		return b != null && remove(b);
	}
	@Override
	public void addEventListener(Container.Listener listener) {// 向容器中添加关注的监听器

		if (_listeners.contains(listener)) {// 已经包含了
			return;
		}

		_listeners.add(listener);

		for (Bean b : _beans) {// 当已经有BEAN的时候直接回调

			listener.beanAdded(this, b._bean);

			if (listener instanceof InheritedListener && b.isManaged() && b._bean instanceof Container) {// 如该监听器具有继承性子,并且目标BEAN被该容器管理和是容器

				if (b._bean instanceof ContainerLifeCycle) {// 则向目标BEAN中也添加该监听器
					((ContainerLifeCycle) b._bean).addBean(listener, false);
				} else {
					((Container) b._bean).addBean(listener);
				}
			}
		}
	}
	@Override
	public void removeEventListener(Container.Listener listener) {// 将监听器删除--嵌套删除并回调

		if (_listeners.remove(listener)) {

			for (Bean b : _beans) {

				listener.beanRemoved(this, b._bean);

				if (listener instanceof InheritedListener && b.isManaged() && b._bean instanceof Container) {
					((Container) b._bean).removeBean(listener);// 将嵌套的监听器删除--只能是被管理的BEAN
				}
			}
		}
	}
	// ****************************************************************************
	// 容器相关接口函数的支撑函数
	public boolean addBean(Object o, Managed managed) {// 添加BEAD的最底层实现

        if (contains(o)) {
			return false;
		}

		Bean new_bean = new Bean(o);// 将该对象封装成一个BEAN--初始状态都是POJO

        if (o instanceof Container.Listener) {
			addEventListener((Container.Listener) o);// 添加监听器
		}

		_beans.add(new_bean);// 也可能是个监听器

		for (Container.Listener l : _listeners) {
			l.beanAdded(this, o);// 执行回调
		}

        try {
            switch (managed) {
                case UNMANAGED:
                    unmanage(new_bean);
                    break;
                case MANAGED:
                    manage(new_bean);
                    if (isStarting() && _doStarted) {
                        LifeCycle l = (LifeCycle)o;
					if (!l.isRunning()) {// 直接启动
                        	start(l);
                        }
                    }
                    break;
                case AUTO:
                	if (o instanceof LifeCycle) {// 该状态的特点:如果该BEAN是生命周期对象
						LifeCycle l = (LifeCycle)o;
						if (isStarting()) {
						if (l.isRunning()) {// (当添加都容器和该BEAN都在运行则不管理该BEAN否则管理并启动)
								unmanage(new_bean);
							} else if (_doStarted) {
								manage(new_bean);
								start(l);
							} else {
							new_bean._managed = Managed.AUTO;// 继续保持该状态
							}
						} else if (isStarted()) {
						   	unmanage(new_bean);
						} else {
							new_bean._managed = Managed.AUTO;
						}
                    } else {
                    	new_bean._managed = Managed.POJO;
                    }
                    break;
                case POJO:
                	new_bean._managed = Managed.POJO;
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
		if (LOG.isDebugEnabled()) {
			LOG.debug("{} added {}", this, new_bean);
		}
        return true;
    }
	public boolean contains(Object bean) {// 检查所有的生命周期BEAN
		for (Bean b : _beans) {
			if (b._bean == bean) {
				return true;
			}
		}
		return false;
	}
	public boolean addBean(Object o, boolean managed) {
		if (o instanceof LifeCycle) {// 包括:ServerConnectorManager、ServletHandler
			return addBean(o, managed ? Managed.MANAGED : Managed.UNMANAGED);
		}
		return addBean(o, managed ? Managed.POJO : Managed.UNMANAGED);// 非声明周期
	}
	private Bean getBean(Object o) {
		for (Bean b : _beans) {
			if (b._bean == o)
				return b;
		}
		return null;
	}
	//****************************************************************************
    public void addManaged(LifeCycle lifecycle) {
        addBean(lifecycle, true);
        try {
			if (isRunning() && !lifecycle.isRunning()) {
				start(lifecycle);
			}
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

	// ****************************************************************************
	public void manage(Object bean) {// 必须是已经在该容器中的BEAN
		for (Bean b : _beans) {
			if (b._bean == bean) {// 先找到该对象所在的BEAN
                manage(b);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown bean " + bean);
    }
	private void manage(Bean bean) {//
        if (bean._managed != Managed.MANAGED) {
            bean._managed = Managed.MANAGED;
			if (bean._bean instanceof Container) {// 目标BEAN是个容器
                for (Container.Listener l : _listeners) {
					if (l instanceof InheritedListener) {// 将具有继承性的监听器放到该目标BEAN中
                        if (bean._bean instanceof ContainerLifeCycle) {
							((ContainerLifeCycle) bean._bean).addBean(l, false);
                        } else {
							((Container) bean._bean).addBean(l);
						}
                    }
                }
            }
			if (bean._bean instanceof AbstractLifeCycle) {// 设置一致的停止超时时间
				((AbstractLifeCycle) bean._bean).setStopTimeout(getStopTimeout());
            }
        }
    }
	public boolean isManaged(Object bean) {
		for (Bean b : _beans) {
			if (b._bean == bean) {
				return b.isManaged();
			}
		}
		return false;
	}
	public void unmanage(Object bean) {
        for (Bean b : _beans) {
			if (b._bean == bean) {// 先找到目标对象所在的BEAN
                unmanage(b);
                return;
            }
        }
		throw new IllegalArgumentException("unknown bean " + bean);
    }
	private void unmanage(Bean bean) {// 目标BEAN不再受该容器的管理--本容器不在管理这个BEAN了
        if (bean._managed != Managed.UNMANAGED) {
			if (bean._managed == Managed.MANAGED && bean._bean instanceof Container) {// 目前在被该容器管理并且本身是个容器
				for (Container.Listener l : _listeners) {
					if (l instanceof InheritedListener) {// 这些监听器是目标BEAN从顶层容器中继承的(以BEAN的方式)
						((Container) bean._bean).removeBean(l);// 移除继承的监听器
					}
                }
            }
			bean._managed = Managed.UNMANAGED;// 设置状态
        }
    }
	// ****************************************************************************

    @Override
    public void setStopTimeout(long stopTimeout) {
        super.setStopTimeout(stopTimeout);
        for (Bean bean : _beans) {
			if (bean.isManaged() && bean._bean instanceof AbstractLifeCycle) {
				((AbstractLifeCycle) bean._bean).setStopTimeout(stopTimeout);
			}
        }
    }

	//******************************************************************
	enum Managed {
		POJO, // POJO:表示一个普通类(非生命周期类)
		MANAGED, // 正在被该容器管理的组件
		UNMANAGED, // UNMANAGED:表示只是存在于该容器但并不受该容器管理(如服务)--生命周期组件已经运行时添加的组件
		AUTO// 生命周期组件运行之前的状态
	};
	private static class Bean {// BEAN对象
		private final Object _bean;// 该BEAN关联的目标对象
		private volatile Managed _managed = Managed.POJO;// 该BEAN的管理状态(初始状态)
        private Bean(Object b) {
            _bean = b;
        }
		public boolean isManaged() {// 表示已经被管理
            return _managed == Managed.MANAGED;
        }
        @Override
        public String toString() {
			return String.format("{%s, %s}", _bean, _managed);
        }
    }
    public void updateBean(Object oldBean, final Object newBean) {
        if (newBean != oldBean) {
            if (oldBean != null) {
				removeBean(oldBean);
			}
            if (newBean != null) {
				addBean(newBean);
			}
        }
    }
	public void updateBean(Object oldBean, final Object newBean, boolean managed) {//
        if (newBean != oldBean) {
            if (oldBean != null) {
				removeBean(oldBean);
			}
            if (newBean != null) {
				addBean(newBean, managed);
			}
        }
    }
    public void updateBeans(Object[] oldBeans, final Object[] newBeans) {//
		if (oldBeans != null) {
            loop: for (Object o : oldBeans) {
                if (newBeans != null) {
                    for (Object n : newBeans) {
						if (o == n) {
							continue loop;
						}
					}
                }
                removeBean(o);
            }
        }
		if (newBeans != null) {
            loop: for (Object n : newBeans) {
                if (oldBeans != null) {
					for (Object o : oldBeans) {
						if (o == n) {
							continue loop;
						}
					}
                }
                addBean(n);
            }
        }
    }
    public void setBeans(Collection<Object> beans) {
        for (Object bean : beans) {
			addBean(bean);
		}
    }
	public void removeBeans() {
		ArrayList<Bean> beans = new ArrayList<>(_beans);
		for (Bean b : beans) {
			remove(b);
		}
	}
	private boolean remove(Bean bean) {// 从该容器中删除该BEAN--最底层实现
        if (_beans.remove(bean)) {
			boolean wasManaged = bean.isManaged();// 该BEAN是被管理的
			unmanage(bean);// 嵌套监听器已经被处理了
			for (Container.Listener l : _listeners) {// 监听器回调--包括本身
				l.beanRemoved(this, bean._bean);
			}
			if (bean._bean instanceof Container.Listener) {// 如果该BEAN是监听器则删除
				removeEventListener((Container.Listener) bean._bean);
			}
            if (wasManaged && bean._bean instanceof LifeCycle) {
                try {
                    stop((LifeCycle)bean._bean);
                } catch(RuntimeException | Error e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return true;
        }
        return false;
    }

	// ***************************************************************************************************************************
	// DESTROY接口相关
	@Override
	public void destroy() {// 以相反的顺序销毁

		List<Bean> reverse = new ArrayList<>(_beans);

		Collections.reverse(reverse);

		for (Bean b : reverse) {

			if (b._bean instanceof Destroyable && (b._managed == Managed.MANAGED || b._managed == Managed.POJO)) {// 两种类型的BEAN
				Destroyable d = (Destroyable) b._bean;
				d.destroy();
			}
		}

		_beans.clear();
	}
	// ***************************************************************************************************************************
	// DUMP接口相关
	// *************************************************************
	// 静态方法
	public static String dump(Dumpable dumpable) {// 将参数DUMP成字符串
		StringBuilder b = new StringBuilder();
		try {
			dumpable.dump(b, "");
		} catch (IOException e) {
			LOG.warn(e);
		}
		return b.toString();
	}
	public static void dumpObject(Appendable out, Object o) throws IOException {
		try {
			if (o instanceof LifeCycle) {
				out.append(String.valueOf(o)).append(" - ").append((AbstractLifeCycle.getState((LifeCycle) o))).append("\n");
			} else {
				out.append(String.valueOf(o)).append("\n");
			}
		} catch (Throwable th) {
			out.append(" => ").append(th.toString()).append('\n');
		}
	}
	// *************************************************************
	public void dumpStdErr() {// 表示将内容DUMP到标准错误上
		try {
			dump(System.err, "");// APPENDABLE
		} catch (IOException e) {
			LOG.warn(e);
		}
	}
	public void dump(Appendable out) throws IOException {
		dump(out, "");// APPENDABLE
	}
	protected void dumpThis(Appendable out) throws IOException {
		out.append(String.valueOf(this)).append(" - ").append(getState()).append("\n");// 生命周期的状态
	}
	protected void dumpBeans(Appendable out, String indent, Collection<?>... collections) throws IOException {
		dumpThis(out);
		int size = _beans.size();// 表示该容器中存放的BEAN
		for (Collection<?> c : collections) {
			size += c.size();
		}
		if (size == 0) {
			return;
		}
		int i = 0;
		for (Bean b : _beans) {
			i++;
			switch (b._managed) {
			case POJO:
				out.append(indent).append(" +- ");
				if (b._bean instanceof Dumpable) {
					((Dumpable) b._bean).dump(out, indent + (i == size ? "    " : " |  "));
				} else {
					dumpObject(out, b._bean);
				}
				break;
			case MANAGED:
				out.append(indent).append(" += ");
				if (b._bean instanceof Dumpable) {
					((Dumpable) b._bean).dump(out, indent + (i == size ? "    " : " |  "));
				} else {
					dumpObject(out, b._bean);
				}
				break;
			case UNMANAGED:
				out.append(indent).append(" +~ ");
				dumpObject(out, b._bean);
				break;
			case AUTO:
				out.append(indent).append(" +? ");
				if (b._bean instanceof Dumpable) {
					((Dumpable) b._bean).dump(out, indent + (i == size ? "    " : " |  "));
				} else {
					dumpObject(out, b._bean);
				}
				break;
			}
		}
		if (i < size) {
			out.append(indent).append(" |\n");
		}
		for (Collection<?> c : collections) {
			for (Object o : c) {
				i++;
				out.append(indent).append(" +> ");
				if (o instanceof Dumpable) {
					((Dumpable) o).dump(out, indent + (i == size ? "    " : " |  "));
				} else {
					dumpObject(out, o);
				}
			}
		}
	}
	public static void dump(Appendable out, String indent, Collection<?>... collections) throws IOException {
		if (collections.length == 0) {
			return;
		}
		int size = 0;
		for (Collection<?> c : collections) {
			size += c.size();
		}
		if (size == 0) {
			return;
		}
		int i = 0;
		for (Collection<?> c : collections) {
			for (Object o : c) {
				i++;
				out.append(indent).append(" +- ");
				if (o instanceof Dumpable) {
					((Dumpable) o).dump(out, indent + (i == size ? "    " : " |  "));
				} else {
					dumpObject(out, o);
				}
			}
		}
	}
	// *************************************************************
	@Override
	public String dump() {
		return dump(this);
	}
	@Override
	public void dump(Appendable out, String indent) throws IOException {
		dumpBeans(out, indent);
	}
	// ***************************************************************************************************************************
}
