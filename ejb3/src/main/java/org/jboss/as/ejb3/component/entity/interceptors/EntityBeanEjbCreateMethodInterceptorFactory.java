/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.ejb3.component.entity.interceptors;

import org.jboss.as.ee.component.Component;
import org.jboss.as.ejb3.component.entity.EntityBeanComponent;
import org.jboss.as.ejb3.component.entity.EntityBeanComponentInstance;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.InterceptorFactoryContext;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interceptor factory for entity beans that class the corresponding ejbCreate method.
 * <p/>
 * This is a post construct interceptor for the Ejb(Local)Object view
 *
 * @author Stuart Douglas
 */
public class EntityBeanEjbCreateMethodInterceptorFactory implements InterceptorFactory {

    public static final Object EXISTING_ID_CONTEXT_KEY = new Object();

    private final Object primaryKeyContextKey;

    public EntityBeanEjbCreateMethodInterceptorFactory(Object primaryKeyContextKey) {
        this.primaryKeyContextKey = primaryKeyContextKey;
    }

    @Override
    public Interceptor create(InterceptorFactoryContext context) {
        final Object existing = context.getContextData().get(EXISTING_ID_CONTEXT_KEY);

        final AtomicReference<Object> primaryKeyReference = new AtomicReference<Object>();
        context.getContextData().put(this.primaryKeyContextKey, primaryKeyReference);

        final Method ejbCreate = (Method) context.getContextData().get(EntityBeanHomeCreateInterceptorFactory.EJB_CREATE_METHOD_KEY);
        final Method ejbPostCreate = (Method) context.getContextData().get(EntityBeanHomeCreateInterceptorFactory.EJB_POST_CREATE_METHOD_KEY);
        final Object[] params = (Object[]) context.getContextData().get(EntityBeanHomeCreateInterceptorFactory.PARAMETERS_KEY);

        return new Interceptor() {
            @Override
            public Object processInvocation(final InterceptorContext context) throws Exception {

                if (existing != null) {
                    primaryKeyReference.set(existing);
                    return context.proceed();
                }

                final Component component = context.getPrivateData(Component.class);
                if (!(component instanceof EntityBeanComponent)) {
                    throw new IllegalStateException("Unexpected component: " + component + " Expected " + EntityBeanComponent.class);
                }
                final EntityBeanComponent entityBeanComponent = (EntityBeanComponent) component;
                //grab an unasociated entity bean from the pool
                final EntityBeanComponentInstance instance = entityBeanComponent.getPool().get();

                //call the ejbCreate method
                final Object primaryKey = ejbCreate.invoke(instance.getInstance(), params);
                instance.associate(primaryKey);
                ejbPostCreate.invoke(instance.getInstance(), params);
                primaryKeyReference.set(primaryKey);

                //now add the instance to the cache, so it is usable
                //note that we do not release it back to the pool
                //the cache will do that when it is expired or removed
                entityBeanComponent.getCache().create(instance);

                //if a transaction is active we register a sync
                //and if the transaction is rolled back we release the instance back into the pool

                final TransactionSynchronizationRegistry transactionSynchronizationRegistry = entityBeanComponent.getTransactionSynchronizationRegistry();
                if (transactionSynchronizationRegistry.getTransactionKey() != null) {
                    transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
                        @Override
                        public void beforeCompletion() {

                        }

                        @Override
                        public void afterCompletion(final int status) {
                            if (status != Status.STATUS_COMMITTED) {
                                //if the transaction is rolled back we release the instance back into the pool
                                entityBeanComponent.getPool().release(instance);
                            }
                        }
                    });
                }

                return context.proceed();
            }
        };

    }

}
