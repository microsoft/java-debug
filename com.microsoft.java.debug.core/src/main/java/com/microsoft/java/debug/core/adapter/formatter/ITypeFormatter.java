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

package com.microsoft.java.debug.core.adapter.formatter;

/**
 * Represents a formatter dedicated to handling specific types within the Java Debug Interface (JDI).
 * This interface extends the {@link IFormatter}, inheriting its methods for converting objects to string representations
 * and determining applicability based on type. Implementers of this interface should provide type-specific
 * formatting logic to accurately represent objects during debugging.
 */
public interface ITypeFormatter extends IFormatter  {
    // Inherits all methods from IFormatter without adding new ones.
}
