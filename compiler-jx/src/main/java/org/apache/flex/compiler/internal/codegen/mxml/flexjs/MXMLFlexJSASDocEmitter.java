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

package org.apache.flex.compiler.internal.codegen.mxml.flexjs;


import java.io.File;
import java.io.FilterWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.flex.abc.semantics.MethodInfo;
import org.apache.flex.abc.semantics.Name;
import org.apache.flex.abc.semantics.Namespace;
import org.apache.flex.compiler.codegen.IASGlobalFunctionConstants;
import org.apache.flex.compiler.codegen.as.IASEmitter;
import org.apache.flex.compiler.codegen.mxml.flexjs.IMXMLFlexJSEmitter;
import org.apache.flex.compiler.common.ASModifier;
import org.apache.flex.compiler.constants.IASKeywordConstants;
import org.apache.flex.compiler.constants.IASLanguageConstants;
import org.apache.flex.compiler.definitions.IClassDefinition;
import org.apache.flex.compiler.definitions.IDefinition;
import org.apache.flex.compiler.definitions.IFunctionDefinition;
import org.apache.flex.compiler.internal.codegen.as.ASEmitterTokens;
import org.apache.flex.compiler.internal.codegen.databinding.BindingDatabase;
import org.apache.flex.compiler.internal.codegen.databinding.BindingInfo;
import org.apache.flex.compiler.internal.codegen.databinding.FunctionWatcherInfo;
import org.apache.flex.compiler.internal.codegen.databinding.PropertyWatcherInfo;
import org.apache.flex.compiler.internal.codegen.databinding.StaticPropertyWatcherInfo;
import org.apache.flex.compiler.internal.codegen.databinding.WatcherInfoBase;
import org.apache.flex.compiler.internal.codegen.databinding.WatcherInfoBase.WatcherType;
import org.apache.flex.compiler.internal.codegen.databinding.XMLWatcherInfo;
import org.apache.flex.compiler.internal.codegen.js.JSSessionModel.PropertyNodes;
import org.apache.flex.compiler.internal.codegen.js.JSSessionModel.BindableVarInfo;
import org.apache.flex.compiler.internal.codegen.js.flexjs.JSFlexJSASDocEmitter;
import org.apache.flex.compiler.internal.codegen.js.flexjs.JSFlexJSEmitterTokens;
import org.apache.flex.compiler.internal.codegen.js.goog.JSGoogEmitterTokens;
import org.apache.flex.compiler.internal.codegen.js.jx.BindableEmitter;
import org.apache.flex.compiler.internal.codegen.js.jx.PackageFooterEmitter;
import org.apache.flex.compiler.internal.codegen.js.utils.EmitterUtils;
import org.apache.flex.compiler.internal.codegen.mxml.MXMLEmitter;
import org.apache.flex.compiler.internal.projects.FlexJSProject;
import org.apache.flex.compiler.internal.projects.FlexProject;
import org.apache.flex.compiler.internal.scopes.ASProjectScope;
import org.apache.flex.compiler.internal.targets.ITargetAttributes;
import org.apache.flex.compiler.internal.tree.as.FunctionCallNode;
import org.apache.flex.compiler.internal.tree.as.IdentifierNode;
import org.apache.flex.compiler.internal.tree.as.MemberAccessExpressionNode;
import org.apache.flex.compiler.internal.tree.mxml.MXMLDocumentNode;
import org.apache.flex.compiler.internal.tree.mxml.MXMLFileNode;
import org.apache.flex.compiler.mxml.IMXMLLanguageConstants;
import org.apache.flex.compiler.projects.ICompilerProject;
import org.apache.flex.compiler.tree.ASTNodeID;
import org.apache.flex.compiler.tree.as.*;
import org.apache.flex.compiler.tree.metadata.IMetaTagNode;
import org.apache.flex.compiler.tree.metadata.IMetaTagsNode;
import org.apache.flex.compiler.tree.mxml.*;
import org.apache.flex.compiler.units.ICompilationUnit;
import org.apache.flex.compiler.utils.NativeUtils;
import org.apache.flex.compiler.visitor.mxml.IMXMLBlockWalker;
import org.apache.flex.swc.ISWC;

import com.google.common.base.Joiner;

/**
 * @author Erik de Bruin
 */
public class MXMLFlexJSASDocEmitter extends MXMLEmitter implements
        IMXMLFlexJSEmitter
{

	// the instances in a container
    private ArrayList<MXMLDescriptorSpecifier> currentInstances;
    private ArrayList<MXMLDescriptorSpecifier> currentPropertySpecifiers;
    private ArrayList<MXMLDescriptorSpecifier> descriptorTree;
    private MXMLDescriptorSpecifier propertiesTree;
    private MXMLDescriptorSpecifier currentStateOverrides;
    private ArrayList<MXMLEventSpecifier> events;
    // all instances in the current document or subdocument
    private ArrayList<MXMLDescriptorSpecifier> instances;
    // all instances in the document AND its subdocuments
    private ArrayList<MXMLDescriptorSpecifier> allInstances = new ArrayList<MXMLDescriptorSpecifier>();
    private ArrayList<MXMLScriptSpecifier> scripts;
    //private ArrayList<MXMLStyleSpecifier> styles;
    private IClassDefinition classDefinition;
    private IClassDefinition documentDefinition;
    private ArrayList<String> usedNames = new ArrayList<String>();
    private ArrayList<IMXMLMetadataNode> metadataNodes = new ArrayList<IMXMLMetadataNode>();
    
    private int eventCounter;
    private int idCounter;
    private int bindingCounter;

    private boolean inMXMLContent;
    private boolean inStatesOverride;
    private boolean makingSimpleArray;
    
    private StringBuilder subDocuments = new StringBuilder();
    private ArrayList<String> subDocumentNames = new ArrayList<String>();
    private String interfaceList;
    
    /**
     * This keeps track of the entries in our temporary array of 
     * DeferredInstanceFromFunction objects that we CG to help with
     * State override CG.
     * 
     * Keys are Instance nodes,
     * values are the array index where the deferred instance is:
     * 
     *  deferred instance = local3[ nodeToIndexMap.get(an instance) ]
     */
    protected Map<IMXMLNode, Integer> nodeToIndexMap;
    
    public MXMLFlexJSASDocEmitter(FilterWriter out)
    {
        super(out);
    }

    @Override
    public String postProcess(String output)
    {
    	return output;
    }
    
    @Override
    protected String getIndent(int numIndent)
    {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numIndent; i++)
            sb.append(JSFlexJSEmitterTokens.INDENT.getToken());
        return sb.toString();
    }

    //--------------------------------------------------------------------------

    @Override
    public void emitDeclarations(IMXMLDeclarationsNode node)
    {
    	super.emitDeclarations(node);
    }
    
    @Override
    public void emitDocument(IMXMLDocumentNode node)
    {
        // visit MXML
        IClassDefinition cdef = node.getClassDefinition();
        classDefinition = cdef;
        documentDefinition = cdef;

        // TODO (mschmalle) will remove this cast as more things get abstracted
        JSFlexJSASDocEmitter fjs = (JSFlexJSASDocEmitter) ((IMXMLBlockWalker) getMXMLWalker())
                .getASEmitter();

        fjs.getModel().setCurrentClass(cdef);
        scripts = new ArrayList<MXMLScriptSpecifier>();
        
        /* can there be asdoc for child tags?
        // visit tags
        final int len = node.getChildCount();
        for (int i = 0; i < len; i++)
        {
            getMXMLWalker().walk(node.getChild(i));
        }
		*/
        
        String cname = node.getFileNode().getName();

        //emitHeader(node);

        emitClassDeclStart(cname, node.getBaseClassName(), false);

        //emitPropertyDecls();
        emitScripts();
        
        emitClassDeclEnd(cname, node.getBaseClassName());

     //   emitMetaData(cdef);

        // can there be asdoc for subdocs?
        //write(subDocuments.toString());
        writeNewline();

        /* these probably don't get asdoc either
        emitEvents(cname);

        emitPropertyGetterSetters(cname);
        */

    }

    public void emitSubDocument(IMXMLComponentNode node)
    {
        // visit MXML
        IClassDefinition oldClassDef = classDefinition;
        IClassDefinition cdef = node.getContainedClassDefinition();
        classDefinition = cdef;
        IASEmitter asEmitter = ((IMXMLBlockWalker) getMXMLWalker())
                .getASEmitter();
        ((JSFlexJSASDocEmitter) asEmitter).getModel().pushClass(cdef);
        
        IASNode classNode = node.getContainedClassDefinitionNode();
        String cname = cdef.getQualifiedName();
        String baseClassName = cdef.getBaseClassAsDisplayString();
        subDocumentNames.add(cname);

        // visit tags
        final int len = classNode.getChildCount();
        for (int i = 0; i < len; i++)
        {
            getMXMLWalker().walk(classNode.getChild(i));
        }

        ((JSFlexJSASDocEmitter) asEmitter).mxmlEmitter = this;

        emitClassDeclStart(cname, baseClassName, false);

        emitComplexInitializers(classNode);
        
        //emitPropertyDecls();
        
        emitClassDeclEnd(cname, baseClassName);


        emitScripts();

        emitEvents(cname);

        emitPropertyGetterSetters(cname);

    }

    //--------------------------------------------------------------------------

    protected void emitClassDeclStart(String cname, String baseClassName,
            boolean indent)
    {
        write("<");
        writeToken(formatQualifiedName(cname));
        write(">");
        if (indent)
            indentPush();
    }

    //--------------------------------------------------------------------------

    protected void emitClassDeclEnd(String cname, String baseClassName)
    {
        indentPop();
        writeNewline();
        writeNewline();
        write("</");
        writeToken(formatQualifiedName(cname));
        write(">");
    }

    //--------------------------------------------------------------------------

    protected void emitScripts()
    {
        for (MXMLScriptSpecifier script : scripts)
        {
            String output = script.output();

            if (!output.equals(""))
            {
                writeNewline(output);
            }
        }
    }

    //--------------------------------------------------------------------------    

    protected void emitEvents(String cname)
    {
        for (MXMLEventSpecifier event : events)
        {
            writeNewline("/**");
            writeNewline(" * @export");
            writeNewline(" * @param {" + formatQualifiedName(event.type) + "} event");
            writeNewline(" */");
            writeNewline(formatQualifiedName(cname)
                    + ".prototype." + event.eventHandler + " = function(event)");
            writeNewline(ASEmitterTokens.BLOCK_OPEN, true);

            writeNewline(event.value + ASEmitterTokens.SEMICOLON.getToken(),
                    false);

            write(ASEmitterTokens.BLOCK_CLOSE);
            writeNewline(";");
            writeNewline();
            writeNewline();
        }
    }

    //--------------------------------------------------------------------------    

    protected void emitPropertyGetterSetters(String cname)
    {
    	int n = 0;
        for (MXMLDescriptorSpecifier instance : instances)
        {
            if (!instance.id.startsWith(MXMLFlexJSEmitterTokens.ID_PREFIX
                    .getToken()))
            {
            	n++;
            }
        }
    	if (n == 0 && descriptorTree.size() == 0)
    		return;
    	
    	String formattedCName = formatQualifiedName(cname);
    	
    	write("Object.defineProperties(");
    	write(formattedCName);
    	writeNewline(".prototype, /** @lends {" + formattedCName + ".prototype} */ {");
        indentPush();
        int i = 0;
        for (MXMLDescriptorSpecifier instance : instances)
        {
            if (!instance.id.startsWith(MXMLFlexJSEmitterTokens.ID_PREFIX
                    .getToken()))
            {
                indentPush();
                writeNewline("/** @export */");
                writeNewline(instance.id + ": {");
                writeNewline("/** @this {" + formattedCName + "} */");
                indentPush();
                writeNewline("get: function() {");
                indentPop();
                writeNewline("return this." + instance.id + "_;");
                writeNewline("},");
                writeNewline("/** @this {" + formattedCName + "} */");
                indentPush();
                writeNewline("set: function(value) {");
                indentPush();
                writeNewline("if (value != this." + instance.id + "_) {");
                writeNewline("this." + instance.id + "_ = value;");
                write("this.dispatchEvent(org.apache.flex.events.ValueChangeEvent.createUpdateEvent(this, '");
                indentPop();
                writeNewline(instance.id + "', null, value));");
                indentPop();
                writeNewline("}");
                indentPop();
                writeNewline("}");
                if (i < n - 1 || descriptorTree.size() > 0)
                	writeNewline("},");
                else
                {
                    indentPop();
                    writeNewline("}");
                }
                i++;
            }
        }
        if (descriptorTree.size() == 0)
        	writeNewline("});");
    }

    //--------------------------------------------------------------------------    

    private HashMap<IMXMLEventSpecifierNode, String> eventHandlerNameMap = new HashMap<IMXMLEventSpecifierNode, String>();
    
    @Override
    public void emitEventSpecifier(IMXMLEventSpecifierNode node)
    {
    	if (isStateDependent(node) && !inStatesOverride)
    		return;
    	
        IDefinition cdef = node.getDefinition();

        MXMLDescriptorSpecifier currentDescriptor = getCurrentDescriptor("i");

        MXMLEventSpecifier eventSpecifier = new MXMLEventSpecifier();
        eventSpecifier.eventHandler = MXMLFlexJSEmitterTokens.EVENT_PREFIX
                .getToken() + eventCounter++;
        eventSpecifier.name = cdef.getBaseName();
        eventSpecifier.type = node.getEventParameterDefinition()
                .getTypeAsDisplayString();

        eventHandlerNameMap.put(node, eventSpecifier.eventHandler);
        
        IASEmitter asEmitter = ((IMXMLBlockWalker) getMXMLWalker())
                .getASEmitter();

        StringBuilder sb = null;
        int len = node.getChildCount();
        if (len > 0)
        {
            sb = new StringBuilder();
            for (int i = 0; i < len; i++)
            {
                sb.append(getIndent((i > 0) ? 1 : 0)
                        + asEmitter.stringifyNode(node.getChild(i)));
                if (i < len - 1)
                {
                    sb.append(ASEmitterTokens.SEMICOLON.getToken());
                    sb.append(ASEmitterTokens.NEW_LINE.getToken());
                }
            }
        }
        eventSpecifier.value = sb.toString();

	    if (currentDescriptor != null)
	        currentDescriptor.eventSpecifiers.add(eventSpecifier);
	    else if (!inStatesOverride) // in theory, if no currentdescriptor must be top tag event
	        propertiesTree.eventSpecifiers.add(eventSpecifier);
        events.add(eventSpecifier);
    }

    @Override
    public void emitInstance(IMXMLInstanceNode node)
    {
        if (isStateDependent(node) && !inStatesOverride)
            return;
        
        IClassDefinition cdef = node
                .getClassReference((ICompilerProject) getMXMLWalker()
                        .getProject());

        MXMLDescriptorSpecifier currentPropertySpecifier = getCurrentDescriptor("ps");

        String id = node.getID();
        if (id == null)
            id = node.getEffectiveID();
        if (id == null)
            id = MXMLFlexJSEmitterTokens.ID_PREFIX.getToken() + idCounter++;

        MXMLDescriptorSpecifier currentInstance = new MXMLDescriptorSpecifier();
        currentInstance.isProperty = false;
        currentInstance.id = id;
        currentInstance.name = formatQualifiedName(cdef.getQualifiedName());
        currentInstance.parent = currentPropertySpecifier;

        if (currentPropertySpecifier != null)
            currentPropertySpecifier.propertySpecifiers.add(currentInstance);
        else if (inMXMLContent)
            descriptorTree.add(currentInstance);
        else
        {
            currentInstance.parent = propertiesTree;
            propertiesTree.propertySpecifiers.add(currentInstance);
        }

        instances.add(currentInstance);

        IMXMLPropertySpecifierNode[] pnodes = node.getPropertySpecifierNodes();
        if (pnodes != null)
        {
            moveDown(false, currentInstance, null);

            for (IMXMLPropertySpecifierNode pnode : pnodes)
            {
                getMXMLWalker().walk(pnode); // Property Specifier
            }

            moveUp(false, true);
        }
        else if (node instanceof IMXMLStateNode)
        {
            IMXMLStateNode stateNode = (IMXMLStateNode)node;
            String name = stateNode.getStateName();
            if (name != null)
            {
                MXMLDescriptorSpecifier stateName = new MXMLDescriptorSpecifier();
                stateName.isProperty = true;
                stateName.id = id;
                stateName.name = "name";
                stateName.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + name + ASEmitterTokens.SINGLE_QUOTE.getToken();
                stateName.parent = currentInstance;
                currentInstance.propertySpecifiers.add(stateName);
            }
            MXMLDescriptorSpecifier overrides = new MXMLDescriptorSpecifier();
            overrides.isProperty = true;
            overrides.hasArray = true;
            overrides.id = id;
            overrides.name = "overrides";
            overrides.parent = currentInstance;
            currentInstance.propertySpecifiers.add(overrides);
            moveDown(false, null, overrides);

            IMXMLClassDefinitionNode classDefinitionNode = stateNode.getClassDefinitionNode();
            List<IMXMLNode> snodes = classDefinitionNode.getNodesDependentOnState(stateNode.getStateName());
            if (snodes != null)
            {
                for (int i=snodes.size()-1; i>=0; --i)
                {
                    IMXMLNode inode = snodes.get(i);
                    if (inode.getNodeID() == ASTNodeID.MXMLInstanceID)
                    {
                        emitInstanceOverride((IMXMLInstanceNode)inode);
                    }
                }
                // Next process the non-instance overrides dependent on this state.
                // Each one will generate code to push an IOverride instance.
                for (IMXMLNode anode : snodes)
                {
                    switch (anode.getNodeID())
                    {
                        case MXMLPropertySpecifierID:
                        {
                            emitPropertyOverride((IMXMLPropertySpecifierNode)anode);
                            break;
                        }
                        case MXMLStyleSpecifierID:
                        {
                            emitStyleOverride((IMXMLStyleSpecifierNode)anode);
                            break;
                        }
                        case MXMLEventSpecifierID:
                        {
                            emitEventOverride((IMXMLEventSpecifierNode)anode);
                            break;
                        }
                        default:
                        {
                            break;
                        }
                    }
                }
            }
            
            moveUp(false, false);
        }

        IMXMLEventSpecifierNode[] enodes = node.getEventSpecifierNodes();
        if (enodes != null)
        {
            moveDown(false, currentInstance, null);

            for (IMXMLEventSpecifierNode enode : enodes)
            {
                getMXMLWalker().walk(enode); // Event Specifier
            }

            moveUp(false, true);
        }
    }

    public void emitPropertyOverride(IMXMLPropertySpecifierNode propertyNode)
    {
        FlexProject project = (FlexProject) getMXMLWalker().getProject();
        Name propertyOverride = project.getPropertyOverrideClassName();
        emitPropertyOrStyleOverride(propertyOverride, propertyNode);
    }
    
    /**
     * Generates instructions in the current context
     * to create an instance of mx.states.SetStyle
     * with its <code>target</code>, <code>name</code>,
     * and <code>value</code> properties set.
     */
    void emitStyleOverride(IMXMLStyleSpecifierNode styleNode)
    {
        FlexProject project = (FlexProject) getMXMLWalker().getProject();
        Name styleOverride = project.getStyleOverrideClassName();
        emitPropertyOrStyleOverride(styleOverride, styleNode);
    }
    
    void emitPropertyOrStyleOverride(Name overrideName, IMXMLPropertySpecifierNode propertyOrStyleNode)
    {
        MXMLDescriptorSpecifier currentInstance = getCurrentDescriptor("ps");
        IASNode parentNode = propertyOrStyleNode.getParent();
        String id = parentNode instanceof IMXMLInstanceNode ?
                    ((IMXMLInstanceNode)parentNode).getEffectiveID() :
                    null;
        
        String name = propertyOrStyleNode.getName();        
        
        boolean valueIsDataBound = isDataBindingNode(propertyOrStyleNode.getChild(0));
        IMXMLInstanceNode propertyOrStyleValueNode = propertyOrStyleNode.getInstanceNode();
        
        MXMLDescriptorSpecifier setProp = new MXMLDescriptorSpecifier();
        setProp.isProperty = false;
        setProp.name = formatQualifiedName(nameToString(overrideName));
        setProp.parent = currentInstance;
        currentInstance.propertySpecifiers.add(setProp);
        
        if (id != null)
        {
	            // Set its 'target' property to the id of the object
	            // whose property or style this override will set.
	        MXMLDescriptorSpecifier target = new MXMLDescriptorSpecifier();
	        target.isProperty = true;
	        target.name = "target";
	        target.parent = setProp;
	        target.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + id + ASEmitterTokens.SINGLE_QUOTE.getToken();
	        setProp.propertySpecifiers.add(target);
        }
        
            // Set its 'name' property to the name of the property or style.
        MXMLDescriptorSpecifier pname = new MXMLDescriptorSpecifier();
        pname.isProperty = true;
        pname.name = "name";
        pname.parent = setProp;
        pname.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + name + ASEmitterTokens.SINGLE_QUOTE.getToken();
        setProp.propertySpecifiers.add(pname);

        if (!valueIsDataBound)
        {
	            // Set its 'value' property to the value of the property or style.
	        MXMLDescriptorSpecifier value = new MXMLDescriptorSpecifier();
	        value.isProperty = true;
	        value.name = "value";
	        value.parent = setProp;
	        setProp.propertySpecifiers.add(value);
	        moveDown(false, null, value);
	        getMXMLWalker().walk(propertyOrStyleValueNode); // instance node
	        moveUp(false, false);
        }
        else
        {
            String overrideID = MXMLFlexJSEmitterTokens.BINDING_PREFIX.getToken() + bindingCounter++;
	        setProp.id = overrideID;
	        instances.add(setProp);
	        BindingDatabase bd = BindingDatabase.bindingMap.get(classDefinition);
	        Set<BindingInfo> bindingInfo = bd.getBindingInfo();
	        IMXMLDataBindingNode bindingNode = (IMXMLDataBindingNode)propertyOrStyleNode.getChild(0);
	        for (BindingInfo bi : bindingInfo)
	        {
	        	if (bi.node == bindingNode)
	        	{
	                bi.setDestinationString(overrideID + ".value");
	                break;
	        	}
	        }
        }
    }
        
    /**
     * Generates instructions in the current context
     * to create an instance of mx.states.SetEventHandler
     * with its <code>target</code>, <code>name</code>,
     * and <code>handlerFunction</code> properties set.
     */
    void emitEventOverride(IMXMLEventSpecifierNode eventNode)
    {
        inStatesOverride = true;
        
        MXMLDescriptorSpecifier currentInstance = getCurrentDescriptor("ps");
        FlexProject project = (FlexProject) getMXMLWalker().getProject();
        Name eventOverride = project.getEventOverrideClassName();
        
        IASNode parentNode = eventNode.getParent();
        String id = parentNode instanceof IMXMLInstanceNode ?
                    ((IMXMLInstanceNode)parentNode).getEffectiveID() :
                    "";
        
        String name = MXMLEventSpecifier.getJSEventName(eventNode.getName());
        
        String eventHandler = eventHandlerNameMap.get(eventNode);
        if (eventHandler == null)
        {
        	emitEventSpecifier(eventNode);
        	eventHandler = eventHandlerNameMap.get(eventNode);
        }

        MXMLDescriptorSpecifier setEvent = new MXMLDescriptorSpecifier();
        setEvent.isProperty = false;
        setEvent.name = formatQualifiedName(nameToString(eventOverride));
        setEvent.parent = currentInstance;
        currentInstance.propertySpecifiers.add(setEvent);
        // Set its 'target' property to the id of the object
        // whose event this override will set.
        MXMLDescriptorSpecifier target = new MXMLDescriptorSpecifier();
        target.isProperty = true;
        target.name = "target";
        target.parent = setEvent;
        target.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + id + ASEmitterTokens.SINGLE_QUOTE.getToken();
        setEvent.propertySpecifiers.add(target);

        // Set its 'name' property to the name of the event.
        MXMLDescriptorSpecifier pname = new MXMLDescriptorSpecifier();
        pname.isProperty = true;
        pname.name = "name";
        pname.parent = setEvent;
        pname.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + name + ASEmitterTokens.SINGLE_QUOTE.getToken();
        setEvent.propertySpecifiers.add(pname);
        
        // Set its 'handlerFunction' property to the autogenerated event handler.
        MXMLDescriptorSpecifier handler = new MXMLDescriptorSpecifier();
        handler.isProperty = true;
        handler.name = "handlerFunction";
        handler.parent = setEvent;
        handler.value = JSFlexJSEmitterTokens.CLOSURE_FUNCTION_NAME.getToken() + ASEmitterTokens.PAREN_OPEN.getToken() + 
        		ASEmitterTokens.THIS.getToken() + ASEmitterTokens.MEMBER_ACCESS.getToken() + eventHandler +
        		ASEmitterTokens.COMMA.getToken() + ASEmitterTokens.SPACE.getToken() + ASEmitterTokens.THIS.getToken() +
        		ASEmitterTokens.COMMA.getToken() + ASEmitterTokens.SPACE.getToken() + ASEmitterTokens.SINGLE_QUOTE.getToken() +
        			eventHandler + ASEmitterTokens.SINGLE_QUOTE.getToken() +
        		ASEmitterTokens.PAREN_CLOSE.getToken();
        setEvent.propertySpecifiers.add(handler);
        
        inStatesOverride = false;
    }

    public void emitInstanceOverride(IMXMLInstanceNode instanceNode)
    {
        inStatesOverride = true;
        
        MXMLDescriptorSpecifier currentInstance = getCurrentDescriptor("ps");
        FlexProject project = (FlexProject) getMXMLWalker().getProject();
        Name instanceOverrideName = project.getInstanceOverrideClassName();

        MXMLDescriptorSpecifier overrideInstances = getCurrentDescriptor("so");
        int index = overrideInstances.propertySpecifiers.size();
        if (nodeToIndexMap == null)
        	nodeToIndexMap = new HashMap<IMXMLNode, Integer>();
        if (nodeToIndexMap.containsKey(instanceNode))
        {
        	index = nodeToIndexMap.get(instanceNode);
        }
        else
        {
        	nodeToIndexMap.put(instanceNode, index);
            MXMLDescriptorSpecifier itemsDesc = new MXMLDescriptorSpecifier();
            itemsDesc.isProperty = true;
            itemsDesc.hasArray = true;
            itemsDesc.name = "itemsDescriptor";
            itemsDesc.parent = overrideInstances;
            overrideInstances.propertySpecifiers.add(itemsDesc);
            boolean oldInMXMLContent = inMXMLContent;
            moveDown(false, null, itemsDesc);
            inMXMLContent = true;
            getMXMLWalker().walk(instanceNode); // instance node
            inMXMLContent = oldInMXMLContent;
            moveUp(false, false);
        }

        MXMLDescriptorSpecifier addItems = new MXMLDescriptorSpecifier();
        addItems.isProperty = false;
        addItems.name = formatQualifiedName(nameToString(instanceOverrideName));
        addItems.parent = currentInstance;
        currentInstance.propertySpecifiers.add(addItems);
        MXMLDescriptorSpecifier itemsDescIndex = new MXMLDescriptorSpecifier();
        itemsDescIndex.isProperty = true;
        itemsDescIndex.hasArray = true;
        itemsDescIndex.name = "itemsDescriptorIndex";
        itemsDescIndex.parent = addItems;
        itemsDescIndex.value = Integer.toString(index);
        addItems.propertySpecifiers.add(itemsDescIndex);
        
        //-----------------------------------------------------------------------------
        // Second property set: maybe set destination and propertyName
        
        // get the property specifier node for the property the instanceNode represents
        IMXMLPropertySpecifierNode propertySpecifier = (IMXMLPropertySpecifierNode) 
            instanceNode.getAncestorOfType( IMXMLPropertySpecifierNode.class);
    
        if (propertySpecifier == null)
        {
           assert false;        // I think this indicates an invalid tree...
        }
        else
        {
            // Check the parent - if it's an instance then we want to use these
            // nodes to get our property values from. If not, then it's the root
            // and we don't need to specify destination
            
            IASNode parent = propertySpecifier.getParent();
            if (parent instanceof IMXMLInstanceNode)
            {
               IMXMLInstanceNode parentInstance = (IMXMLInstanceNode)parent;
               String parentId = parentInstance.getEffectiveID();
               assert parentId != null;
               String propName = propertySpecifier.getName();
               
               MXMLDescriptorSpecifier dest = new MXMLDescriptorSpecifier();
               dest.isProperty = true;
               dest.name = "destination";
               dest.parent = addItems;
               dest.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + parentId + ASEmitterTokens.SINGLE_QUOTE.getToken();
               addItems.propertySpecifiers.add(dest);

               MXMLDescriptorSpecifier prop = new MXMLDescriptorSpecifier();
               prop.isProperty = true;
               prop.name = "propertyName";
               prop.parent = addItems;
               prop.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + propName + ASEmitterTokens.SINGLE_QUOTE.getToken();
               addItems.propertySpecifiers.add(prop);
            }
        }  
        
        //---------------------------------------------------------------
        // Third property set: position and relativeTo
        String positionPropertyValue = null;
        String relativeToPropertyValue = null;
       
        // look to see if we have any sibling nodes that are not state dependent
        // that come BEFORE us
        IASNode instanceParent = instanceNode.getParent();
        IASNode prevStatelessSibling=null;
        for (int i=0; i< instanceParent.getChildCount(); ++i)
        {
            IASNode sib = instanceParent.getChild(i);
            assert sib instanceof IMXMLInstanceNode;    // surely our siblings are also instances?
           
            // stop looking for previous nodes when we find ourself
            if (sib == instanceNode)
                break;

            if (sib instanceof IMXMLInstanceNode && !isStateDependent(sib))
            {
                prevStatelessSibling = sib;
            }
        }
        
        if (prevStatelessSibling == null) {
            positionPropertyValue = "first";        // TODO: these should be named constants
        }
        else {
            positionPropertyValue = "after";
            relativeToPropertyValue = ((IMXMLInstanceNode)prevStatelessSibling).getEffectiveID();
        }
       
        MXMLDescriptorSpecifier pos = new MXMLDescriptorSpecifier();
        pos.isProperty = true;
        pos.name = "position";
        pos.parent = addItems;
        pos.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + positionPropertyValue + ASEmitterTokens.SINGLE_QUOTE.getToken();
        addItems.propertySpecifiers.add(pos);
        
        if (relativeToPropertyValue != null)
        {
            MXMLDescriptorSpecifier rel = new MXMLDescriptorSpecifier();
            rel.isProperty = true;
            rel.name = "relativeTo";
            rel.parent = addItems;
            rel.value = ASEmitterTokens.SINGLE_QUOTE.getToken() + relativeToPropertyValue + ASEmitterTokens.SINGLE_QUOTE.getToken();
            addItems.propertySpecifiers.add(rel);
        }
        
        inStatesOverride = false;
    }

    private String nameToString(Name name)
    {
        String s;
        Namespace ns = name.getSingleQualifier();
        s = ns.getName();
        if (s != "") s = s + ASEmitterTokens.MEMBER_ACCESS.getToken() + name.getBaseName();
        else s = name.getBaseName();
        return s;
    }
    /**
     * Determines whether a node is state-dependent.
     * TODO: we should move to IMXMLNode
     */
    protected boolean isStateDependent(IASNode node)
    {
        if (node instanceof IMXMLSpecifierNode)
        {
            String suffix = ((IMXMLSpecifierNode)node).getSuffix();
            return suffix != null && suffix.length() > 0;
        }
        else if (isStateDependentInstance(node))
            return true;
        return false;
    }
    
    /**
     * Determines whether the geven node is an instance node, as is state dependent
     */
    protected boolean isStateDependentInstance(IASNode node)
    {
        if (node instanceof IMXMLInstanceNode)
        {
            String[] includeIn = ((IMXMLInstanceNode)node).getIncludeIn();
            String[] excludeFrom = ((IMXMLInstanceNode)node).getExcludeFrom();
            return includeIn != null || excludeFrom != null;
        }
        return false;
    }
    
    /**
     * Is a give node a "databinding node"?
     */
    public static boolean isDataBindingNode(IASNode node)
    {
        return node instanceof IMXMLDataBindingNode;
    }
    
    protected static boolean isDataboundProp(IMXMLPropertySpecifierNode propertyNode)
    {
        boolean ret = propertyNode.getChildCount() > 0 && isDataBindingNode(propertyNode.getInstanceNode());
        
        // Sanity check that we based our conclusion about databinding on the correct node.
        // (code assumes only one child if databinding)
        int n = propertyNode.getChildCount();
        for (int i = 0; i < n; i++)
        {
            boolean db = isDataBindingNode(propertyNode.getChild(i));
            assert db == ret;
        }
        
        return ret;
    }

    @Override
    public void emitPropertySpecifier(IMXMLPropertySpecifierNode node)
    {
        if (isDataboundProp(node))
            return;
        
        if (isStateDependent(node))
            return;
        
        IDefinition cdef = node.getDefinition();

        IASNode cnode = node.getChild(0);

        MXMLDescriptorSpecifier currentInstance = getCurrentDescriptor("i");

        MXMLDescriptorSpecifier currentPropertySpecifier = new MXMLDescriptorSpecifier();
        currentPropertySpecifier.isProperty = true;
        currentPropertySpecifier.name = cdef.getQualifiedName();
        currentPropertySpecifier.parent = currentInstance;

        boolean oldInMXMLContent = inMXMLContent;
        boolean reusingDescriptor = false;
        if (currentPropertySpecifier.name.equals("mxmlContent"))
        {
            inMXMLContent = true;
            ArrayList<MXMLDescriptorSpecifier> specList = 
            	(currentInstance == null) ? descriptorTree : currentInstance.propertySpecifiers;
            for (MXMLDescriptorSpecifier ds : specList)
            {
            	if (ds.name.equals("mxmlContent"))
            	{
            		currentPropertySpecifier = ds;
            		reusingDescriptor = true;
            		break;
            	}
            }
        }
        
        if (currentInstance != null)
        {
        	// we end up here for children of tags
        	if (!reusingDescriptor)
        		currentInstance.propertySpecifiers.add(currentPropertySpecifier);
        }
        else if (inMXMLContent)
        {
        	// we end up here for top tags?
        	if (!reusingDescriptor)
        		descriptorTree.add(currentPropertySpecifier);
        }
        else
        {
            currentPropertySpecifier.parent = propertiesTree;
            propertiesTree.propertySpecifiers.add(currentPropertySpecifier);
        }

        boolean valueIsArray = cnode != null && cnode instanceof IMXMLArrayNode;
        boolean valueIsObject = cnode != null && cnode instanceof IMXMLObjectNode;

        currentPropertySpecifier.hasArray = valueIsArray;
        currentPropertySpecifier.hasObject = valueIsObject;

        moveDown(valueIsArray || valueIsObject, null, currentPropertySpecifier);

        getMXMLWalker().walk(cnode); // Array or Instance

        moveUp(valueIsArray || valueIsObject, false);
        
        inMXMLContent = oldInMXMLContent;
    }

    @Override
    public void emitScript(IMXMLScriptNode node)
    {
        IASEmitter asEmitter = ((IMXMLBlockWalker) getMXMLWalker())
                .getASEmitter();

        String nl = ASEmitterTokens.NEW_LINE.getToken();

        StringBuilder sb = null;
        MXMLScriptSpecifier scriptSpecifier = null;

        int len = node.getChildCount();
        if (len > 0)
        {
            for (int i = 0; i < len; i++)
            {
                IASNode cnode = node.getChild(i);
                getMXMLWalker().walk(cnode);
            }
        }
    }

    @Override
    public void emitStyleSpecifier(IMXMLStyleSpecifierNode node)
    {
    }

    //--------------------------------------------------------------------------

    @Override
    public void emitObject(IMXMLObjectNode node)
    {
        final int len = node.getChildCount();
    	if (!makingSimpleArray)
    	{
            for (int i = 0; i < len; i++)
            {
                getMXMLWalker().walk(node.getChild(i)); // props in object
            }    		
    	}
    	else
    	{
            MXMLDescriptorSpecifier ps = getCurrentDescriptor("ps");
            if (ps.value == null)
            	ps.value = "";
            ps.value += "{";	
            for (int i = 0; i < len; i++)
            {
                IMXMLPropertySpecifierNode propName = (IMXMLPropertySpecifierNode)node.getChild(i);
                ps.value += propName.getName() + ": ";	                
                getMXMLWalker().walk(propName.getChild(0));
                if (i < len - 1)
                    ps.value += ", ";	                                	
            }    		
            ps.value += "}";	
    	}
    }
    
    @Override
    public void emitArray(IMXMLArrayNode node)
    {
        moveDown(false, null, null);

        boolean isSimple = true;
        final int len = node.getChildCount();
        for (int i = 0; i < len; i++)
        {
            final IASNode child = node.getChild(i);
            ASTNodeID nodeID = child.getNodeID();
            if (nodeID == ASTNodeID.MXMLArrayID || nodeID == ASTNodeID.MXMLInstanceID || nodeID == ASTNodeID.MXMLStateID)
            {
                isSimple = false;
                break;
            }
        }
        boolean oldMakingSimpleArray = makingSimpleArray;
        MXMLDescriptorSpecifier ps = getCurrentDescriptor("ps");
        if (isSimple)
        {
        	makingSimpleArray = true;
        	ps.value = ASEmitterTokens.SQUARE_OPEN.getToken();
        }
        for (int i = 0; i < len; i++)
        {
            getMXMLWalker().walk(node.getChild(i)); // Instance
            if (isSimple && i < len - 1)
            	ps.value += ASEmitterTokens.COMMA.getToken();
        }
        if (isSimple)
        {
        	ps.value += ASEmitterTokens.SQUARE_CLOSE.getToken();        	
        }
        makingSimpleArray = oldMakingSimpleArray;

        moveUp(false, false);
    }

    @Override
    public void emitString(IMXMLStringNode node)
    {
        getCurrentDescriptor("ps").valueNeedsQuotes = true;

        emitAttributeValue(node);
    }

    //--------------------------------------------------------------------------

    @Override
    public void emitLiteral(IMXMLLiteralNode node)
    {
        MXMLDescriptorSpecifier ps = getCurrentDescriptor("ps");
        if (ps.value == null) // might be non-null if makingSimpleArray
        	ps.value = "";

        if (ps.valueNeedsQuotes)
            ps.value += ASEmitterTokens.SINGLE_QUOTE.getToken();

        String s = node.getValue().toString();
        if (ps.valueNeedsQuotes)
        {
            // escape all single quotes found within the string
            s = s.replace(ASEmitterTokens.SINGLE_QUOTE.getToken(), 
                    "\\" + ASEmitterTokens.SINGLE_QUOTE.getToken());
        }
        ps.value += s;
        
        if (ps.valueNeedsQuotes)
            ps.value += ASEmitterTokens.SINGLE_QUOTE.getToken();
    }

    //--------------------------------------------------------------------------

    @Override
    public void emitFactory(IMXMLFactoryNode node)
    {
        MXMLDescriptorSpecifier ps = getCurrentDescriptor("ps");
        ps.value = "new " + formatQualifiedName("org.apache.flex.core.ClassFactory") + "(";

        IASNode cnode = node.getChild(0);
        if (cnode instanceof IMXMLClassNode)
        {
            ps.value += formatQualifiedName(((IMXMLClassNode)cnode).getValue(getMXMLWalker().getProject()).getQualifiedName());
        }
        ps.value += ")";
    }

    //--------------------------------------------------------------------------

    @Override
    public void emitComponent(IMXMLComponentNode node)
    {
        MXMLDescriptorSpecifier ps = getCurrentDescriptor("ps");
        ps.value = "new " + formatQualifiedName("org.apache.flex.core.ClassFactory") + "(";

        ps.value += formatQualifiedName(documentDefinition.getQualifiedName()) + ".";
        ps.value += formatQualifiedName(node.getName());
        ps.value += ")";
        
        setBufferWrite(true);
        emitSubDocument(node);
        subDocuments.append(getBuilder().toString());
        getBuilder().setLength(0);
        setBufferWrite(false);
    }

    @Override
    protected void setBufferWrite(boolean value)
    {
    	super.setBufferWrite(value);
        IASEmitter asEmitter = ((IMXMLBlockWalker) getMXMLWalker()).getASEmitter();
        ((JSFlexJSASDocEmitter)asEmitter).setBufferWrite(value);
    }
    
    //--------------------------------------------------------------------------
    //    JS output
    //--------------------------------------------------------------------------
    
    private void emitHeader(IMXMLDocumentNode node)
    {
        String cname = node.getFileNode().getName();
        String bcname = node.getBaseClassName();

        FlexJSProject project = (FlexJSProject) getMXMLWalker().getProject();
        List<File> sourcePaths = project.getSourcePath();
        String sourceName = node.getSourcePath();
        for (File sourcePath : sourcePaths)
        {
            if (sourceName.startsWith(sourcePath.getAbsolutePath())) 
            {
            	sourceName = sourceName.substring(sourcePath.getAbsolutePath().length() + 1);        	
            }
        }
        writeNewline("/**");
        writeNewline(" * Generated by Apache Flex Cross-Compiler from " + sourceName);
        writeNewline(" * " + cname);
        writeNewline(" *");
        writeNewline(" * @fileoverview");
        writeNewline(" *");
        writeNewline(" * @suppress {checkTypes|accessControls}");
        writeNewline(" */");
        writeNewline();
        
        ArrayList<String> writtenInstances = new ArrayList<String>();
        emitHeaderLine(cname, true); // provide
        for (String subDocumentName : subDocumentNames)
        {
            emitHeaderLine(subDocumentName, true);
            writtenInstances.add(formatQualifiedName(subDocumentName));
        }
        writeNewline();
        emitHeaderLine(bcname);
        writtenInstances.add(formatQualifiedName(cname)); // make sure we don't add ourselves
        writtenInstances.add(formatQualifiedName(bcname)); // make sure we don't add the baseclass twice
        allInstances.addAll(0, instances);
        for (MXMLDescriptorSpecifier instance : allInstances)
        {
            String name = instance.name;
            if (writtenInstances.indexOf(name) == -1)
            {
                emitHeaderLine(name);
                writtenInstances.add(name);
            }
        }
        ASProjectScope projectScope = (ASProjectScope) project.getScope();
        IDefinition cdef = node.getDefinition();
        ICompilationUnit cu = projectScope
                .getCompilationUnitForDefinition(cdef);
        ArrayList<String> deps = project.getRequires(cu);

        // TODO (mschmalle) will remove this cast as more things get abstracted
        JSFlexJSASDocEmitter fjs = (JSFlexJSASDocEmitter) ((IMXMLBlockWalker) getMXMLWalker())
                .getASEmitter();
        if (fjs.getModel().hasStaticBindableVars()) {
            //we need to add EventDispatcher
            if (deps.indexOf(BindableEmitter.DISPATCHER_CLASS_QNAME) == -1)
                deps.add(BindableEmitter.DISPATCHER_CLASS_QNAME);
            if (usedNames.indexOf(BindableEmitter.DISPATCHER_CLASS_QNAME) == -1)
                usedNames.add(BindableEmitter.DISPATCHER_CLASS_QNAME);
        }

        if (interfaceList != null)
        {
        	String[] interfaces = interfaceList.split(", ");
        	for (String iface : interfaces)
        	{
        		deps.add(iface);
        		usedNames.add(iface);
        	}
        }
        if (deps != null)
        {
        	Collections.sort(deps);
            for (String imp : deps)
            {
                if (imp.indexOf(JSGoogEmitterTokens.AS3.getToken()) != -1)
                    continue;
    
                if (imp.equals(cname))
                    continue;
    
                if (imp.equals("mx.binding.Binding"))
                    continue;
                if (imp.equals("mx.binding.BindingManager"))
                    continue;
                if (imp.equals("mx.binding.FunctionReturnWatcher"))
                    continue;
                if (imp.equals("mx.binding.PropertyWatcher"))
                    continue;
                if (imp.equals("mx.binding.StaticPropertyWatcher"))
                    continue;
                if (imp.equals("mx.binding.XMLWatcher"))
                    continue;
                if (imp.equals("mx.events.PropertyChangeEvent"))
                    continue;
                if (imp.equals("mx.events.PropertyChangeEventKind"))
                    continue;
                if (imp.equals("mx.core.DeferredInstanceFromFunction"))
                    continue;
    
                if (NativeUtils.isNative(imp))
                    continue;
    
                String formatted = formatQualifiedName(imp, false);
                if (writtenInstances.indexOf(formatted) == -1)
                {
                    emitHeaderLine(imp);
                    writtenInstances.add(formatted);
                }
            }
        }

        // erikdebruin: Add missing language feature support, like the 'is' and 
        //              'as' operators. We don't need to worry about requiring
        //              this in every project: ADVANCED_OPTIMISATIONS will NOT
        //              include any of the code if it is not used in the project.
        if (project.mainCU != null &&
                cu.getName().equals(project.mainCU.getName()))
        {
            if (project instanceof FlexJSProject)
            {
            	if (((FlexJSProject)project).needLanguage)
            		emitHeaderLine(JSFlexJSEmitterTokens.LANGUAGE_QNAME.getToken());
            }
        }

        writeNewline();
        writeNewline();
    }

    private void emitHeaderLine(String qname)
    {
        emitHeaderLine(qname, false);
    }

    private void emitHeaderLine(String qname, boolean isProvide)
    {
        write((isProvide) ? JSGoogEmitterTokens.GOOG_PROVIDE
                : JSGoogEmitterTokens.GOOG_REQUIRE);
        write(ASEmitterTokens.PAREN_OPEN);
        write(ASEmitterTokens.SINGLE_QUOTE);
        write(formatQualifiedName(qname, false));
        write(ASEmitterTokens.SINGLE_QUOTE);
        write(ASEmitterTokens.PAREN_CLOSE);
        writeNewline(ASEmitterTokens.SEMICOLON);
    }

    private String createRequireLine(String qname, boolean isProvide) {
        StringBuilder createHeader = new StringBuilder();
        createHeader.append(isProvide ? JSGoogEmitterTokens.GOOG_PROVIDE.getToken() : JSGoogEmitterTokens.GOOG_REQUIRE.getToken());
        createHeader.append(ASEmitterTokens.PAREN_OPEN.getToken());
        createHeader.append(ASEmitterTokens.SINGLE_QUOTE.getToken());
        createHeader.append(qname);
        createHeader.append(ASEmitterTokens.SINGLE_QUOTE.getToken());
        createHeader.append(ASEmitterTokens.PAREN_CLOSE.getToken());
        createHeader.append(ASEmitterTokens.SEMICOLON.getToken());
        return createHeader.toString();
    }

    //--------------------------------------------------------------------------
    //    Utils
    //--------------------------------------------------------------------------

    @Override
    protected void emitAttributeValue(IASNode node)
    {
        IMXMLLiteralNode cnode = (IMXMLLiteralNode) node.getChild(0);

        if (cnode.getValue() != null)
            getMXMLWalker().walk((IASNode) cnode); // Literal
    }

    private MXMLDescriptorSpecifier getCurrentDescriptor(String type)
    {
        MXMLDescriptorSpecifier currentDescriptor = null;

        int index;

        if (type.equals("i"))
        {
            index = currentInstances.size() - 1;
            if (index > -1)
                currentDescriptor = currentInstances.get(index);
        }
        else if (type.equals("so"))
        {
            return currentStateOverrides;
        }
        else
        {
            index = currentPropertySpecifiers.size() - 1;
            if (index > -1)
                currentDescriptor = currentPropertySpecifiers.get(index);
        }

        return currentDescriptor;
    }

    protected void moveDown(boolean byPass,
            MXMLDescriptorSpecifier currentInstance,
            MXMLDescriptorSpecifier currentPropertySpecifier)
    {
        if (!byPass)
        {
            if (currentInstance != null)
                currentInstances.add(currentInstance);
        }

        if (currentPropertySpecifier != null)
            currentPropertySpecifiers.add(currentPropertySpecifier);
    }

    protected void moveUp(boolean byPass, boolean isInstance)
    {
        if (!byPass)
        {
            int index;

            if (isInstance)
            {
                index = currentInstances.size() - 1;
                if (index > -1)
                    currentInstances.remove(index);
            }
            else
            {
                index = currentPropertySpecifiers.size() - 1;
                if (index > -1)
                    currentPropertySpecifiers.remove(index);
            }
        }
    }

    public String formatQualifiedName(String name)
    {
    	return formatQualifiedName(name, true);
    }
    
    protected String formatQualifiedName(String name, boolean useName)
    {
    	/*
    	if (name.contains("goog.") || name.startsWith("Vector."))
    		return name;
    	name = name.replaceAll("\\.", "_");
    	*/
    	if (subDocumentNames.contains(name))
    		return documentDefinition.getQualifiedName() + "." + name;
        if (NativeUtils.isJSNative(name)) return name;
		if (useName && !usedNames.contains(name))
			usedNames.add(name);
     	return name;
    }

    private void emitComplexInitializers(IASNode node)
    {
    	int n = node.getChildCount();
    	for (int i = 0; i < n; i++)
    	{
    		IASNode child = node.getChild(i);
    		if (child.getNodeID() == ASTNodeID.MXMLScriptID)
    		{
    			int m = child.getChildCount();
    			for (int j = 0; j < m; j++)
    			{
    				IASNode schild = child.getChild(j);
    				ASTNodeID schildID = schild.getNodeID();
    				if (schildID == ASTNodeID.VariableID ||
    						schildID == ASTNodeID.BindableVariableID)
    				{
    					IVariableNode varnode = (IVariableNode)schild;
    			        IExpressionNode vnode = varnode.getAssignedValueNode();
    			        if (vnode != null && (!(varnode.isConst() || EmitterUtils.isScalar(vnode))))
    			        {
    	                    writeNewline();
    	                    write(ASEmitterTokens.THIS);
    	                    write(ASEmitterTokens.MEMBER_ACCESS);
    	                    write(varnode.getName());
    	                    if (schildID == ASTNodeID.BindableVariableID)
    	                    	write("_"); // use backing variable
    	                    write(ASEmitterTokens.SPACE);
    	                    writeToken(ASEmitterTokens.EQUAL);
    	                    JSFlexJSASDocEmitter fjs = (JSFlexJSASDocEmitter) ((IMXMLBlockWalker) getMXMLWalker())
    	                    .getASEmitter();
    	                    fjs.getWalker().walk(vnode);
    	                    write(ASEmitterTokens.SEMICOLON);

    			        }
    				}
    			}
    		}
    	}
    }
    
    @Override
    public void emitImplements(IMXMLImplementsNode node)
    {
    	StringBuilder list = new StringBuilder();
    	boolean needsComma = false;
        IIdentifierNode[] interfaces = node.getInterfaceNodes();
        for (IIdentifierNode iface : interfaces)
        {
        	if (needsComma)
        		list.append(", ");
        	list.append(iface.getName());
        	needsComma = true;
        }
        //System.out.println("mxml implements "+list);
        interfaceList = list.toString();
    }
    
}