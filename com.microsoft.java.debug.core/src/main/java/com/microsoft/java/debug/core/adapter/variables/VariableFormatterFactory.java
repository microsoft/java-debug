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

package com.microsoft.java.debug.core.adapter.variables;

import com.microsoft.java.debug.core.adapter.formatter.ArrayObjectFormatter;
import com.microsoft.java.debug.core.adapter.formatter.BooleanFormatter;
import com.microsoft.java.debug.core.adapter.formatter.CharacterFormatter;
import com.microsoft.java.debug.core.adapter.formatter.ClassObjectFormatter;
import com.microsoft.java.debug.core.adapter.formatter.NullObjectFormatter;
import com.microsoft.java.debug.core.adapter.formatter.NumericFormatter;
import com.microsoft.java.debug.core.adapter.formatter.ObjectFormatter;
import com.microsoft.java.debug.core.adapter.formatter.SimpleTypeFormatter;
import com.microsoft.java.debug.core.adapter.formatter.StringObjectFormatter;

public final class VariableFormatterFactory {
    /**
     * Private constructor to prevent instance of <code>VariableFormatterFactory</code>.
     */
    private VariableFormatterFactory() {

    }

    /**
     * Create an <code>IVariableFormatter</code> instance with proper value and type formatters.
     * @return an <code>IVariableFormatter</code> instance
     */
    public static IVariableFormatter createVariableFormatter() {
        VariableFormatter formatter = new VariableFormatter();
        formatter.registerTypeFormatter(new SimpleTypeFormatter(), 1);
        formatter.registerValueFormatter(new BooleanFormatter(), 1);
        formatter.registerValueFormatter(new CharacterFormatter(), 1);
        formatter.registerValueFormatter(new NumericFormatter(), 1);
        formatter.registerValueFormatter(new ObjectFormatter(formatter::typeToString), 1);
        formatter.registerValueFormatter(new NullObjectFormatter(), 1);

        formatter.registerValueFormatter(new StringObjectFormatter(), 2);
        formatter.registerValueFormatter(new ArrayObjectFormatter(formatter::typeToString), 2);
        formatter.registerValueFormatter(new ClassObjectFormatter(formatter::typeToString), 2);
        return formatter;
    }
}
