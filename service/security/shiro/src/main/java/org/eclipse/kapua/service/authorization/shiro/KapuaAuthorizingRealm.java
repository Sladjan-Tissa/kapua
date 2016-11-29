/*******************************************************************************
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.kapua.service.authorization.shiro;

import org.apache.shiro.ShiroException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.model.query.predicate.AttributePredicate;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.query.KapuaListResult;
import org.eclipse.kapua.model.query.predicate.KapuaPredicate;
import org.eclipse.kapua.service.authorization.access.AccessInfo;
import org.eclipse.kapua.service.authorization.access.AccessInfoFactory;
import org.eclipse.kapua.service.authorization.access.AccessInfoQuery;
import org.eclipse.kapua.service.authorization.access.AccessInfoService;
import org.eclipse.kapua.service.authorization.access.AccessPermission;
import org.eclipse.kapua.service.authorization.access.AccessRole;
import org.eclipse.kapua.service.authorization.access.shiro.AccessInfoPredicates;
import org.eclipse.kapua.service.authorization.permission.Permission;
import org.eclipse.kapua.service.authorization.role.Role;
import org.eclipse.kapua.service.authorization.role.RolePermission;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JPA-based application's one and only configured Apache Shiro Realm.
 */
public class KapuaAuthorizingRealm extends AuthorizingRealm {

    private static final Logger logger = LoggerFactory.getLogger(KapuaAuthorizingRealm.class);

    public static final String REALM_NAME = "kapuaAuthorizingRealm";

    public KapuaAuthorizingRealm() throws KapuaException {
        setName(REALM_NAME);
    }

    /**
     * Authorization.
     */
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals)
            throws AuthenticationException {
        //
        // Extract principal
        String username = (String) principals.getPrimaryPrincipal();
        logger.debug("Getting authorization info for: {}", username);

        //
        // Get Services
        KapuaLocator locator = KapuaLocator.getInstance();

        UserService userService = locator.getService(UserService.class);
        AccessInfoService accessInfoService = locator.getService(AccessInfoService.class);
        AccessInfoFactory accessInfoFactory = locator.getFactory(AccessInfoFactory.class);

        //
        // Get the associated user by name
        final User user;
        try {
            user = KapuaSecurityUtils.doPriviledge(() -> userService.findByName(username));
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new ShiroException("Error while find user!", e);
        }

        //
        // Check existence
        if (user == null) {
            throw new UnknownAccountException();
        }

        //
        // Get user access infos
        AccessInfoQuery accessInfoQuery = accessInfoFactory.newQuery(user.getScopeId());
        KapuaPredicate predicate = new AttributePredicate<KapuaId>(AccessInfoPredicates.USER_ID, user.getId());
        accessInfoQuery.setPredicate(predicate);

        final KapuaListResult<AccessInfo> accessInfos;
        try {
            accessInfos = KapuaSecurityUtils.doPriviledge(() -> accessInfoService.query(accessInfoQuery));
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new ShiroException("Error while find access info!", e);
        }

        //
        // Check existence
        if (accessInfos == null) {
            throw new UnknownAccountException();
        }

        //
        // Create SimpleAuthorizationInfo with principals permissions
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        // Get user roles set and related permissions
        for (AccessInfo accessInfo : accessInfos.getItems()) {

            for (AccessPermission accessPermission : accessInfo.getPermissions()) {
                Permission p = accessPermission.getPermission();
                info.addStringPermission(p.toString());
                logger.trace("User: {} has permission: {}", username, p);
            }

            for (AccessRole accessRole : accessInfo.getRoles()) {

                Role role = accessRole.getRole();
                info.addRole(role.getName());
                for (RolePermission rolePermission : role.getRolePermissions()) {

                    Permission p = rolePermission.getPermission();
                    info.addStringPermission(p.toString());
                    logger.trace("Role: {} has permission: {}", role, p);
                }
            }

        }

        //
        // Return authorization info
        return info;
    }

    @Override
    public boolean supports(AuthenticationToken authenticationToken) {
        /**
         * This method always returns false as it works only as AuthorizingReam.
         */
        return false;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
            throws AuthenticationException {
        /**
         * This method can always return null as it does not support any authentication token.
         */
        return null;
    }

}