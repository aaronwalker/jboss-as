/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.jpa.subsystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jboss.as.jpa.JpaMessages.MESSAGES;

/**
 * @author Scott Marlow
 */
public class JBossAssemblyDescriptor {
    private Map<Class<?>, Object> any = new ConcurrentHashMap<Class<?>, Object>();

    public void addAny(Object a) {
        Class<?> cls = a.getClass();
        Object previous = any.put(cls, a);
        assert previous == null : MESSAGES.objectAlreadyDefined(cls, previous);
    }

    public <T> T getAny(Class<T> type) {
        return type.cast(any.get(type));
    }
}
