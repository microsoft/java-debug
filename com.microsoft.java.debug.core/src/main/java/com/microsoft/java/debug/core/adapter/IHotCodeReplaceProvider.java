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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.reactivex.Observable;

public interface IHotCodeReplaceProvider extends IProvider {
    void onClassRedefined(Consumer<List<String>> consumer);

    CompletableFuture<List<String>> redefineClasses();

    Observable<HotCodeReplaceEvent> getEventHub();
}
