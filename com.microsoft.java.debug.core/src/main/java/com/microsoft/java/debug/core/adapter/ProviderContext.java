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

import java.util.HashMap;
import java.util.Map;

public class ProviderContext implements IProviderContext {

    private Map<Class<? extends IProvider>, IProvider> providerMap;

    public ProviderContext() {
        providerMap = new HashMap<>();
    }

    /**
     * Get the registered provider with the interface type,
     * <code>IllegalArgumentException</code> will raise if the provider is absent.
     * The returned object is type-safe to be assigned to T since registerProvider
     * will check the compatibility, so suppress unchecked rule.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends IProvider> T getProvider(Class<T> clazz) {
        if (!providerMap.containsKey(clazz)) {
            throw new IllegalArgumentException(String.format("%s has not been registered.", clazz.getName()));
        }
        return (T) providerMap.get(clazz);
    }

    @Override
    public void registerProvider(Class<? extends IProvider> clazz, IProvider provider) {
        if (clazz == null) {
            throw new IllegalArgumentException("Null provider class is illegal.");
        }

        if (provider == null) {
            throw new IllegalArgumentException("Null provider is illegal.");
        }

        if (providerMap.containsKey(clazz)) {
            throw new IllegalArgumentException(String.format("%s has already been registered.", clazz.getName()));
        }

        if (!clazz.isInstance(provider)) {
            throw new IllegalArgumentException(String.format("The provider doesn't implement interface %s.", clazz.getName()));
        }

        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("The provider class should be an interface");
        }

        providerMap.put(clazz, provider);
    }

}
