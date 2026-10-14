/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.web.controller;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.security.ChwSyncTokenAuthenticationScheme;
import org.openmrs.module.chwsync.api.security.ChwSyncTokenCredentials;

/**
 * Shared plumbing for the sync endpoints: turning an {@code Authorization: Bearer} header into an
 * authenticated OpenMRS context, and tearing that context down again.
 * <p>
 * The module deliberately does not authenticate itself. It hands the token to a
 * {@link ChwSyncTokenAuthenticationScheme} and lets OpenMRS resolve it to a user, so a request
 * through this API carries exactly the privileges that user has everywhere else. That keeps
 * authorization in one place and means a device can never do more than its owner, but it also means
 * every protected endpoint has to establish a real user context before it touches a core service -
 * which is what {@link #asDevice} is for.
 */
public abstract class BaseSyncController {
	
	/** The work to run once a device token has been resolved to a user. */
	protected interface DeviceWork<T> {
		
		T run();
	}
	
	/**
	 * Establishes the user behind a device token for the duration of {@code work}.
	 * <p>
	 * The context is replaced, because the default context cannot authenticate anything but a username
	 * and password; it is restored afterwards so a thread reused for another request does not carry
	 * this user into it.
	 *
	 * @param authorization the raw {@code Authorization} header
	 * @param work the request handling, run as the token's user
	 * @return whatever the work returns
	 * @throws UnauthenticatedException the header is missing or the token no longer resolves
	 * @throws ForbiddenException the user behind the token may not sync
	 */
	protected <T> T asDevice(String authorization, DeviceWork<T> work) {
		final String token = bearerToken(authorization);
		if (StringUtils.isBlank(token)) {
			throw new UnauthenticatedException("A device token is required.");
		}
		
		final boolean opened = !Context.isSessionOpen();
		if (opened) {
			Context.openSession();
		}
		try {
			UserContext userContext = new UserContext(new ChwSyncTokenAuthenticationScheme());
			Context.setUserContext(userContext);
			try {
				userContext.authenticate(new ChwSyncTokenCredentials(token, "chwsync-device"));
			}
			catch (ContextAuthenticationException e) {
				throw new UnauthenticatedException("The device token is not valid, or has expired or been revoked.");
			}
			
			if (!Context.hasPrivilege(ChwSyncConstants.SYNC_PRIVILEGE)) {
				throw new ForbiddenException("This account is not allowed to sync CHW data.");
			}
			
			return work.run();
		}
		finally {
			Context.clearUserContext();
			if (opened) {
				Context.closeSession();
			}
		}
	}
	
	/**
	 * Establishes the user behind a device token and requires a further privilege, for the device
	 * administration endpoints only {@code Manage CHW Devices} holders may reach.
	 */
	protected <T> T asManager(String authorization, DeviceWork<T> work) {
		return asDevice(authorization, new DeviceWork<T>() {
			
			@Override
			public T run() {
				if (!Context.hasPrivilege(ChwSyncConstants.MANAGE_DEVICES_PRIVILEGE)) {
					throw new ForbiddenException("This account may not manage CHW devices.");
				}
				return work.run();
			}
		});
	}
	
	/** The authenticated user, for audit columns and display. */
	protected User currentUser() {
		return Context.getAuthenticatedUser();
	}
	
	/**
	 * Extracts the token from an {@code Authorization} header. A missing {@code Bearer } prefix is
	 * tolerated because clients get it wrong often enough that rejecting the token over it would just
	 * be a support burden.
	 *
	 * @return the token, or null when there is nothing usable
	 */
	protected String bearerToken(String header) {
		if (StringUtils.isBlank(header)) {
			return null;
		}
		String trimmed = header.trim();
		if (trimmed.regionMatches(true, 0, ChwSyncConstants.AUTH_HEADER_PREFIX, 0,
		    ChwSyncConstants.AUTH_HEADER_PREFIX.length())) {
			return trimmed.substring(ChwSyncConstants.AUTH_HEADER_PREFIX.length()).trim();
		}
		return trimmed;
	}
	
	/** No usable device token was presented. Answered with 401. */
	public static class UnauthenticatedException extends RuntimeException {
		
		private static final long serialVersionUID = 1L;
		
		public UnauthenticatedException(String message) {
			super(message);
		}
	}
	
	/** The token's user is known but lacks the required privilege. Answered with 403. */
	public static class ForbiddenException extends RuntimeException {
		
		private static final long serialVersionUID = 1L;
		
		public ForbiddenException(String message) {
			super(message);
		}
	}
	
	/** The requested record does not exist. Answered with 404. */
	public static class NotFoundException extends RuntimeException {
		
		private static final long serialVersionUID = 1L;
		
		public NotFoundException(String message) {
			super(message);
		}
	}
	
	/** Translates a core authorization failure into this module's 403, keeping the message. */
	protected static ForbiddenException forbidden(APIAuthenticationException e) {
		return new ForbiddenException(e.getMessage() == null ? "You do not have permission to do that." : e.getMessage());
	}
}
