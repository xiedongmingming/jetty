package extral.org.eclipse.jetty.rewrite.handler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;

public abstract class Rule {// 用于创建重写规则的抽象类

	// *******************************************************************************************************
	public interface ApplyURI {// 接口
        void applyURI(Request request, String oldURI, String newURI) throws IOException;   
    }
	// *******************************************************************************************************
    protected boolean _terminating;
    protected boolean _handling;

	// *******************************************************************************************************
	public abstract String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException;
	// *******************************************************************************************************
	public void setTerminating(boolean terminating) {
        _terminating = terminating;
    }
	public boolean isTerminating() {
        return _terminating;
    }
	public boolean isHandling() {
        return _handling;
    }
	public void setHandling(boolean handling) {
		_handling = handling;
    }
	// *******************************************************************************************************
    @Override
	public String toString() {
		return this.getClass().getName() + (_handling ? "[H" : "[h") + (_terminating ? "T]" : "t]");
    }
	// *******************************************************************************************************
}
