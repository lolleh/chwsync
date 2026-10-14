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
import java.util.Map;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.dto.SyncDtos;
import org.openmrs.module.chwsync.api.sync.ChangeOperation;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;

/**
 * The sync feed: what a CHW device reads from and writes to.
 * <p>
 * Pull and push are both idempotent and both safe to retry. A device in the field will lose
 * connectivity mid-call and call again; the feed is built so that the second call is not a second
 * write. That is the whole design constraint: a sync protocol that is merely correct on a stable
 * connection is a protocol that corrupts data on a phone.
 */
public interface ChwSyncService extends OpenmrsService {
	
	/**
	 * Everything that changed strictly after {@code since}, up to a page.
	 * <p>
	 * The server re-reads a short window before {@code since} on purpose, so a record written by a
	 * transaction that was still committing when the last pull ran is not lost. Duplicates are possible
	 * at the window boundary and are harmless: clients key their stores on uuid.
	 *
	 * @param since the cursor from the previous pull, or null for a first sync
	 * @param types the record types to include, or null/empty for all of them
	 * @param limit the maximum number of changes, or null for the configured default
	 * @return the page of changes and the cursor to send back next time
	 */
	@Authorized(ChwSyncConstants.SYNC_PRIVILEGE)
	SyncDtos.PullResponse pull(Date since, List<SyncEntityType> types, Integer limit);
	
	/**
	 * Applies a batch of client changes.
	 * <p>
	 * Each change is answered individually, so one conflict does not fail the batch. Re-offering a
	 * change the server has already seen returns the original outcome rather than applying it twice.
	 *
	 * @param request the batch
	 * @param deviceUuid the device the batch came from, recorded as the origin of each change
	 * @return per-change outcomes and the cursor for the next pull
	 */
	@Authorized(ChwSyncConstants.SYNC_PRIVILEGE)
	SyncDtos.PushResponse push(SyncDtos.PushRequest request, String deviceUuid);
	
	/**
	 * Reads a single record in wire form, for the conflict response and for debugging.
	 *
	 * @param type the record type
	 * @param uuid the record uuid
	 * @return the serialized record, or null when it does not exist
	 */
	@Authorized(ChwSyncConstants.SYNC_PRIVILEGE)
	Map<String, Object> fetch(SyncEntityType type, String uuid);
	
	/**
	 * @return the largest number of changes a single push may carry
	 */
	int maxPushBatch();
	
	/**
	 * @return the configured default page size for a pull
	 */
	int defaultPageSize();
	
	/**
	 * Appends a change to the feed on behalf of a core event.
	 * <p>
	 * Deliberately not annotated with a privilege: it is called from the event listener, on a thread
	 * with no authenticated user, and a privilege check there would silently drop every facility-side
	 * edit from the feed. The listener only ever passes through uuids it has already read from core, so
	 * there is nothing here a client could use to forge a change.
	 *
	 * @param type the synced record type the event concerns
	 * @param uuid the top-level record uuid (an obs event maps to its encounter)
	 * @param operation what happened
	 * @param when the moment to record
	 */
	void recordCoreChange(SyncEntityType type, String uuid, ChangeOperation operation, Date when);
}
