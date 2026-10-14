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

import org.openmrs.module.chwsync.api.sync.ChangeOperation;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;

/**
 * One entry in the append-only log of everything that has changed, anywhere.
 * <p>
 * The log is the sync feed. Its primary key doubles as the revision cursor: because it only ever
 * grows and is written in a single transaction per change, a client that remembers the highest
 * revision it has seen can ask for everything above it and be sure it missed nothing.
 * <p>
 * The log deliberately stores no payload. A change is three facts - what type, which uuid, and what
 * happened - and the record itself is read fresh when a client pulls. Storing a snapshot here would
 * duplicate every clinical row and, worse, would hand a client a stale copy if the row was edited
 * twice between two pulls.
 */
public class SyncChange {
	
	private Long changeId;
	
	private SyncEntityType entityType;
	
	private String entityUuid;
	
	private ChangeOperation operation;
	
	private Date changedAt;
	
	private Integer changedBy;
	
	/** The device that originated the change, when it came in over a push rather than a core event. */
	private String originDeviceUuid;
	
	/** The client's own id for the change, kept for auditing and for spotting replay attempts. */
	private String clientChangeId;
	
	public Long getChangeId() {
		return changeId;
	}
	
	public void setChangeId(Long changeId) {
		this.changeId = changeId;
	}
	
	/** The revision cursor: this entry's {@link #changeId}. */
	public long getRevision() {
		return changeId == null ? 0L : changeId.longValue();
	}
	
	public SyncEntityType getEntityType() {
		return entityType;
	}
	
	public void setEntityType(SyncEntityType entityType) {
		this.entityType = entityType;
	}
	
	public String getEntityUuid() {
		return entityUuid;
	}
	
	public void setEntityUuid(String entityUuid) {
		this.entityUuid = entityUuid;
	}
	
	public ChangeOperation getOperation() {
		return operation;
	}
	
	public void setOperation(ChangeOperation operation) {
		this.operation = operation;
	}
	
	public Date getChangedAt() {
		return changedAt;
	}
	
	public void setChangedAt(Date changedAt) {
		this.changedAt = changedAt;
	}
	
	public Integer getChangedBy() {
		return changedBy;
	}
	
	public void setChangedBy(Integer changedBy) {
		this.changedBy = changedBy;
	}
	
	public String getOriginDeviceUuid() {
		return originDeviceUuid;
	}
	
	public void setOriginDeviceUuid(String originDeviceUuid) {
		this.originDeviceUuid = originDeviceUuid;
	}
	
	public String getClientChangeId() {
		return clientChangeId;
	}
	
	public void setClientChangeId(String clientChangeId) {
		this.clientChangeId = clientChangeId;
	}
}
