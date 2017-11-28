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

package com.microsoft.java.debug.core.trace;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class AmbientContext extends ConcurrentHashMap<String, Object> {
    public static final String AMBCTX_ID = AmbientContext.class.getSimpleName() + "Id";
    static InheritableThreadLocal<AmbientContext> callContext = new InheritableThreadLocal<AmbientContext>() {
        @Override
        protected AmbientContext initialValue() {
            return null;
        }
    };
    private String id;
    private AtomicLong count = new AtomicLong(0);

    /**
     * Constructor.
     */
    public AmbientContext() {
        super();
        id = UUID.randomUUID().toString();
        put(AMBCTX_ID, id);
    }

    /**
     * Copy constructor.
     */
    public AmbientContext(AmbientContext context) {
        super(context);
    }

    public String getId() {
        return id;
    }

    /**
     * current Context.
     */
    public static AmbientContext currentContext() {
        AmbientContext context = tryGetCurrentContext();
        if (null == context) {
            context = initializeAmbientContext(null);
        }
        return context;
    }

    public static void removeAmbientContext() {
        callContext.remove();
    }

    /**
     * initialize ambient context.
     */
    public static AmbientContext initializeAmbientContext(AmbientContext rawContext) {
        AmbientContext context = (rawContext != null) ? rawContext : new AmbientContext();
        callContext.set(context);
        return context;
    }

    public static AmbientContext tryGetCurrentContext() {
        return callContext.get();
    }

    /**
     * create branch.
     */
    public AmbientContext createBranch() {
        String branchId = generateNextCorrelationId();
        AmbientContext branchContext = new AmbientContext(this);
        branchContext.id = branchId;
        return branchContext;
    }

    private String generateNextCorrelationId() {
        return String.format("%s.%s", id, count.incrementAndGet());
    }
}
