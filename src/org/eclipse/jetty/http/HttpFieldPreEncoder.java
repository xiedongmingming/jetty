package org.eclipse.jetty.http;

public interface HttpFieldPreEncoder {
    HttpVersion getHttpVersion();
    byte[] getEncodedField(HttpHeader header, String headerString, String value);
}