/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

public class ShutdownHookTest {
    public static void main(String[] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(ShutdownHookTest::onShutdown));
        System.out.println("ready");
        Thread.sleep(Long.MAX_VALUE);
    }

    private static void onShutdown() {
        int hookReached = 1;
        System.out.println(hookReached);
    }
}
