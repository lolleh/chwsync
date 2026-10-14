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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Privilege;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.context.UserContext;
import org.openmrs.api.context.UsernamePasswordAuthenticationScheme;
import org.openmrs.api.context.UsernamePasswordCredentials;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.dto.AuthDtos;
import org.openmrs.module.chwsync.api.model.ChwSyncDevice;
import org.openmrs.module.chwsync.api.service.ChwSyncDeviceService;
import org.openmrs.module.chwsync.api.sync.Json;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a device trades a password for a token.
 * <p>
 * This is the only endpoint a CHW types a password into, and the only one that sees it. Everything
 * afterwards is a bearer token, so the password is never stored on the handset and a lost phone is
 * dealt with by revoking a token rather than changing a password everywhere the CHW has ever logged
 * in.
 */
@RestController
@RequestMapping(ChwSyncConstants.REST_NAMESPACE + "/auth")
public class SyncAuthController extends BaseSyncController {
	
	/**
	 * Verifies a username and password, registers the device, and issues it a token.
	 * <p>
	 * The device is registered in the same call so a flaky connection cannot leave the user logged in
	 * on a server that does not know the handset exists.
	 */
	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public AuthDtos.LoginResponse login(@RequestBody AuthDtos.LoginRequest request) {
		if (request == null || StringUtils.isBlank(request.getUsername()) || StringUtils.isBlank(request.getPassword())) {
			throw new IllegalArgumentException("A username and password are required.");
		}
		
		boolean opened = !Context.isSessionOpen();
		if (opened) {
			Context.openSession();
		}
		try {
			// The distro's default scheme is the authentication module's delegating one, which only
			// understands its own credentials and refuses a plain username/password. Replacing the
			// context with one wired to the username/password scheme is what lets Context.authenticate
			// work here at all; it also sets the authenticated user so the service calls below pass
			// their @Authorized checks. The context is cleared again in the finally block.
			UserContext userContext = new UserContext(new UsernamePasswordAuthenticationScheme());
			Context.setUserContext(userContext);
			
			User user;
			try {
				userContext
				        .authenticate(new UsernamePasswordCredentials(request.getUsername().trim(), request.getPassword()));
				user = Context.getAuthenticatedUser();
			}
			catch (ContextAuthenticationException e) {
				// The same message whether the user is unknown or the password is wrong, so this cannot
				// be used to enumerate accounts.
				throw new UnauthenticatedException("The username or password is incorrect.");
			}
			
			if (user == null) {
				throw new UnauthenticatedException("The username or password is incorrect.");
			}
			
			ChwSyncDeviceService deviceService = Context.getService(ChwSyncDeviceService.class);
			Date now = new Date();
			
			String deviceUuid = StringUtils.trimToNull(request.getDeviceUuid());
			if (deviceUuid == null) {
				// A client that never generated one still gets a working login; the server just names the
				// device for it.
				deviceUuid = java.util.UUID.randomUUID().toString();
			}
			
			final ChwSyncDevice device;
			final String token;
			try {
				device = deviceService.register(deviceUuid, request.getDeviceName(), request.getPlatform(),
				    request.getAppVersion(), user.getUserId(), now);
				token = deviceService.issueToken(user.getUserId(), device.getUuid(), now);
			}
			catch (APIAuthenticationException e) {
				// The password was right but the account was never granted the sync privilege.
				throw forbidden(e);
			}
			
			AuthDtos.LoginResponse response = new AuthDtos.LoginResponse();
			response.setToken(token);
			response.setDeviceUuid(device.getUuid());
			response.setUserUuid(user.getUuid());
			response.setUsername(user.getUsername());
			response.setDisplayName(user.getPersonName() == null ? user.getUsername() : user.getPersonName().getFullName());
			response.setPrivileges(privilegeNames(user));
			response.setExpiresAt(Json.formatDateTime(new Date(now.getTime() + tokenTtlHours() * 3600_000L)));
			response.setServerTime(Json.formatDateTime(now));
			return response;
		}
		finally {
			Context.clearUserContext();
			if (opened) {
				Context.closeSession();
			}
		}
	}
	
	/** What the current token is good for, so a client can warn before a call starts failing. */
	@RequestMapping(value = "/me", method = RequestMethod.GET)
	public AuthDtos.LoginResponse me(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization) {
		return asDevice(authorization, new DeviceWork<AuthDtos.LoginResponse>() {
			
			@Override
			public AuthDtos.LoginResponse run() {
				User user = currentUser();
				AuthDtos.LoginResponse response = new AuthDtos.LoginResponse();
				response.setUserUuid(user.getUuid());
				response.setUsername(user.getUsername());
				response.setDisplayName(
				    user.getPersonName() == null ? user.getUsername() : user.getPersonName().getFullName());
				response.setPrivileges(privilegeNames(user));
				response.setServerTime(Json.formatDateTime(new Date()));
				return response;
			}
		});
	}
	
	/**
	 * Revokes the presented token, which is how a user signs a handset out without touching the account
	 * password. Idempotent: revoking an already-dead token is not an error.
	 */
	@RequestMapping(value = "/logout", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) final String authorization) {
		asDevice(authorization, new DeviceWork<Void>() {
			
			@Override
			public Void run() {
				Context.getService(ChwSyncDeviceService.class).revokeToken(bearerToken(authorization));
				return null;
			}
		});
	}
	
	private static List<String> privilegeNames(User user) {
		List<String> names = new ArrayList<String>();
		for (Privilege privilege : user.getPrivileges()) {
			names.add(privilege.getName());
		}
		return names;
	}
	
	private static int tokenTtlHours() {
		String configured = Context.getAdministrationService().getGlobalProperty(ChwSyncConstants.GP_TOKEN_TTL_HOURS);
		if (StringUtils.isNotBlank(configured)) {
			try {
				return Integer.parseInt(configured.trim());
			}
			catch (NumberFormatException e) {
				// fall through to the default
			}
		}
		return ChwSyncConstants.DEFAULT_TOKEN_TTL_HOURS;
	}
}
