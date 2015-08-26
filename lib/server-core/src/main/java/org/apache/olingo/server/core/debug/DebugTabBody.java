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
package org.apache.olingo.server.core.debug;

import java.io.IOException;
import java.io.Writer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.server.api.ODataResponse;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Response body debug information.
 */
public class DebugTabBody implements DebugTab {

  private static enum ResponseContent {
    JSON, XML, TEXT, IMAGE
  };

  private final ODataResponse response;
  private final ResponseContent responseContent;

  public DebugTabBody(final ODataResponse response) {
    this.response = response;
    if (response != null) {
      final String contentTypeString = response.getHeader(HttpHeader.CONTENT_TYPE);
      if (contentTypeString != null) {
        if (contentTypeString.startsWith("application/json")) {
          responseContent = ResponseContent.JSON;
        } else if (contentTypeString.startsWith("image/")) {
          responseContent = ResponseContent.IMAGE;
        } else if (contentTypeString.contains("xml")) {
          responseContent = ResponseContent.XML;
        } else {
          responseContent = ResponseContent.TEXT;
        }
      } else {
        responseContent = ResponseContent.TEXT;
      }
    } else {
      responseContent = ResponseContent.TEXT;
    }
  }

  @Override
  public String getName() {
    return "Body";
  }

//
  @Override
  public void appendJson(final JsonGenerator gen) throws IOException {
    if (response == null || response.getContent() == null) {
      gen.writeNull();
    } else {
      gen.writeString(getContentString());
    }
  }

  private String getContentString() {
    try {
      String contentString;
      switch (responseContent) {
      case IMAGE:
        contentString = Base64.encodeBase64String(IOUtils.toString(response.getContent()).getBytes("UTF-8"));
        break;
      case JSON:
      case XML:
      case TEXT:
      default:
        contentString = IOUtils.toString(response.getContent(), "UTF-8");
        break;
      }
      return contentString;
    } catch (IOException e) {
      return "Could not parse Body for Debug Output";
    }
  }

  @Override
  public void appendHtml(final Writer writer) throws IOException {

    final String body =
        response == null || response.getContent() == null ? "ODataLibrary: No body." : getContentString();
    switch (responseContent) {
    case XML:
      writer.append("<pre class=\"code").append(" xml").append("\">\n");
      writer.append(DebugResponseHelperImpl.escapeHtml(body));
      writer.append("</pre>\n");
      break;
    case JSON:
      writer.append("<pre class=\"code").append(" json").append("\">\n");
      writer.append(DebugResponseHelperImpl.escapeHtml(body));
      writer.append("</pre>\n");
      break;
    case IMAGE:
      writer.append("<img src=\"data:").append(response.getHeader(HttpHeader.CONTENT_TYPE)).append(";base64,")
          .append(body)
          .append("\" />\n");
      break;
    case TEXT:
    default:
      writer.append("<pre class=\"code").append("\">\n");
      writer.append(DebugResponseHelperImpl.escapeHtml(body));
      writer.append("</pre>\n");
      break;
    }
  }
}
