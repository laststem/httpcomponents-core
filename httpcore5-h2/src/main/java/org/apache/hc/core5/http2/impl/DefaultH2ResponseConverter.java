/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.http2.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.DefaultHttpResponseFactory;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.H2MessageConverter;
import org.apache.hc.core5.http2.H2PseudoResponseHeaders;

/**
 * HTTP/2 response converter.
 *
 * @since 5.0
 */
public class DefaultH2ResponseConverter implements H2MessageConverter<HttpResponse> {

    public final static DefaultH2ResponseConverter INSTANCE = new DefaultH2ResponseConverter();

    private HttpResponseFactory responseFactory;

    public DefaultH2ResponseConverter() {
        this(null);
    }

    public DefaultH2ResponseConverter(final HttpResponseFactory responseFactory) {
        this.responseFactory = responseFactory != null ? responseFactory : DefaultHttpResponseFactory.INSTANCE;
    }

    @Override
    public HttpResponse convert(final List<Header> headers) throws HttpException {
        String statusText = null;
        final List<Header> messageHeaders = new ArrayList<>();

        for (int i = 0; i < headers.size(); i++) {
            final Header header = headers.get(i);
            final String name = header.getName();
            final String value = header.getValue();

            for (int n = 0; n < name.length(); n++) {
                final char ch = name.charAt(n);
                if (Character.isAlphabetic(ch) && !Character.isLowerCase(ch)) {
                    throw new ProtocolException("Header name '" + name + "' is invalid (header name contains uppercase characters)");
                }
            }

            if (name.startsWith(":")) {
                if (!messageHeaders.isEmpty()) {
                    throw new ProtocolException("Invalid sequence of headers (pseudo-headers must precede message headers)");
                }
                if (name.equals(H2PseudoResponseHeaders.STATUS)) {
                    if (statusText != null) {
                        throw new ProtocolException("Multiple '" + name + "' response headers are illegal");
                    }
                    statusText = value;
                } else {
                    throw new ProtocolException("Unsupported response header '" + name + "'");
                }
            } else {
                if (name.equalsIgnoreCase(HttpHeaders.CONNECTION)) {
                    throw new ProtocolException("Header '" + header.getName() + ": " + header.getValue() + "' is illegal for HTTP/2 messages");
                }
                messageHeaders.add(header);
            }

        }

        if (statusText == null) {
            throw new ProtocolException("Mandatory response header ':status' not found");
        }
        final int statusCode;
        try {
            statusCode = Integer.parseInt(statusText);
        } catch (NumberFormatException ex) {
            throw new ProtocolException("Invalid response status: " + statusText);
        }
        final HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_2, statusCode, null);
        for (int i = 0; i < messageHeaders.size(); i++) {
            response.addHeader(messageHeaders.get(i));
        }
        return response;
    }

    @Override
    public List<Header> convert(final HttpResponse message) throws HttpException {
        final int code = message.getCode();
        if (code < 100 || code >= 600) {
            throw new ProtocolException("Response status " + code + " is invalid");
        }
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(H2PseudoResponseHeaders.STATUS, Integer.toString(code), false));

        for (final Iterator<Header> it = message.headerIterator(); it.hasNext(); ) {
            final Header header = it.next();
            final String name = header.getName();
            if (name.startsWith(":")) {
                throw new ProtocolException("Header name '" + name + "' is invalid");
            }
            if (name.equalsIgnoreCase(HttpHeaders.CONNECTION)) {
                throw new ProtocolException("Header '" + header.getName() + ": " + header.getValue() + "' is illegal for HTTP/2 messages");
            }
            headers.add(header);
        }
        return headers;
    }

}
