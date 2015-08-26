/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.olingo.server.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ODataServerError;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.debug.DebugSupport;
import org.apache.olingo.server.api.etag.CustomETagSupport;
import org.apache.olingo.server.api.processor.Processor;
import org.apache.olingo.server.api.serializer.CustomContentTypeSupport;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.core.debug.ServerCoreDebugger;

public class ODataHttpHandlerImpl implements ODataHttpHandler {

  public static final int COPY_BUFFER_SIZE = 8192;

  private final ODataHandler handler;
  private final ServerCoreDebugger debugger;

  private int split = 0;

  public ODataHttpHandlerImpl(final OData odata, final ServiceMetadata serviceMetadata) {
    debugger = new ServerCoreDebugger(odata);
    handler = new ODataHandler(odata, serviceMetadata, debugger);
  }

  @Override
  public void process(final HttpServletRequest request, final HttpServletResponse response) {
    ODataRequest odRequest = new ODataRequest();
    Exception exception = null;
    ODataResponse odResponse;
    debugger.resolveDebugMode(request);

    int processMethodHandel = debugger.startRuntimeMeasurement("ODataHttpHandlerImpl", "process");
    try {
      int requestHandel = debugger.startRuntimeMeasurement("ODataHttpHandlerImpl", "fillODataRequest");
      fillODataRequest(odRequest, request, split);
      debugger.stopRuntimeMeasurement(requestHandel);

      int responseHandel = debugger.startRuntimeMeasurement("ODataHandler", "process");
      odResponse = handler.process(odRequest);
      debugger.stopRuntimeMeasurement(responseHandel);
      // ALL future methods after process must not throw exceptions!
    } catch (Exception e) {
      exception = e;
      odResponse = handleException(odRequest, e);
    }
    debugger.stopRuntimeMeasurement(processMethodHandel);

    if (debugger.isDebugMode()) {
      Map<String, String> serverEnvironmentVariables = createEnvironmentVariablesMap(request);
      if (exception == null) {
        // This is to ensure that we have access to the thrown OData Exception
        exception = handler.getLastThrownException();
      }
      odResponse =
          debugger.createDebugResponse(request, exception, odRequest, odResponse, handler.getUriInfo(),
              serverEnvironmentVariables);
    }

    convertToHttp(response, odResponse);
  }

  private Map<String, String> createEnvironmentVariablesMap(HttpServletRequest request) {
    LinkedHashMap<String, String> environment = new LinkedHashMap<String, String>();
    environment.put("authType", request.getAuthType());
    environment.put("localAddr", request.getLocalAddr());
    environment.put("localName", request.getLocalName());
    environment.put("localPort", getIntAsString(request.getLocalPort()));
    environment.put("pathInfo", request.getPathInfo());
    environment.put("pathTranslated", request.getPathTranslated());
    environment.put("remoteAddr", request.getRemoteAddr());
    environment.put("remoteHost", request.getRemoteHost());
    environment.put("remotePort", getIntAsString(request.getRemotePort()));
    environment.put("remoteUser", request.getRemoteUser());
    environment.put("scheme", request.getScheme());
    environment.put("serverName", request.getServerName());
    environment.put("serverPort", getIntAsString(request.getServerPort()));
    environment.put("servletPath", request.getServletPath());
    return environment;
  }

  private String getIntAsString(final int number) {
    return number == 0 ? "unknown" : Integer.toString(number);
  }

  @Override
  public void setSplit(final int split) {
    this.split = split;
  }

  private ODataResponse handleException(final ODataRequest odRequest, final Exception e) {
    ODataResponse resp = new ODataResponse();
    ODataServerError serverError;
    if (e instanceof ODataHandlerException) {
      serverError = ODataExceptionHelper.createServerErrorObject((ODataHandlerException) e, null);
    } else if (e instanceof ODataLibraryException) {
      serverError = ODataExceptionHelper.createServerErrorObject((ODataLibraryException) e, null);
    } else {
      serverError = ODataExceptionHelper.createServerErrorObject(e);
    }
    handler.handleException(odRequest, resp, serverError, e);
    return resp;
  }

  static void convertToHttp(final HttpServletResponse response, final ODataResponse odResponse) {
    response.setStatus(odResponse.getStatusCode());

    for (Entry<String, List<String>> entry : odResponse.getAllHeaders().entrySet()) {
      for (String headerValue : entry.getValue()) {
        response.addHeader(entry.getKey(), headerValue);
      }
    }

    copyContent(odResponse, response);
  }

  static void copyContent(final ODataResponse odataResponse, final HttpServletResponse servletResponse) {
    ReadableByteChannel input = null;
    WritableByteChannel output = null;
    try {
      ByteBuffer inBuffer = ByteBuffer.allocate(COPY_BUFFER_SIZE);
      output = Channels.newChannel(servletResponse.getOutputStream());
      input = Channels.newChannel(odataResponse.getContent());
      while (input.read(inBuffer) > 0) {
        inBuffer.flip();
        output.write(inBuffer);
        inBuffer.rewind();
      }
    } catch (IOException e) {
      throw new ODataRuntimeException("Error on reading request content", e);
    } finally {
      closeStream(input);
      closeStream(output);
    }
  }

  private static void closeStream(final Channel closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  private ODataRequest fillODataRequest(final ODataRequest odRequest, final HttpServletRequest httpRequest,
      final int split)
      throws ODataLibraryException {
    try {
      odRequest.setBody(httpRequest.getInputStream());
      odRequest.setProtocol(httpRequest.getProtocol());
      odRequest.setMethod(extractMethod(httpRequest));
      copyHeaders(odRequest, httpRequest);
      fillUriInformation(odRequest, httpRequest, split);

      return odRequest;
    } catch (final IOException e) {
      throw new SerializerException("An I/O exception occurred.", e,
          SerializerException.MessageKeys.IO_EXCEPTION);
    }
  }

  static HttpMethod extractMethod(final HttpServletRequest httpRequest)
      throws ODataLibraryException {
    try {
      HttpMethod httpRequestMethod = HttpMethod.valueOf(httpRequest.getMethod());

      if (httpRequestMethod == HttpMethod.POST) {
        String xHttpMethod = httpRequest.getHeader(HttpHeader.X_HTTP_METHOD);
        String xHttpMethodOverride = httpRequest.getHeader(HttpHeader.X_HTTP_METHOD_OVERRIDE);

        if (xHttpMethod == null && xHttpMethodOverride == null) {
          return httpRequestMethod;
        } else if (xHttpMethod == null) {
          return HttpMethod.valueOf(xHttpMethodOverride);
        } else if (xHttpMethodOverride == null) {
          return HttpMethod.valueOf(xHttpMethod);
        } else {
          if (!xHttpMethod.equalsIgnoreCase(xHttpMethodOverride)) {
            throw new ODataHandlerException("Ambiguous X-HTTP-Methods",
                ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD, xHttpMethod, xHttpMethodOverride);
          }
          return HttpMethod.valueOf(xHttpMethod);
        }
      } else {
        return httpRequestMethod;
      }
    } catch (IllegalArgumentException e) {
      throw new ODataHandlerException("Invalid HTTP method" + httpRequest.getMethod(), e,
          ODataHandlerException.MessageKeys.INVALID_HTTP_METHOD, httpRequest.getMethod());
    }
  }

  static void fillUriInformation(final ODataRequest odRequest, final HttpServletRequest httpRequest, final int split) {
    String rawRequestUri = httpRequest.getRequestURL().toString();

    String rawODataPath;
    if (!"".equals(httpRequest.getServletPath())) {
      int beginIndex = rawRequestUri.indexOf(httpRequest.getServletPath()) + httpRequest.getServletPath().length();
      rawODataPath = rawRequestUri.substring(beginIndex);
    } else if (!"".equals(httpRequest.getContextPath())) {
      int beginIndex = rawRequestUri.indexOf(httpRequest.getContextPath()) + httpRequest.getContextPath().length();
      rawODataPath = rawRequestUri.substring(beginIndex);
    } else {
      rawODataPath = httpRequest.getRequestURI();
    }

    String rawServiceResolutionUri = null;
    if (split > 0) {
      rawServiceResolutionUri = rawODataPath;
      for (int i = 0; i < split; i++) {
        int index = rawODataPath.indexOf('/', 1);
        if (-1 == index) {
          rawODataPath = "";
          break;
        } else {
          rawODataPath = rawODataPath.substring(index);
        }
      }
      int end = rawServiceResolutionUri.length() - rawODataPath.length();
      rawServiceResolutionUri = rawServiceResolutionUri.substring(0, end);
    }

    String rawBaseUri = rawRequestUri.substring(0, rawRequestUri.length() - rawODataPath.length());

    odRequest.setRawQueryPath(httpRequest.getQueryString());
    odRequest.setRawRequestUri(rawRequestUri
        + (httpRequest.getQueryString() == null ? "" : "?" + httpRequest.getQueryString()));
    odRequest.setRawODataPath(rawODataPath);
    odRequest.setRawBaseUri(rawBaseUri);
    odRequest.setRawServiceResolutionUri(rawServiceResolutionUri);
  }

  static void copyHeaders(final ODataRequest odRequest, final HttpServletRequest req) {
    for (Enumeration<?> headerNames = req.getHeaderNames(); headerNames.hasMoreElements();) {
      String headerName = (String) headerNames.nextElement();
      List<String> headerValues = new ArrayList<String>();
      for (Enumeration<?> headers = req.getHeaders(headerName); headers.hasMoreElements();) {
        String value = (String) headers.nextElement();
        headerValues.add(value);
      }
      odRequest.addHeader(headerName, headerValues);
    }
  }

  @Override
  public void register(final Processor processor) {
    handler.register(processor);
  }

  @Override
  public void register(final CustomContentTypeSupport customContentTypeSupport) {
    handler.register(customContentTypeSupport);
  }

  @Override
  public void register(final CustomETagSupport customConcurrencyControlSupport) {
    handler.register(customConcurrencyControlSupport);
  }

  @Override
  public void register(final DebugSupport debugSupport) {
    debugger.setDebugSupportProcessor(debugSupport);
  }
}
