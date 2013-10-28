/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.picketlink.idm.credential.handler;

import static org.picketlink.idm.IDMLog.CREDENTIAL_LOGGER;

import java.util.List;
import java.util.Map;

import org.picketlink.idm.IdentityManagementException;
import org.picketlink.idm.IdentityManager;
import org.picketlink.idm.credential.AbstractBaseCredentials;
import org.picketlink.idm.credential.Credentials.Status;
import org.picketlink.idm.credential.storage.CredentialStorage;
import org.picketlink.idm.credential.util.CredentialUtils;
import org.picketlink.idm.model.Account;
import org.picketlink.idm.model.AttributedType;
import org.picketlink.idm.model.IdentityType;
import org.picketlink.idm.spi.IdentityContext;
import org.picketlink.idm.spi.IdentityStore;

/**
 * <p>Base class for {@link CredentialHandler} implementations.</p>
 *
 * @author pedroigor
 */
public abstract class AbstractCredentialHandler<S extends IdentityStore<?>, V extends AbstractBaseCredentials, U>
        implements CredentialHandler<S, V, U> {

    private static final String DEFAULT_LOGIN_NAME_PROPERTY = "loginName";

    /**
     * This is the name of the identity type property that will be used to retrieve the account's
     * login name, used for account lookup.
     */
    public static final String LOGIN_NAME_PROPERTY = "LOGIN_NAME_PROPERTY";

    private String loginNameProperty = DEFAULT_LOGIN_NAME_PROPERTY;

    public void setup(S store) {
        Map<String, Object> options = store.getConfig().getCredentialHandlerProperties();

        if (options != null) {

            String loginNameProperty = (String) options.get(LOGIN_NAME_PROPERTY);
            if (loginNameProperty != null) {
                this.loginNameProperty = loginNameProperty;
            }
        }
    }

    protected Account getAccount(final IdentityContext context, String loginName) {
        IdentityManager identityManager = getIdentityManager(context);

        if (isDebugEnabled()) {
            CREDENTIAL_LOGGER.debugf("Trying to find account with [%s] property value of [%s].",
                loginNameProperty, loginName);
        }

        List<IdentityType> accounts = identityManager.createIdentityQuery(IdentityType.class)
                .setParameter(AttributedType.QUERY_ATTRIBUTE.byName(loginNameProperty),
                        loginName).getResultList();
        if (accounts.isEmpty()) {
            return null;
        } else if (accounts.size() == 1) {
            IdentityType result = accounts.get(0);
            if (!Account.class.isInstance(result.getClass())) {
                throw new IdentityManagementException("Error - the IdentityType returned is not an Account: [" +
                result.toString() + "]");
            }

            return (Account) result;
        } else {
            throw new IdentityManagementException("Error - multiple Account objects found with same login name");
        }
    }

    @Override
    public void validate(final IdentityContext context, final V credentials, final S store) {
        credentials.setStatus(Status.IN_PROGRESS);

        if (isDebugEnabled()) {
            CREDENTIAL_LOGGER.debugf("Starting validation for credentials [%s][%s] using identity store [%s] and credential handler [%s].", credentials.getClass(), credentials, store, this);
        }

        Account account = getAccount(context, credentials);

        if (account != null) {
            if (isDebugEnabled()) {
                CREDENTIAL_LOGGER.debugf("Found account [%s] from credentials [%s].", account, credentials);
            }

            if (account.isEnabled()) {
                if (isDebugEnabled()) {
                    CREDENTIAL_LOGGER.debugf("Account [%s] is ENABLED.", account, credentials);
                }

                CredentialStorage credentialStorage = getCredentialStorage(context, account, credentials, store);

                if (isDebugEnabled()) {
                    CREDENTIAL_LOGGER.debugf("Current credential storage for account [%s] is [%s].", account, credentialStorage);
                }

                if (validateCredential(credentialStorage, credentials)) {
                    if (credentialStorage != null && CredentialUtils.isCredentialExpired(credentialStorage)) {
                        credentials.setStatus(Status.EXPIRED);
                    } else if (Status.IN_PROGRESS.equals(credentials.getStatus())) {
                        credentials.setStatus(Status.VALID);
                    }
                }
            } else {
                if (isDebugEnabled()) {
                    CREDENTIAL_LOGGER.debugf("Account [%s] is DISABLED.", account, credentials);
                }
                credentials.setStatus(Status.ACCOUNT_DISABLED);
            }
        } else {
            if (isDebugEnabled()) {
                CREDENTIAL_LOGGER.debugf("Account NOT FOUND for credentials [%s][%s].", credentials.getClass(), credentials);
            }
        }

        credentials.setValidatedAccount(null);

        if (Status.VALID.equals(credentials.getStatus())) {
            credentials.setValidatedAccount(account);
        } else if (Status.IN_PROGRESS.equals(credentials.getStatus())) {
            credentials.setStatus(Status.INVALID);
        }

        if (isDebugEnabled()) {
            CREDENTIAL_LOGGER.debugf("Finishing validation for credential [%s][%s] validated using identity store [%s] and credential handler [%s]. Status [%s]. Validated Account [%s]",
                    credentials.getClass(), credentials, store, this, credentials.getStatus(), credentials.getValidatedAccount());
        }
    }

    protected abstract boolean validateCredential(final CredentialStorage credentialStorage, final V credentials);
    protected abstract Account getAccount(final IdentityContext context, final V credentials);
    protected abstract CredentialStorage getCredentialStorage(final IdentityContext context, final Account account,
                                                              final V credentials,
                                                              final S store);

    protected IdentityManager getIdentityManager(IdentityContext context) {
        IdentityManager identityManager = context.getParameter(IdentityManager.IDENTITY_MANAGER_CTX_PARAMETER);

        if (identityManager == null) {
            throw new IdentityManagementException("IdentityManager not set into context.");
        }

        return identityManager;
    }

    protected boolean isDebugEnabled() {
        return CREDENTIAL_LOGGER.isDebugEnabled();
    }

}
