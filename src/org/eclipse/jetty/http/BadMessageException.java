package org.eclipse.jetty.http;

public class BadMessageException extends RuntimeException {

	private static final long serialVersionUID = 4311633086711663655L;

	final int _code;// 400

    final String _reason;

	// ***************************************************************************************
	public BadMessageException() {
		this(400, null);
    }
	public BadMessageException(int code) {
		this(code, null);
    }
	public BadMessageException(String reason) {
		this(400, reason);
    }
	public BadMessageException(int code, String reason) {
		super(code + ": " + reason);// 构成原因
		_code = code;
		_reason = reason;
    }
	public BadMessageException(int code, String reason, Throwable cause) {
		super(code + ": " + reason, cause);
		_code = code;
		_reason = reason;
    }
	// ***************************************************************************************
	public int getCode() {
        return _code;
    }
	public String getReason() {
        return _reason;
    }
	// ***************************************************************************************
}
