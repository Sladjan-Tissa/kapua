/*******************************************************************************
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kapua.commons.jpa;

import javax.persistence.PersistenceException;

import org.eclipse.kapua.KapuaEntityExistsException;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.commons.util.KapuaExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entity manager session reference implementation.
 * 
 * @since 1.0
 * 
 */
public class EntityManagerSession {

    private static final Logger logger = LoggerFactory.getLogger(EntityManagerSession.class);

    private final EntityManagerFactory entityManagerFactory;
    private final static int           MAX_INSERT_ALLOWED_RETRY = SystemSetting.getInstance().getInt(SystemSettingKey.KAPUA_INSERT_MAX_RETRY);

    /**
     * Constructor
     * 
     * @param entityManagerFactory
     */
    public EntityManagerSession(EntityManagerFactory entityManagerFactory)
    {
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Execute the action on a new entity manager.<br>
     * <br>
     * WARNING!<br>
     * The transactionality (if needed by the code) must be managed internally to the entityManagerActionCallback.<br>
     * This method performs only a rollback (if the transaction is active and an error occurred)!<br>
     * 
     * @param entityManagerActionCallback
     * @throws KapuaException
     */
    public <T> void onEntityManagerAction(EntityManagerActionCallback entityManagerActionCallback) throws KapuaException {
        EntityManager manager = null;
        try {
            manager = entityManagerFactory.createEntityManager();
            entityManagerActionCallback.actionOn(manager);
        } catch (KapuaException e) {
            if (manager!=null) {
                manager.rollback();
            }
            throw KapuaExceptionUtils.convertPersistenceException(e);
        } finally {
            if (manager != null) {
                manager.close();
            }
        }
    }

    /**
     * Return the execution result invoked on a new entity manager.<br>
     * <br>
     * WARNING!<br>
     * The transactionality (if needed by the code) must be managed internally to the entityManagerResultCallback.<br>
     * This method performs only a rollback (if the transaction is active and an error occurred)!<br>
     * 
     * @param entityManagerResultCallback
     * @return
     * @throws KapuaException
     */
    public <T> T onEntityManagerResult(EntityManagerResultCallback<T> entityManagerResultCallback) throws KapuaException {
        EntityManager manager = null;
        try {
            manager = entityManagerFactory.createEntityManager();
            return entityManagerResultCallback.onEntityManager(manager);
        } catch (KapuaException e) {
            if (manager != null) {
                manager.rollback();
            }
            throw KapuaExceptionUtils.convertPersistenceException(e);
        } finally {
            if (manager != null) {
                manager.close();
            }
        }
    }

    /**
     * Return the insert execution result invoked on a new entity manager.<br>
     * This method differs from the onEntityManagerResult because it reiterates the execution if it fails due to {@link KapuaEntityExistsException} for a maximum retry.<br>
     * The maximum allowed retry is set by {@link SystemSettingKey#KAPUA_INSERT_MAX_RETRY}.<br>
     * <br>
     * WARNING!<br>
     * The transactionality (if needed by the code) must be managed internally to the entityManagerInsertCallback.<br>
     * This method performs only a rollback (if the transaction is active and an error occurred)!<br>
     * 
     * @param entityManagerInsertCallback
     * @return
     * @throws KapuaException
     */
    public <T> T onEntityManagerInsert(EntityManagerInsertCallback<T> entityManagerInsertCallback) throws KapuaException
    {
        boolean succeeded = false;
        int retry = 0;
        EntityManager manager = entityManagerFactory.createEntityManager();
        T instance = null;
        try {
            do {
                try {
                    instance = entityManagerInsertCallback.onEntityInsert(manager);
                    succeeded = true;
                }
                catch (KapuaEntityExistsException e) {
                    if (manager != null) {
                        manager.rollback();
                    }
                    if (++retry < MAX_INSERT_ALLOWED_RETRY) {
                        logger.warn("Entity already exists. Cannot insert the entity, try again!");
                    }
                    else {
                        throw KapuaExceptionUtils.convertPersistenceException(e);
                    }
                }
                catch (PersistenceException e) {
                    if (manager != null) {
                        manager.rollback();
                    }
                    throw KapuaExceptionUtils.convertPersistenceException(e);
                }
            }
            while (!succeeded);
        }
        finally {
            if (manager != null) {
                manager.close();
            }
        }
        return instance;
    }

}
