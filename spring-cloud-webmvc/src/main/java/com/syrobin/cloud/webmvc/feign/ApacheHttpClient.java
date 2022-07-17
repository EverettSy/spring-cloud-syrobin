package com.syrobin.cloud.webmvc.feign;

import feign.Client;
import feign.Request;
import feign.Response;
import feign.Util;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author syrobin
 * @version v1.0
 * @description: 和 feign.httpclient.ApacheHttpClient 一模一样，但是不是 final 的
 * @date 2022-07-08 17:17
 * @see https://github.com/OpenFeign/feign/blob/master/httpclient/src/main/java/feign/httpclient/ApacheHttpClient.java
 * @see feign.httpclient.ApacheHttpClient
 */
public class ApacheHttpClient implements Client {

    private static final String ACCEPT_HEADER_NAME = "Accept";

    private final HttpClient client;

    public ApacheHttpClient() {
        this(HttpClientBuilder.create().build());
    }

    public ApacheHttpClient(HttpClient client) {
        this.client = client;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        HttpUriRequest httpUriRequest;
        try {
            httpUriRequest = toHttpUriRequest(request, options);
        } catch (Exception e) {
            throw new IOException("URL '" + request.url() + "' couldn't be parsed into a URI", e);
        }
        HttpResponse httpResponse = client.execute(httpUriRequest);
        return toFeignResponse(httpResponse, request);
    }

    private HttpUriRequest toHttpUriRequest(Request request, Request.Options options) throws URISyntaxException {
        RequestBuilder requestBuilder = RequestBuilder.create(request.httpMethod().name());

        //request timeouts
        RequestConfig requestConfig =
                (client instanceof Configurable ? RequestConfig.copy(((Configurable) client).getConfig())
                        : RequestConfig.custom())
                        .setConnectTimeout(options.connectTimeoutMillis())
                        .setSocketTimeout(options.readTimeoutMillis())
                        .build();
        requestBuilder.setConfig(requestConfig);

        URI uri = new URIBuilder(request.url()).build();

        requestBuilder.setUri(uri.getScheme() + "://" + uri.getAuthority() + uri.getRawPath());

        //request query params
        List<NameValuePair> queryParams = URLEncodedUtils.parse(uri, requestBuilder.getCharset());
        for (NameValuePair queryParam : queryParams) {
            requestBuilder.addParameter(queryParam);
        }

        //request headers
        boolean hasAcceptHeader = false;
        for (Map.Entry<String, Collection<String>> headerEntry : request.headers().entrySet()) {
            String headerName = headerEntry.getKey();
            if (headerName.equalsIgnoreCase(ACCEPT_HEADER_NAME)) {
                hasAcceptHeader = true;
            }
            if (headerName.equalsIgnoreCase(Util.CONTENT_LENGTH)) {
                // The 'Content-Length' header is always set by the Apache client and it
                // doesn't like us to set it as well.
                continue;
            }
            for (String headerValue : headerEntry.getValue()) {
                requestBuilder.addHeader(headerName, headerValue);
            }
        }
        // some servers choke on the default accept string, so we'll set it to anything
        if (!hasAcceptHeader) {
            requestBuilder.addHeader(ACCEPT_HEADER_NAME, "*/*");
        }

        //request body
        if (request.body() != null) {
            HttpEntity entity = null;
            if (request.charset() != null) {
                ContentType contentType = getContentType(request);
                String content = new String(request.body(), request.charset());
                entity = new StringEntity(content, contentType);
            } else {
                entity = new ByteArrayEntity(request.body());
            }
            requestBuilder.setEntity(entity);

        } else {
            requestBuilder.setEntity(new ByteArrayEntity(new byte[0]));
        }

        return requestBuilder.build();
    }

    private ContentType getContentType(Request request) {
        ContentType contentType = null;
        for (Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
            if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                Collection<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    contentType = ContentType.parse(entry.getValue().iterator().next());
                    if (contentType.getCharset() == null) {
                        contentType = contentType.withCharset(request.charset());
                    }
                }
                break;
            }
        }
        return contentType;
    }

    private Response toFeignResponse(HttpResponse httpResponse, Request request) {
        StatusLine statusLine = httpResponse.getStatusLine();
        int statusCode = statusLine.getStatusCode();

        String reason = statusLine.getReasonPhrase();

        Map<String, Collection<String>> headers = new HashMap<>();
        for (Header header : httpResponse.getAllHeaders()) {
            String name = header.getName();
            String value = header.getValue();

            Collection<String> headerValues = headers.computeIfAbsent(name, k -> new ArrayList<>());
            headerValues.add(value);
        }
        return Response.builder()
                .status(statusCode)
                .reason(reason)
                .headers(headers)
                .request(request)
                .body(toFeignBody(httpResponse))
                .build();
    }

    private Response.Body toFeignBody(HttpResponse httpResponse) {
        final HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            return null;
        }
        return new Response.Body() {
            @Override
            public Integer length() {
                return entity.getContentLength() >= 0 && entity.getContentLength() <= Integer.MAX_VALUE
                        ? (int) entity.getContentLength()
                        : null;
            }

            @Override
            public boolean isRepeatable() {
                return entity.isRepeatable();
            }

            @Override
            public InputStream asInputStream() throws IOException {
                return entity.getContent();
            }

            @SuppressWarnings("deprecation")
            @Override
            public Reader asReader() throws IOException {
                return new InputStreamReader(asInputStream(), UTF_8);
            }

            @Override
            public Reader asReader(Charset charset) throws IOException {
                Util.checkNotNull(charset, "charset should not be null");
                return new InputStreamReader(asInputStream(), charset);
            }

            @Override
            public void close() throws IOException {
                EntityUtils.consume(entity);
            }
        };
    }


}
