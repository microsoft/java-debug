/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter;

import java.util.List;

import com.microsoft.java.debug.core.protocol.Types.CompletionItem;
import com.sun.jdi.StackFrame;

public interface ICompletionsProvider extends IProvider {

    /**
     * Complete the code snippet on the target frame.
     *
     * @param frame
     *            the target frame that the completions on
     * @param snippet
     *            the code snippet text
     * @param line
     *            the line number of the operation happens inside the snippet
     * @param column
     *            the column number of the operation happens inside the snippet
     * @return a list of {@link CompletionItem}
     */
    List<CompletionItem> codeComplete(StackFrame frame, String snippet, int line, int column);
}
