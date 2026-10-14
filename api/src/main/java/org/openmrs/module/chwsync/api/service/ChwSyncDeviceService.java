/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.service;

import java.util.Date;
import java.util.List;

import org.openmrs.User;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.model.ChwSyncDevice;
import org.openmrs.module.chwsync.api.model.ClientToken;

/**
 * Device registration and the bearer tokens devices present instead of a password.
 * <p>
 * Tokens exist so a CHW's password is typed once and never stored on the handset. What is stored is
 * an opaque bearer value the server can revoke, which is the only realistic way to take access back
 * from a lost phone without changing the user's password everywhere.
 */
public interface ChwSyncDeviceService extends OpenmrsService {
	
	/**
	 * Registers a device on first sight, or refreshes the metadata of one already known. The device is
	 * never reassigned to a different user; a device that reappears claiming another user is rejected
	 * by the caller.
	 *
	 * @param uuid the device's own stable identifier
	 * @param name a human name for the administrator's list
	 * @param platform android / web
	 * @param appVersion the client version, for support
	 * @param userId the user the device belongs to
	 * @param now the moment of registration
	 * @return the stored device
	 */
	@Authorized(ChwSyncConstants.SYNC_PRIVILEGE)
	ChwSyncDevice register(String uuid, String name, String platform, String appVersion, Integer userId, Date now);
	
	@Authorized(ChwSyncConstants.MANAGE_DEVICES_PRIVILEGE)
	ChwSyncDevice getDevice(String uuid);
	
	@Authorized(ChwSyncConstants.MANAGE_DEVICES_PRIVILEGE)
	List<ChwSyncDevice> listDevices();
	
	@Authorized(ChwSyncConstants.MANAGE_DEVICES_PRIVILEGE)
	void setActive(String uuid, boolean active);
	
	/**
	 * Issues a fresh bearer token for a device and returns the plaintext exactly once. Only its hash is
	 * retained, so the value returned here cannot be recovered afterwards.
	 *
	 * @param userId the user the token authenticates as
	 * @param deviceUuid the device it is bound to
	 * @param now the issue moment
	 * @return the plaintext token to hand to the client
	 */
	@Authorized(ChwSyncConstants.SYNC_PRIVILEGE)
	String issueToken(Integer userId, String deviceUuid, Date now);
	
	/**
	 * Resolves a plaintext token to its stored record, or null when it is unknown, expired or revoked.
	 */
	ClientToken verifyToken(String token);
	
	/**
	 * Loads the user a token was issued for, without requiring the {@code Get Users} privilege.
	 * <p>
	 * This runs while a device token is being resolved, before any user is authenticated, so it cannot
	 * go through the privileged {@code UserService.getUser}.
	 */
	User findUserById(Integer userId);
	
	void touchToken(ClientToken token, Date now);
	
	void revokeToken(String token);
	
	/**
	 * Revokes every token bound to a device, which is what deactivating a lost handset does.
	 *
	 * @return the number of tokens revoked
	 */
	@Authorized(ChwSyncConstants.MANAGE_DEVICES_PRIVILEGE)
	int revokeDevice(String deviceUuid);
}
