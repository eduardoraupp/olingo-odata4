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
package org.apache.olingo.server.core.edm.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.apache.olingo.commons.api.edm.EdmAnnotatable;
import org.apache.olingo.commons.api.edm.EdmNamed;
import org.apache.olingo.commons.api.edm.provider.CsdlAnnotatable;
import org.apache.olingo.commons.api.edm.provider.CsdlAnnotation;
import org.apache.olingo.commons.core.edm.AbstractEdmNamed;
import org.junit.jupiter.api.Test;

public class AbstractEdmNamedTest {

  @Test
  public void getNameTest() {
    EdmNamed obj = new EdmNamedImplTester("Name");
    assertEquals("Name", obj.getName());
    EdmAnnotatable an = (EdmAnnotatable) obj;
    assertNotNull(an.getAnnotations().get(0));
  }

  private class EdmNamedImplTester extends AbstractEdmNamed {

    public EdmNamedImplTester(final String name) {
      super(null, name, new AnnoTester());
    }
  }

  private class AnnoTester implements CsdlAnnotatable {
    @Override
    public List<CsdlAnnotation> getAnnotations() {
      CsdlAnnotation annotation = new CsdlAnnotation();
      annotation.setTerm("NS.SimpleTerm");
      return Arrays.asList(annotation);
    }
  }
}
