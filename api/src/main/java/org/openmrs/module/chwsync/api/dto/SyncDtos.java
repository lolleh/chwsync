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
import java.util.Map;

/**
 * The shapes that cross the sync wire. Grouped as nested types because they only make sense
 * together: a pull answers in changes, a push answers in results, and both agree on what a change
 * is.
 */
public final class SyncDtos {
	
	private SyncDtos() {
	}
	
	/**
	 * One record in a pull. {@code data} is the serialized record, or null for a delete - a client that
	 * sees a delete for a uuid it never stored simply does nothing.
	 */
	public static class Change {
		
		private long revision;
		
		private String entityType;
		
		private String uuid;
		
		private String operation;
		
		private Long version;
		
		private boolean deleted;
		
		/**
		 * An ISO-8601 string, not a {@link java.util.Date}: the wire carries every timestamp as the same
		 * string format so a client never has to guess how the server's Jackson is configured. A raw Date
		 * would serialize as an epoch number under the default settings and break clients that expect text.
		 */
		private String changedAt;
		
		private Map<String, Object> data;
		
		public long getRevision() {
			return revision;
		}
		
		public void setRevision(long revision) {
			this.revision = revision;
		}
		
		public String getEntityType() {
			return entityType;
		}
		
		public void setEntityType(String entityType) {
			this.entityType = entityType;
		}
		
		public String getUuid() {
			return uuid;
		}
		
		public void setUuid(String uuid) {
			this.uuid = uuid;
		}
		
		public String getOperation() {
			return operation;
		}
		
		public void setOperation(String operation) {
			this.operation = operation;
		}
		
		public Long getVersion() {
			return version;
		}
		
		public void setVersion(Long version) {
			this.version = version;
		}
		
		public boolean isDeleted() {
			return deleted;
		}
		
		public void setDeleted(boolean deleted) {
			this.deleted = deleted;
		}
		
		public String getChangedAt() {
			return changedAt;
		}
		
		public void setChangedAt(String changedAt) {
			this.changedAt = changedAt;
		}
		
		public Map<String, Object> getData() {
			return data;
		}
		
		public void setData(Map<String, Object> data) {
			this.data = data;
		}
	}
	
	/** The answer to a pull: everything that changed after the client's cursor, and the next cursor. */
	public static class PullResponse {
		
		private String cursor;
		
		private boolean hasMore;
		
		private List<Change> changes = new ArrayList<Change>();
		
		public String getCursor() {
			return cursor;
		}
		
		public void setCursor(String cursor) {
			this.cursor = cursor;
		}
		
		public boolean isHasMore() {
			return hasMore;
		}
		
		public void setHasMore(boolean hasMore) {
			this.hasMore = hasMore;
		}
		
		public List<Change> getChanges() {
			return changes;
		}
		
		public void setChanges(List<Change> changes) {
			this.changes = changes;
		}
	}
	
	/** One client change offered to the server. */
	public static class PushItem {
		
		private String clientChangeId;
		
		private String entityType;
		
		private String uuid;
		
		private String operation;
		
		/** The {@code version} the client last saw, or null for a create. Drives conflict detection. */
		private Long baseVersion;
		
		private Map<String, Object> payload;
		
		public String getClientChangeId() {
			return clientChangeId;
		}
		
		public void setClientChangeId(String clientChangeId) {
			this.clientChangeId = clientChangeId;
		}
		
		public String getEntityType() {
			return entityType;
		}
		
		public void setEntityType(String entityType) {
			this.entityType = entityType;
		}
		
		public String getUuid() {
			return uuid;
		}
		
		public void setUuid(String uuid) {
			this.uuid = uuid;
		}
		
		public String getOperation() {
			return operation;
		}
		
		public void setOperation(String operation) {
			this.operation = operation;
		}
		
		public Long getBaseVersion() {
			return baseVersion;
		}
		
		public void setBaseVersion(Long baseVersion) {
			this.baseVersion = baseVersion;
		}
		
		public Map<String, Object> getPayload() {
			return payload;
		}
		
		public void setPayload(Map<String, Object> payload) {
			this.payload = payload;
		}
	}
	
	public static class PushRequest {
		
		private String deviceUuid;
		
		private List<PushItem> changes = new ArrayList<PushItem>();
		
		public String getDeviceUuid() {
			return deviceUuid;
		}
		
		public void setDeviceUuid(String deviceUuid) {
			this.deviceUuid = deviceUuid;
		}
		
		public List<PushItem> getChanges() {
			return changes;
		}
		
		public void setChanges(List<PushItem> changes) {
			this.changes = changes;
		}
	}
	
	/**
	 * The outcome of one offered change. {@code status} is one of APPLIED, CONFLICT, REJECTED or
	 * REPLAYED. On CONFLICT the server's current record is returned in {@code data} so the client can
	 * decide how to reconcile without a second round trip.
	 */
	public static class PushResult {
		
		public static final String STATUS_APPLIED = "APPLIED";
		
		public static final String STATUS_CONFLICT = "CONFLICT";
		
		public static final String STATUS_REJECTED = "REJECTED";
		
		public static final String STATUS_REPLAYED = "REPLAYED";
		
		private String clientChangeId;
		
		private String entityType;
		
		private String uuid;
		
		private String status;
		
		private Long serverVersion;
		
		private String message;
		
		private Map<String, Object> data;
		
		public String getClientChangeId() {
			return clientChangeId;
		}
		
		public void setClientChangeId(String clientChangeId) {
			this.clientChangeId = clientChangeId;
		}
		
		public String getEntityType() {
			return entityType;
		}
		
		public void setEntityType(String entityType) {
			this.entityType = entityType;
		}
		
		public String getUuid() {
			return uuid;
		}
		
		public void setUuid(String uuid) {
			this.uuid = uuid;
		}
		
		public String getStatus() {
			return status;
		}
		
		public void setStatus(String status) {
			this.status = status;
		}
		
		public Long getServerVersion() {
			return serverVersion;
		}
		
		public void setServerVersion(Long serverVersion) {
			this.serverVersion = serverVersion;
		}
		
		public String getMessage() {
			return message;
		}
		
		public void setMessage(String message) {
			this.message = message;
		}
		
		public Map<String, Object> getData() {
			return data;
		}
		
		public void setData(Map<String, Object> data) {
			this.data = data;
		}
	}
	
	public static class PushResponse {
		
		private String cursor;
		
		private int applied;
		
		private int conflicts;
		
		private int rejected;
		
		private List<PushResult> results = new ArrayList<PushResult>();
		
		public String getCursor() {
			return cursor;
		}
		
		public void setCursor(String cursor) {
			this.cursor = cursor;
		}
		
		public int getApplied() {
			return applied;
		}
		
		public void setApplied(int applied) {
			this.applied = applied;
		}
		
		public int getConflicts() {
			return conflicts;
		}
		
		public void setConflicts(int conflicts) {
			this.conflicts = conflicts;
		}
		
		public int getRejected() {
			return rejected;
		}
		
		public void setRejected(int rejected) {
			this.rejected = rejected;
		}
		
		public List<PushResult> getResults() {
			return results;
		}
		
		public void setResults(List<PushResult> results) {
			this.results = results;
		}
	}
}
