/*******************************************************************************
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
 ******************************************************************************/
package org.apache.olingo.commons.core.edm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmAction;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmEntityContainer;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmFunction;
import org.apache.olingo.commons.api.edm.EdmServiceMetadata;
import org.apache.olingo.commons.api.edm.EdmTypeDefinition;
import org.apache.olingo.commons.api.edm.helper.FullQualifiedName;

public abstract class EdmImpl implements Edm {

  private final Map<FullQualifiedName, EdmEntityContainer> entityContainers =
      new HashMap<FullQualifiedName, EdmEntityContainer>();
  private final Map<FullQualifiedName, EdmEnumType> enumTypes = new HashMap<FullQualifiedName, EdmEnumType>();
  private final Map<FullQualifiedName, EdmTypeDefinition> typeDefinitions =
      new HashMap<FullQualifiedName, EdmTypeDefinition>();
  private final Map<FullQualifiedName, EdmEntityType> entityTypes = new HashMap<FullQualifiedName, EdmEntityType>();
  private final Map<FullQualifiedName, EdmComplexType> complexTypes = new HashMap<FullQualifiedName, EdmComplexType>();
  private final Map<ActionMapKey, EdmAction> actions = new HashMap<ActionMapKey, EdmAction>();
  private final Map<FunctionMapKey, EdmFunction> functions = new HashMap<FunctionMapKey, EdmFunction>();
  private EdmServiceMetadata serviceMetadata;
  private Map<String, String> aliasToNamespaceInfo;

  @Override
  public EdmEntityContainer getEntityContainer(final FullQualifiedName namespaceOrAliasFQN) {
    FullQualifiedName fqn = resolvePossibleAlias(namespaceOrAliasFQN);
    EdmEntityContainer container = entityContainers.get(fqn);
    if (container == null) {
      container = createEntityContainer(fqn);
      if (container != null) {
        entityContainers.put(fqn, container);
        if (fqn == null) {
          entityContainers.put(new FullQualifiedName(container.getNamespace(), container.getName()), container);
        }
      }
    }
    return container;
  }

  @Override
  public EdmEnumType getEnumType(final FullQualifiedName namespaceOrAliasFQN) {
    FullQualifiedName fqn = resolvePossibleAlias(namespaceOrAliasFQN);
    EdmEnumType enumType = enumTypes.get(fqn);
    if (enumType == null) {
      enumType = createEnumType(fqn);
      if (enumType != null) {
        enumTypes.put(fqn, enumType);
      }
    }
    return enumType;
  }

  @Override
  public EdmTypeDefinition getTypeDefinition(final FullQualifiedName namespaceOrAliasFQN) {
    FullQualifiedName fqn = resolvePossibleAlias(namespaceOrAliasFQN);
    EdmTypeDefinition typeDefinition = typeDefinitions.get(fqn);
    if (typeDefinition == null) {
      typeDefinition = createTypeDefinition(fqn);
      if (typeDefinition != null) {
        typeDefinitions.put(fqn, typeDefinition);
      }
    }
    return typeDefinition;
  }

  @Override
  public EdmEntityType getEntityType(final FullQualifiedName namespaceOrAliasFQN) {
    FullQualifiedName fqn = resolvePossibleAlias(namespaceOrAliasFQN);
    EdmEntityType entityType = entityTypes.get(fqn);
    if (entityType == null) {
      entityType = createEntityType(fqn);
      if (entityType != null) {
        entityTypes.put(fqn, entityType);
      }
    }
    return entityType;
  }

  @Override
  public EdmComplexType getComplexType(final FullQualifiedName namespaceOrAliasFQN) {
    FullQualifiedName fqn = resolvePossibleAlias(namespaceOrAliasFQN);
    EdmComplexType complexType = complexTypes.get(fqn);
    if (complexType == null) {
      complexType = createComplexType(fqn);
      if (complexType != null) {
        complexTypes.put(fqn, complexType);
      }
    }
    return complexType;
  }

  @Override
  public EdmAction getAction(final FullQualifiedName actionName, final FullQualifiedName bindingParameterTypeName,
      final Boolean isBindingParameterCollection) {
    FullQualifiedName actionFqn = resolvePossibleAlias(actionName);
    FullQualifiedName bindingParameterTypeFqn = resolvePossibleAlias(bindingParameterTypeName);
    ActionMapKey key = new ActionMapKey(actionFqn, bindingParameterTypeFqn, isBindingParameterCollection);
    EdmAction action = actions.get(key);
    if (action == null) {
      action = createAction(actionFqn, bindingParameterTypeFqn, isBindingParameterCollection);
      if (action != null) {
        actions.put(key, action);
      }
    }
    return action;
  }

  @Override
  public EdmFunction getFunction(final FullQualifiedName functionName,
      final FullQualifiedName bindingParameterTypeName,
      final Boolean isBindingParameterCollection, final List<String> parameterNames) {
    FullQualifiedName functionFqn = resolvePossibleAlias(functionName);
    FullQualifiedName bindingParameterTypeFqn = resolvePossibleAlias(bindingParameterTypeName);
    FunctionMapKey key =
        new FunctionMapKey(functionFqn, bindingParameterTypeFqn, isBindingParameterCollection, parameterNames);
    EdmFunction function = functions.get(key);
    if (function == null) {
      function = createFunction(functionFqn, bindingParameterTypeFqn, isBindingParameterCollection,
          parameterNames);
      if (function != null) {
        functions.put(key, function);
      }
    }
    return function;
  }

  @Override
  public EdmServiceMetadata getServiceMetadata() {
    if (serviceMetadata == null) {
      serviceMetadata = createServiceMetadata();
    }
    return serviceMetadata;
  }

  private FullQualifiedName resolvePossibleAlias(final FullQualifiedName namespaceOrAliasFQN) {
    if (aliasToNamespaceInfo == null) {
      aliasToNamespaceInfo = createAliasToNamespaceInfo();
    }
    FullQualifiedName finalFQN = null;
    if (namespaceOrAliasFQN != null) {
      String namespace = aliasToNamespaceInfo.get(namespaceOrAliasFQN.getNamespace());
      // If not contained in info it must be a namespace
      if (namespace == null) {
        finalFQN = namespaceOrAliasFQN;
      } else {
        finalFQN = new FullQualifiedName(namespace, namespaceOrAliasFQN.getName());
      }
    }
    return finalFQN;
  }

  protected abstract Map<String, String> createAliasToNamespaceInfo();

  protected abstract EdmEntityContainer createEntityContainer(FullQualifiedName containerName);

  protected abstract EdmEnumType createEnumType(FullQualifiedName enumName);

  protected abstract EdmTypeDefinition createTypeDefinition(FullQualifiedName typeDefinitionName);

  protected abstract EdmEntityType createEntityType(FullQualifiedName entityTypeName);

  protected abstract EdmComplexType createComplexType(FullQualifiedName complexTypeName);

  protected abstract EdmAction createAction(FullQualifiedName actionName, FullQualifiedName bindingParameterTypeName,
      Boolean isBindingParameterCollection);

  protected abstract EdmFunction createFunction(FullQualifiedName functionName,
      FullQualifiedName bindingParameterTypeName, Boolean isBindingParameterCollection,
      List<String> parameterNames);

  protected abstract EdmServiceMetadata createServiceMetadata();

}
