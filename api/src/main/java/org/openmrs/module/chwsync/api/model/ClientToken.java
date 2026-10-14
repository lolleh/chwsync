/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.model;

import java.util.Date;

/**
 * A bearer token a device presents instead of a password on every sync call.
 * <p>
 * Only the SHA-256 hash of the token is stored, so a database dump does not hand out live sessions.
 * Tokens are long lived because a CHW may be away from the network for weeks; the control that
 * actually matters is revocation, and an {@link #revoked} flag administered from the server.
 */
public class ClientToken {
	
	private Integer tokenId;
	
	private String tokenHash;
	
	private Integer userId;
	
	private String deviceUuid;
	
	private Date dateCreated;
	
	private Date dateExpires;
	
	private Date lastUsed;
	
	private boolean revoked;
	
	public Integer getTokenId() {
		return tokenId;
	}
	
	public void setTokenId(Integer tokenId) {
		this.tokenId = tokenId;
	}
	
	public String getTokenHash() {
		return tokenHash;
	}
	
	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}
	
	public Integer getUserId() {
		return userId;
	}
	
	public void setUserId(Integer userId) {
		this.userId = userId;
	}
	
	public String getDeviceUuid() {
		return deviceUuid;
	}
	
	public void setDeviceUuid(String deviceUuid) {
		this.deviceUuid = deviceUuid;
	}
	
	public Date getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	public Date getDateExpires() {
		return dateExpires;
	}
	
	public void setDateExpires(Date dateExpires) {
		this.dateExpires = dateExpires;
	}
	
	public Date getLastUsed() {
		return lastUsed;
	}
	
	public void setLastUsed(Date lastUsed) {
		this.lastUsed = lastUsed;
	}
	
	public boolean isRevoked() {
		return revoked;
	}
	
	public void setRevoked(boolean revoked) {
		this.revoked = revoked;
	}
	
	public boolean isExpired(Date now) {
		return dateExpires != null && now != null && now.after(dateExpires);
	}
}
