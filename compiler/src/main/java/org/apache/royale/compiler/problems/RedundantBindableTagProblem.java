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

package org.apache.royale.compiler.problems;

import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.common.SourceLocation;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.problems.annotations.DefaultSeverity;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IDefinitionNode;

/**
 * Problem generated when code attempts to bind to something that isn't bindable.
 */
@DefaultSeverity(CompilerProblemSeverity.WARNING)
public final class RedundantBindableTagProblem extends SemanticProblem
{
    public static final String DESCRIPTION =
        "Class already marked [Bindable]; property-level [Bindable] is redundant and will be ignored.";

    public static final int warningCode = 5050;

    /**
     * @param site should be the identifier node of identifier/thing with the redundant [Bindable] that is causing the problem.
     */
    public RedundantBindableTagProblem(IASNode site)
    {
        super(site);
    }

}
