/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public interface IEvaluationProvider extends IProvider {
    void eval(String project, String expression, StackFrame sf, IEvaluationListener listener);
    boolean isInEvaluation(ThreadReference thread);
}
