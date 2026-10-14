/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.openmrs.User;
import org.openmrs.module.chwsync.api.model.ChwSyncDevice;
import org.openmrs.module.chwsync.api.model.ClientToken;
import org.openmrs.module.chwsync.api.model.SyncChange;
import org.openmrs.module.chwsync.api.sync.ChangeOperation;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;

/**
 * Persistence for the sync feed itself: the change log, the devices, and the tokens they present.
 * <p>
 * The interface exists so the sync engine depends on what it can do, not on how it is implemented;
 * the only implementation speaks native SQL.
 */
public interface SyncDao {
	
	void appendChange(SyncEntityType type, String entityUuid, ChangeOperation operation, Date changedAt, Integer changedBy,
	        String deviceUuid, String clientChangeId);
	
	List<SyncChange> findChanges(Date fromExclusive, Date toInclusive, Collection<SyncEntityType> types, int limit);
	
	String findClientChangeResult(String clientChangeId);
	
	void recordClientChange(String clientChangeId, String deviceUuid, String resultJson, Date processedAt);
	
	ChwSyncDevice findDeviceByUuid(String uuid);
	
	List<ChwSyncDevice> findDevicesByUser(Integer userId);
	
	List<ChwSyncDevice> listDevices();
	
	void insertDevice(ChwSyncDevice device);
	
	void updateDevice(ChwSyncDevice device);
	
	void touchDevice(String uuid, Date when);
	
	void setDeviceActive(String uuid, boolean active);
	
	void insertToken(ClientToken token);
	
	ClientToken findTokenByHash(String tokenHash);
	
	/**
	 * Loads a user directly, bypassing {@code UserService.getUser} and its {@code Get Users} privilege.
	 * A device token is resolved to its user before anyone is authenticated, so the privileged lookup
	 * can never be used here.
	 */
	User findUserById(Integer userId);
	
	void touchToken(Integer tokenId, Date when);
	
	void revokeToken(Integer tokenId);
	
	int revokeTokensForDevice(String deviceUuid);
}
