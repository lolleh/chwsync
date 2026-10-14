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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.openmrs.module.chwsync.api.dao.SyncDao;
import org.openmrs.module.chwsync.api.model.ChwSyncDevice;
import org.openmrs.module.chwsync.api.model.ClientToken;
import org.openmrs.module.chwsync.api.model.SyncChange;
import org.openmrs.module.chwsync.api.sync.ChangeOperation;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;

/**
 * Token verification is the security boundary for every sync call, and it is pure enough to test
 * without a database. These tests pin the two properties that matter: only the holder of the
 * plaintext can match a stored hash, and revoked or expired tokens never resolve.
 */
public class ChwSyncDeviceServiceImplTest {
	
	@Test
	public void verifyTokenShouldMatchAPreviouslyStoredHash() {
		FakeSyncDao dao = new FakeSyncDao();
		dao.tokenByHash = sha256Hex("the-secret-token");
		ClientToken stored = token(dao.tokenByHash, 7, future());
		dao.tokens.add(stored);
		
		ChwSyncDeviceServiceImpl service = service(dao);
		assertSame(stored, service.verifyToken("the-secret-token"));
	}
	
	@Test
	public void verifyTokenShouldRejectTheWrongPlaintext() {
		FakeSyncDao dao = new FakeSyncDao();
		ClientToken stored = token(sha256Hex("the-secret-token"), 7, future());
		dao.tokenByHash = stored.getTokenHash();
		dao.tokens.add(stored);
		
		ChwSyncDeviceServiceImpl service = service(dao);
		assertNull(service.verifyToken("a-different-token"));
	}
	
	@Test
	public void verifyTokenShouldRejectBlankInput() {
		ChwSyncDeviceServiceImpl service = service(new FakeSyncDao());
		assertNull(service.verifyToken(null));
		assertNull(service.verifyToken("   "));
	}
	
	@Test
	public void verifyTokenShouldRejectRevokedTokens() {
		FakeSyncDao dao = new FakeSyncDao();
		ClientToken stored = token(sha256Hex("revoked-token"), 7, future());
		stored.setRevoked(true);
		dao.tokenByHash = stored.getTokenHash();
		dao.tokens.add(stored);
		
		assertNull(service(dao).verifyToken("revoked-token"));
	}
	
	@Test
	public void verifyTokenShouldRejectExpiredTokens() {
		FakeSyncDao dao = new FakeSyncDao();
		ClientToken stored = token(sha256Hex("old-token"), 7, new Date(System.currentTimeMillis() - 60_000L));
		dao.tokenByHash = stored.getTokenHash();
		dao.tokens.add(stored);
		
		assertNull(service(dao).verifyToken("old-token"));
	}
	
	@Test
	public void revokeDeviceShouldRevokeEveryTokenForThatDevice() {
		FakeSyncDao dao = new FakeSyncDao();
		ClientToken first = token(sha256Hex("a"), 1, future());
		first.setDeviceUuid("device-1");
		ClientToken second = token(sha256Hex("b"), 1, future());
		second.setDeviceUuid("device-1");
		ClientToken other = token(sha256Hex("c"), 2, future());
		other.setDeviceUuid("device-2");
		dao.tokens.add(first);
		dao.tokens.add(second);
		dao.tokens.add(other);
		
		int revoked = service(dao).revokeDevice("device-1");
		assertEquals(2, revoked);
		assertTrue(first.isRevoked());
		assertTrue(second.isRevoked());
		assertTrue(!other.isRevoked());
	}
	
	// ---------------------------------------------------------------- helpers
	
	private static ChwSyncDeviceServiceImpl service(FakeSyncDao dao) {
		ChwSyncDeviceServiceImpl service = new ChwSyncDeviceServiceImpl();
		service.setDao(dao);
		return service;
	}
	
	private static ClientToken token(String hash, Integer userId, Date expires) {
		ClientToken token = new ClientToken();
		token.setTokenHash(hash);
		token.setUserId(userId);
		token.setDateExpires(expires);
		token.setRevoked(false);
		return token;
	}
	
	private static Date future() {
		return new Date(System.currentTimeMillis() + 3_600_000L);
	}
	
	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hashed.length * 2);
			for (byte b : hashed) {
				hex.append(Character.forDigit((b >> 4) & 0xF, 16));
				hex.append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	/** An in-memory stand-in for the SQL DAO, enough for the token paths. */
	private static class FakeSyncDao implements SyncDao {
		
		private final List<ClientToken> tokens = new ArrayList<ClientToken>();
		
		private String tokenByHash;
		
		@Override
		public ClientToken findTokenByHash(String hash) {
			if (hash == null) {
				return null;
			}
			for (ClientToken token : tokens) {
				if (hash.equals(token.getTokenHash())) {
					return token;
				}
			}
			return null;
		}
		
		@Override
		public int revokeTokensForDevice(String deviceUuid) {
			int count = 0;
			for (ClientToken token : tokens) {
				if (deviceUuid != null && deviceUuid.equals(token.getDeviceUuid())) {
					token.setRevoked(true);
					count++;
				}
			}
			return count;
		}
		
		@Override
		public void appendChange(SyncEntityType type, String entityUuid, ChangeOperation operation, Date changedAt,
		        Integer changedBy, String deviceUuid, String clientChangeId) {
		}
		
		@Override
		public List<SyncChange> findChanges(Date fromExclusive, Date toInclusive, Collection<SyncEntityType> types,
		        int limit) {
			return new ArrayList<SyncChange>();
		}
		
		@Override
		public String findClientChangeResult(String clientChangeId) {
			return null;
		}
		
		@Override
		public void recordClientChange(String clientChangeId, String deviceUuid, String resultJson, Date processedAt) {
		}
		
		@Override
		public ChwSyncDevice findDeviceByUuid(String uuid) {
			return null;
		}
		
		@Override
		public List<ChwSyncDevice> findDevicesByUser(Integer userId) {
			return new ArrayList<ChwSyncDevice>();
		}
		
		@Override
		public List<ChwSyncDevice> listDevices() {
			return new ArrayList<ChwSyncDevice>();
		}
		
		@Override
		public void insertDevice(ChwSyncDevice device) {
		}
		
		@Override
		public void updateDevice(ChwSyncDevice device) {
		}
		
		@Override
		public void touchDevice(String uuid, Date when) {
		}
		
		@Override
		public void setDeviceActive(String uuid, boolean active) {
		}
		
		@Override
		public org.openmrs.User findUserById(Integer userId) {
			return null;
		}
		
		@Override
		public void insertToken(ClientToken token) {
			tokens.add(token);
		}
		
		@Override
		public void touchToken(Integer tokenId, Date when) {
		}
		
		@Override
		public void revokeToken(Integer tokenId) {
		}
	}
}
