/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.dao.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.User;
import org.openmrs.module.chwsync.api.dao.AbstractChwSyncDao;
import org.openmrs.module.chwsync.api.dao.SyncDao;
import org.openmrs.module.chwsync.api.model.ChwSyncDevice;
import org.openmrs.module.chwsync.api.model.ClientToken;
import org.openmrs.module.chwsync.api.model.SyncChange;
import org.openmrs.module.chwsync.api.sync.ChangeOperation;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;

/**
 * Native-SQL persistence for the sync feed: the change log, the registered devices, and the bearer
 * tokens devices present.
 */
public class HibernateSyncDao extends AbstractChwSyncDao implements SyncDao {
	
	private static final String CHANGE_COLUMNS = "SELECT change_id, entity_type, entity_uuid, operation, changed_at,"
	        + " changed_by_id, origin_device_uuid, client_change_id FROM chwsync_change";
	
	@Override
	public void appendChange(SyncEntityType type, String entityUuid, ChangeOperation operation, Date changedAt,
	        Integer changedBy, String deviceUuid, String clientChangeId) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("entityType", type.getWireName());
		params.put("entityUuid", entityUuid);
		params.put("operation", operation.getWireName());
		params.put("changedAt", changedAt);
		params.put("changedBy", changedBy);
		params.put("deviceUuid", deviceUuid);
		params.put("clientChangeId", clientChangeId);
		execute("INSERT INTO chwsync_change (entity_type, entity_uuid, operation, changed_at, changed_by_id,"
		        + " origin_device_uuid, client_change_id) VALUES (:entityType, :entityUuid, :operation, :changedAt,"
		        + " :changedBy, :deviceUuid, :clientChangeId)",
		    params);
	}
	
	@Override
	public List<SyncChange> findChanges(Date fromExclusive, Date toInclusive, Collection<SyncEntityType> types, int limit) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("from", fromExclusive);
		params.put("to", toInclusive);
		StringBuilder sql = new StringBuilder(CHANGE_COLUMNS);
		sql.append(" WHERE changed_at > :from AND changed_at <= :to");
		if (types != null && !types.isEmpty()) {
			List<String> names = new ArrayList<String>();
			for (SyncEntityType type : types) {
				names.add(type.getWireName());
			}
			params.put("types", names);
			sql.append(" AND entity_type IN (:types)");
		}
		sql.append(" ORDER BY changed_at ASC, change_id ASC");
		return list(sql.toString(), params, HibernateSyncDao::mapChange, limit);
	}
	
	@Override
	public String findClientChangeResult(String clientChangeId) {
		if (clientChangeId == null) {
			return null;
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("clientChangeId", clientChangeId);
		String result = unique("SELECT result_json FROM chwsync_client_change WHERE client_change_id = :clientChangeId",
		    params, row -> asString(row[0]));
		return result;
	}
	
	@Override
	public void recordClientChange(String clientChangeId, String deviceUuid, String resultJson, Date processedAt) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("clientChangeId", clientChangeId);
		params.put("deviceUuid", deviceUuid);
		params.put("resultJson", resultJson);
		params.put("processedAt", processedAt);
		execute("INSERT INTO chwsync_client_change (client_change_id, device_uuid, result_json, processed_at)"
		        + " VALUES (:clientChangeId, :deviceUuid, :resultJson, :processedAt)",
		    params);
	}
	
	@Override
	public ChwSyncDevice findDeviceByUuid(String uuid) {
		if (uuid == null) {
			return null;
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", uuid);
		return unique("SELECT device_id, uuid, user_id, name, platform, app_version, active, date_created, last_seen"
		        + " FROM chwsync_device WHERE uuid = :uuid",
		    params, HibernateSyncDao::mapDevice);
	}
	
	@Override
	public List<ChwSyncDevice> findDevicesByUser(Integer userId) {
		if (userId == null) {
			return new ArrayList<ChwSyncDevice>();
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("userId", userId);
		return list(
		    "SELECT device_id, uuid, user_id, name, platform, app_version, active, date_created, last_seen"
		            + " FROM chwsync_device WHERE user_id = :userId ORDER BY date_created DESC",
		    params, HibernateSyncDao::mapDevice, -1);
	}
	
	@Override
	public List<ChwSyncDevice> listDevices() {
		return list("SELECT device_id, uuid, user_id, name, platform, app_version, active, date_created, last_seen"
		        + " FROM chwsync_device ORDER BY date_created DESC",
		    null, HibernateSyncDao::mapDevice, -1);
	}
	
	@Override
	public void insertDevice(ChwSyncDevice device) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", device.getUuid());
		params.put("userId", device.getUserId());
		params.put("name", device.getName());
		params.put("platform", device.getPlatform());
		params.put("appVersion", device.getAppVersion());
		params.put("active", Boolean.valueOf(device.isActive()));
		params.put("dateCreated", device.getDateCreated());
		params.put("lastSeen", device.getLastSeen());
		execute("INSERT INTO chwsync_device (uuid, user_id, name, platform, app_version, active, date_created, last_seen)"
		        + " VALUES (:uuid, :userId, :name, :platform, :appVersion, :active, :dateCreated, :lastSeen)",
		    params);
	}
	
	@Override
	public void updateDevice(ChwSyncDevice device) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", device.getUuid());
		params.put("userId", device.getUserId());
		params.put("name", device.getName());
		params.put("platform", device.getPlatform());
		params.put("appVersion", device.getAppVersion());
		params.put("active", Boolean.valueOf(device.isActive()));
		params.put("lastSeen", device.getLastSeen());
		execute("UPDATE chwsync_device SET user_id = :userId, name = :name, platform = :platform,"
		        + " app_version = :appVersion, active = :active, last_seen = :lastSeen WHERE uuid = :uuid",
		    params);
	}
	
	@Override
	public void touchDevice(String uuid, Date when) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", uuid);
		params.put("lastSeen", when);
		execute("UPDATE chwsync_device SET last_seen = :lastSeen WHERE uuid = :uuid", params);
	}
	
	@Override
	public void setDeviceActive(String uuid, boolean active) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", uuid);
		params.put("active", Boolean.valueOf(active));
		execute("UPDATE chwsync_device SET active = :active WHERE uuid = :uuid", params);
	}
	
	@Override
	public void insertToken(ClientToken token) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("tokenHash", token.getTokenHash());
		params.put("userId", token.getUserId());
		params.put("deviceUuid", token.getDeviceUuid());
		params.put("dateCreated", token.getDateCreated());
		params.put("dateExpires", token.getDateExpires());
		params.put("lastUsed", token.getLastUsed());
		params.put("revoked", Boolean.valueOf(token.isRevoked()));
		execute(
		    "INSERT INTO chwsync_client_token (token_hash, user_id, device_uuid, date_created, date_expires, last_used,"
		            + " revoked) VALUES (:tokenHash, :userId, :deviceUuid, :dateCreated, :dateExpires, :lastUsed, :revoked)",
		    params);
	}
	
	@Override
	public ClientToken findTokenByHash(String tokenHash) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("tokenHash", tokenHash);
		return unique("SELECT token_id, token_hash, user_id, device_uuid, date_created, date_expires, last_used, revoked"
		        + " FROM chwsync_client_token WHERE token_hash = :tokenHash",
		    params, HibernateSyncDao::mapToken);
	}
	
	@Override
	public User findUserById(Integer userId) {
		if (userId == null) {
			return null;
		}
		// User is mapped by core, not by this module, so loading it here does not depend on any
		// mapping this module maintains - and, unlike UserService, it needs no privilege.
		return session().get(User.class, userId);
	}
	
	@Override
	public void touchToken(Integer tokenId, Date when) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("tokenId", tokenId);
		params.put("lastUsed", when);
		execute("UPDATE chwsync_client_token SET last_used = :lastUsed WHERE token_id = :tokenId", params);
	}
	
	@Override
	public void revokeToken(Integer tokenId) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("tokenId", tokenId);
		execute("UPDATE chwsync_client_token SET revoked = true WHERE token_id = :tokenId", params);
	}
	
	@Override
	public int revokeTokensForDevice(String deviceUuid) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("deviceUuid", deviceUuid);
		return execute("UPDATE chwsync_client_token SET revoked = true WHERE device_uuid = :deviceUuid", params);
	}
	
	// ---------------------------------------------------------------- mappers
	
	private static SyncChange mapChange(Object[] row) {
		SyncChange change = new SyncChange();
		change.setChangeId(asLongObject(row[0]));
		change.setEntityType(SyncEntityType.fromWireName(asString(row[1])));
		change.setEntityUuid(asString(row[2]));
		change.setOperation(ChangeOperation.fromWireName(asString(row[3])));
		change.setChangedAt(asDate(row[4]));
		change.setChangedBy(asInteger(row[5]));
		change.setOriginDeviceUuid(asString(row[6]));
		change.setClientChangeId(asString(row[7]));
		return change;
	}
	
	private static ChwSyncDevice mapDevice(Object[] row) {
		ChwSyncDevice device = new ChwSyncDevice();
		device.setDeviceId(asInteger(row[0]));
		device.setUuid(asString(row[1]));
		device.setUserId(asInteger(row[2]));
		device.setName(asString(row[3]));
		device.setPlatform(asString(row[4]));
		device.setAppVersion(asString(row[5]));
		device.setActive(asPrimitiveBoolean(row[6]));
		device.setDateCreated(asDate(row[7]));
		device.setLastSeen(asDate(row[8]));
		return device;
	}
	
	private static ClientToken mapToken(Object[] row) {
		ClientToken token = new ClientToken();
		token.setTokenId(asInteger(row[0]));
		token.setTokenHash(asString(row[1]));
		token.setUserId(asInteger(row[2]));
		token.setDeviceUuid(asString(row[3]));
		token.setDateCreated(asDate(row[4]));
		token.setDateExpires(asDate(row[5]));
		token.setLastUsed(asDate(row[6]));
		token.setRevoked(asPrimitiveBoolean(row[7]));
		return token;
	}
}
