/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.security;

import org.openmrs.api.context.Credentials;

/**
 * A device bearer token offered to OpenMRS as a credential.
 * <p>
 * This exists so a CHW handset can be authenticated by the same machinery that authenticates a user
 * typing a password: the token is presented to a {@link ChwSyncTokenAuthenticationScheme}, which
 * resolves it to a user and hands that user to the context. Everything downstream -
 * {@code @Authorized} checks, core service calls, audit columns - then behaves exactly as it does
 * for an interactive login, instead of the module having to re-implement authorization for its own
 * endpoints.
 */
public class ChwSyncTokenCredentials implements Credentials {
	
	/** Kept stable: it is written to the authentication audit trail. */
	public static final String SCHEME = "chwsyncToken";
	
	private final String token;
	
	private final String clientName;
	
	public ChwSyncTokenCredentials(String token, String clientName) {
		this.token = token;
		this.clientName = clientName;
	}
	
	public String getToken() {
		return token;
	}
	
	@Override
	public String getAuthenticationScheme() {
		return SCHEME;
	}
	
	@Override
	public String getClientName() {
		return clientName == null ? "chwsync-device" : clientName;
	}
}
