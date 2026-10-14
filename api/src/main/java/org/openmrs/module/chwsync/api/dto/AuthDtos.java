/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Login and device payloads. The device sends its identity on login so the server can register it
 * in the same call that hands back a token, rather than in a second one that a flaky connection can
 * drop.
 */
public final class AuthDtos {
	
	private AuthDtos() {
	}
	
	public static class LoginRequest {
		
		private String username;
		
		private String password;
		
		private String deviceUuid;
		
		private String deviceName;
		
		private String platform;
		
		private String appVersion;
		
		public String getUsername() {
			return username;
		}
		
		public void setUsername(String username) {
			this.username = username;
		}
		
		public String getPassword() {
			return password;
		}
		
		public void setPassword(String password) {
			this.password = password;
		}
		
		public String getDeviceUuid() {
			return deviceUuid;
		}
		
		public void setDeviceUuid(String deviceUuid) {
			this.deviceUuid = deviceUuid;
		}
		
		public String getDeviceName() {
			return deviceName;
		}
		
		public void setDeviceName(String deviceName) {
			this.deviceName = deviceName;
		}
		
		public String getPlatform() {
			return platform;
		}
		
		public void setPlatform(String platform) {
			this.platform = platform;
		}
		
		public String getAppVersion() {
			return appVersion;
		}
		
		public void setAppVersion(String appVersion) {
			this.appVersion = appVersion;
		}
	}
	
	public static class LoginResponse {
		
		private String token;
		
		private String deviceUuid;
		
		private String userUuid;
		
		private String username;
		
		private String displayName;
		
		private List<String> privileges = new ArrayList<String>();
		
		private String expiresAt;
		
		private String serverTime;
		
		public String getToken() {
			return token;
		}
		
		public void setToken(String token) {
			this.token = token;
		}
		
		public String getDeviceUuid() {
			return deviceUuid;
		}
		
		public void setDeviceUuid(String deviceUuid) {
			this.deviceUuid = deviceUuid;
		}
		
		public String getUserUuid() {
			return userUuid;
		}
		
		public void setUserUuid(String userUuid) {
			this.userUuid = userUuid;
		}
		
		public String getUsername() {
			return username;
		}
		
		public void setUsername(String username) {
			this.username = username;
		}
		
		public String getDisplayName() {
			return displayName;
		}
		
		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}
		
		public List<String> getPrivileges() {
			return privileges;
		}
		
		public void setPrivileges(List<String> privileges) {
			this.privileges = privileges;
		}
		
		public String getExpiresAt() {
			return expiresAt;
		}
		
		public void setExpiresAt(String expiresAt) {
			this.expiresAt = expiresAt;
		}
		
		public String getServerTime() {
			return serverTime;
		}
		
		public void setServerTime(String serverTime) {
			this.serverTime = serverTime;
		}
	}
	
	public static class DeviceView {
		
		private String uuid;
		
		private Integer userId;
		
		private String username;
		
		private String name;
		
		private String platform;
		
		private String appVersion;
		
		private boolean active;
		
		private String dateCreated;
		
		private String lastSeen;
		
		public String getUuid() {
			return uuid;
		}
		
		public void setUuid(String uuid) {
			this.uuid = uuid;
		}
		
		public Integer getUserId() {
			return userId;
		}
		
		public void setUserId(Integer userId) {
			this.userId = userId;
		}
		
		public String getUsername() {
			return username;
		}
		
		public void setUsername(String username) {
			this.username = username;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getPlatform() {
			return platform;
		}
		
		public void setPlatform(String platform) {
			this.platform = platform;
		}
		
		public String getAppVersion() {
			return appVersion;
		}
		
		public void setAppVersion(String appVersion) {
			this.appVersion = appVersion;
		}
		
		public boolean isActive() {
			return active;
		}
		
		public void setActive(boolean active) {
			this.active = active;
		}
		
		public String getDateCreated() {
			return dateCreated;
		}
		
		public void setDateCreated(String dateCreated) {
			this.dateCreated = dateCreated;
		}
		
		public String getLastSeen() {
			return lastSeen;
		}
		
		public void setLastSeen(String lastSeen) {
			this.lastSeen = lastSeen;
		}
	}
}
