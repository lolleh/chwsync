/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.dao.SyncDao;
import org.openmrs.module.chwsync.api.model.ChwSyncDevice;
import org.openmrs.module.chwsync.api.model.ClientToken;
import org.openmrs.module.chwsync.api.service.ChwSyncDeviceService;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ChwSyncDeviceService}.
 */
@Transactional
public class ChwSyncDeviceServiceImpl extends BaseOpenmrsService implements ChwSyncDeviceService {
	
	private static final SecureRandom RANDOM = new SecureRandom();
	
	private SyncDao dao;
	
	public void setDao(SyncDao dao) {
		this.dao = dao;
	}
	
	@Override
	public ChwSyncDevice register(String uuid, String name, String platform, String appVersion, Integer userId, Date now) {
		ChwSyncDevice device = StringUtils.isBlank(uuid) ? null : dao.findDeviceByUuid(uuid.trim());
		if (device == null) {
			device = new ChwSyncDevice();
			device.setUuid(StringUtils.isBlank(uuid) ? UUID.randomUUID().toString() : uuid.trim());
			device.setUserId(userId);
			device.setName(name);
			device.setPlatform(platform);
			device.setAppVersion(appVersion);
			device.setActive(true);
			device.setDateCreated(now);
			device.setLastSeen(now);
			dao.insertDevice(device);
		} else {
			device.setUserId(userId);
			if (StringUtils.isNotBlank(name)) {
				device.setName(name);
			}
			if (StringUtils.isNotBlank(platform)) {
				device.setPlatform(platform);
			}
			if (StringUtils.isNotBlank(appVersion)) {
				device.setAppVersion(appVersion);
			}
			device.setLastSeen(now);
			dao.updateDevice(device);
		}
		return device;
	}
	
	@Override
	public ChwSyncDevice getDevice(String uuid) {
		return dao.findDeviceByUuid(uuid);
	}
	
	@Override
	public List<ChwSyncDevice> listDevices() {
		return dao.listDevices();
	}
	
	@Override
	public void setActive(String uuid, boolean active) {
		dao.setDeviceActive(uuid, active);
	}
	
	@Override
	public String issueToken(Integer userId, String deviceUuid, Date now) {
		String token = newToken();
		ClientToken record = new ClientToken();
		record.setTokenHash(hash(token));
		record.setUserId(userId);
		record.setDeviceUuid(deviceUuid);
		record.setDateCreated(now);
		record.setDateExpires(new Date(now.getTime() + tokenTtlHours() * 3600_000L));
		record.setLastUsed(now);
		record.setRevoked(false);
		dao.insertToken(record);
		return token;
	}
	
	@Override
	public ClientToken verifyToken(String token) {
		if (StringUtils.isBlank(token)) {
			return null;
		}
		ClientToken record = dao.findTokenByHash(hash(token.trim()));
		if (record == null || record.isRevoked() || record.isExpired(new Date())) {
			return null;
		}
		return record;
	}
	
	@Override
	public User findUserById(Integer userId) {
		return dao.findUserById(userId);
	}
	
	@Override
	public void touchToken(ClientToken token, Date now) {
		if (token != null && token.getTokenId() != null) {
			dao.touchToken(token.getTokenId(), now);
		}
	}
	
	@Override
	public void revokeToken(String token) {
		if (StringUtils.isBlank(token)) {
			return;
		}
		ClientToken record = dao.findTokenByHash(hash(token.trim()));
		if (record != null) {
			dao.revokeToken(record.getTokenId());
		}
	}
	
	@Override
	public int revokeDevice(String deviceUuid) {
		return dao.revokeTokensForDevice(deviceUuid);
	}
	
	// ---------------------------------------------------------------- helpers
	
	private int tokenTtlHours() {
		String configured = Context.getAdministrationService().getGlobalProperty(ChwSyncConstants.GP_TOKEN_TTL_HOURS);
		if (StringUtils.isNotBlank(configured)) {
			try {
				return Integer.parseInt(configured.trim());
			}
			catch (NumberFormatException e) {
				// fall through to the default rather than refuse to issue a token
			}
		}
		return ChwSyncConstants.DEFAULT_TOKEN_TTL_HOURS;
	}
	
	private static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
	
	private static String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hashed.length * 2);
			for (byte value : hashed) {
				hex.append(Character.forDigit((value >> 4) & 0xF, 16));
				hex.append(Character.forDigit(value & 0xF, 16));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required but unavailable", e);
		}
	}
}
