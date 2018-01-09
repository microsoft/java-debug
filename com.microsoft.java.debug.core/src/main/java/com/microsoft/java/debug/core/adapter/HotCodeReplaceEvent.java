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

public class HotCodeReplaceEvent {

    public enum EventType {
        ERROR(-1),

        WARNING(-2),

        STARTING(1),

        END(2),

        BUILD_COMPLETE(3);

        private int value;

        private EventType(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    private EventType eventType;

    private String message;

    public HotCodeReplaceEvent(EventType eventType, String message) {
        this.eventType = eventType;
        this.message = message;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }
}
