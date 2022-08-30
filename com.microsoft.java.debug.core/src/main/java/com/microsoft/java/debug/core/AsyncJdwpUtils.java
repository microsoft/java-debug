/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core;

import static java.util.concurrent.CompletableFuture.allOf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class AsyncJdwpUtils {
    /**
     * Create a the thread pool to process JDWP tasks.
     * JDWP tasks are IO-bounded, so use a relatively large thread pool for JDWP tasks.
     */
    public static ExecutorService jdwpThreadPool = Executors.newWorkStealingPool(100);
    // public static ExecutorService jdwpThreadPool = Executors.newCachedThreadPool();

    public static CompletableFuture<Void> runAsync(List<Runnable> tasks) {
        return runAsync(jdwpThreadPool, tasks.toArray(new Runnable[0]));
    }

    public static CompletableFuture<Void> runAsync(Runnable... tasks) {
        return runAsync(jdwpThreadPool, tasks);
    }

    public static CompletableFuture<Void> runAsync(Executor executor, List<Runnable> tasks) {
        return runAsync(executor, tasks.toArray(new Runnable[0]));
    }

    public static CompletableFuture<Void> runAsync(Executor executor, Runnable... tasks) {
        List<CompletableFuture<Void>> promises = new ArrayList<>();
        for (Runnable task : tasks) {
            if (task == null) {
                continue;
            }

            promises.add(CompletableFuture.runAsync(task, executor));
        }

        return CompletableFuture.allOf(promises.toArray(new CompletableFuture[0]));
    }

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return supplyAsync(jdwpThreadPool, supplier);
    }

    public static <U> CompletableFuture<U> supplyAsync(Executor executor, Supplier<U> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public static <U> U await(CompletableFuture<U> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }

            throw ex;
        }
    }

    public static <U> List<U> await(CompletableFuture<U>[] futures) {
        List<U> results = new ArrayList<>();
        try {
            allOf(futures).join();
            for (CompletableFuture<U> future : futures) {
                results.add(await(future));
            }
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }

            throw ex;
        }

        return results;
    }

    public static <U> List<U> await(List<CompletableFuture<U>> futures) {
        return await((CompletableFuture<U>[]) futures.toArray(new CompletableFuture[0]));
    }

    public static <U> CompletableFuture<List<U>> all(CompletableFuture<U>... futures) {
        return allOf(futures).thenApply((res) -> {
            List<U> results = new ArrayList<>();
            for (CompletableFuture<U> future : futures) {
                results.add(future.join());
            }

            return results;
        });
    }

    public static <U> CompletableFuture<List<U>> all(List<CompletableFuture<U>> futures) {
        return allOf(futures.toArray(new CompletableFuture[0])).thenApply((res) -> {
            List<U> results = new ArrayList<>();
            for (CompletableFuture<U> future : futures) {
                results.add(future.join());
            }

            return results;
        });
    }

    public static <U> CompletableFuture<List<U>> flatAll(CompletableFuture<List<U>>... futures) {
        return allOf(futures).thenApply((res) -> {
            List<U> results = new ArrayList<>();
            for (CompletableFuture<List<U>> future : futures) {
                results.addAll(future.join());
            }

            return results;
        });
    }

    public static <U> CompletableFuture<List<U>> flatAll(List<CompletableFuture<List<U>>> futures) {
        return allOf(futures.toArray(new CompletableFuture[0])).thenApply((res) -> {
            List<U> results = new ArrayList<>();
            for (CompletableFuture<List<U>> future : futures) {
                results.addAll(future.join());
            }

            return results;
        });
    }
}
