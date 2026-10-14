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

import org.openmrs.User;
import org.openmrs.api.context.Authenticated;
import org.openmrs.api.context.AuthenticationScheme;
import org.openmrs.api.context.BasicAuthenticated;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.context.Credentials;
import org.openmrs.module.chwsync.api.model.ClientToken;
import org.openmrs.module.chwsync.api.service.ChwSyncDeviceService;

/**
 * Resolves a device bearer token to the user it was issued for.
 * <p>
 * The token is looked up by its hash and rejected if it is unknown, expired or revoked, so taking
 * access away from a lost handset is a single update to the token row. The user it maps to then
 * carries whatever roles and privileges they have everywhere else in OpenMRS - this scheme grants
 * no privilege of its own, which is the point: a device cannot do more than its owner.
 */
public class ChwSyncTokenAuthenticationScheme implements AuthenticationScheme {
	
	@Override
	public Authenticated authenticate(Credentials credentials) throws ContextAuthenticationException {
		if (!(credentials instanceof ChwSyncTokenCredentials)) {
			throw new ContextAuthenticationException("CHW device tokens must be presented as ChwSyncTokenCredentials");
		}
		String token = ((ChwSyncTokenCredentials) credentials).getToken();
		
		ChwSyncDeviceService deviceService = Context.getService(ChwSyncDeviceService.class);
		ClientToken record = deviceService == null ? null : deviceService.verifyToken(token);
		if (record == null || record.getUserId() == null) {
			throw new ContextAuthenticationException("The device token is unknown, expired or revoked");
		}
		
		// UserService.getUser is @Authorized("Get Users"), and this runs before that user is
		// authenticated, so the client-facing lookup goes through the module's own DAO instead.
		User user = deviceService.findUserById(record.getUserId());
		if (user == null || Boolean.TRUE.equals(user.getRetired())) {
			throw new ContextAuthenticationException("The user this device token belongs to no longer exists");
		}
		
		// Best-effort: a failed last-used update must not fail the login it is recording.
		try {
			deviceService.touchToken(record, new java.util.Date());
		}
		catch (RuntimeException ignored) {
			// nothing useful to do here; the token still authenticates
		}
		
		return new BasicAuthenticated(user, ChwSyncTokenCredentials.SCHEME);
	}
}
