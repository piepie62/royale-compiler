/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.flex.compiler.tree.mxml;

/**
 * This AST node represents an MXML {@code <RemoteObject>} tag.
 * <p>
 * The {@code <RemoteObject>} tag is an ordinary instance tag from the compiler's
 * point of view, except for the fact that a {@code <RemoteObject>} tag can have
 * special child {@code <method>} tags mixed in with its other child tags
 * for properties and events.
 * These are not property tags, because {@code RemoteObject} has no <code>method</code> property.
 * Instead, each {@code <method>} tag creates an instance of <code>mx.rpc.remoting.mxml.Operation</code>
 * and adds it as a dynamic property of the <code>operations</code> object
 * of the <code>RemoteObject</code> instance; the name of the property in this object
 * is the name specified by the <code>name</code> attribute on the {@code <method>} tag.
 * <p>
 * Each {@code <method>} tag is represented by a child {@code IMXMLRemoteObjectMethodNode}.
 */
public interface IMXMLRemoteObjectNode extends IMXMLInstanceNode
{
}
