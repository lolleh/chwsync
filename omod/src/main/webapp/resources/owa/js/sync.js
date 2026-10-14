/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
// The sync engine: pull the server's changes into IndexedDB, push the device's changes back, and
// reconcile the two. It is deliberately the only code that talks to both the network and the store,
// so the rest of the app can pretend the register is always local.

import * as api from './api.js';
import * as db from './db.js';

const ALL_TYPES = ['patient', 'visit', 'encounter', 'household', 'appointment', 'referral'];

function isDirtyFlag(record) {
	return record && (record.dirty === 1 || record.dirty === true);
}

/** Stores one pulled change, leaving a locally edited record alone so a pull cannot clobber a CHW's
 *  unsent work. The push path is what resolves that overlap. */
async function applyPullChange(change) {
	const existing = await db.getRecord(change.entityType, change.uuid);
	if (existing && isDirtyFlag(existing)) {
		return false;
	}
	if (change.deleted) {
		if (existing) {
			await db.deleteRecord(change.entityType, change.uuid);
		}
		return true;
	}
	await db.putRecord({
		type: change.entityType,
		uuid: change.uuid,
		version: change.version,
		data: change.data,
		deleted: 0,
		dirty: 0,
		changedAt: change.changedAt || null
	});
	return true;
}

/**
 * Pulls every page of changes after the stored cursor.
 *
 * @param {function} onProgress called with {changes, pages} as pages arrive
 * @param {number} limit page size requested from the server
 * @returns {Promise<{changes:number, pages:number}>}
 */
export async function pull(onProgress, limit) {
	let since = await db.getSetting('cursor', null);
	let changes = 0;
	let pages = 0;

	for (;;) {
		const page = await api.pull(since, ALL_TYPES, limit);
		const list = (page && page.changes) || [];
		for (const change of list) {
			if (await applyPullChange(change)) {
				changes += 1;
			}
		}
		pages += 1;
		since = page ? page.cursor : since;
		if (since) {
			await db.setSetting('cursor', since);
		}
		if (onProgress) {
			onProgress({ changes, pages });
		}
		if (!page || !page.hasMore) {
			break;
		}
	}
	return { changes, pages };
}

/** Replaces a local record with the server's version after a conflict, so the device holds the
 *  authoritative copy and the CHW can re-apply their intent deliberately. */
async function acceptServerVersion(entry, data, serverVersion) {
	if (entry.operation === 'DELETE') {
		await db.putRecord({
			type: entry.type,
			uuid: entry.uuid,
			version: serverVersion,
			data: data,
			deleted: 1,
			dirty: 0
		});
		return;
	}
	await db.putRecord({
		type: entry.type,
		uuid: entry.uuid,
		version: serverVersion,
		data: data,
		deleted: 0,
		dirty: 0
	});
}

/**
 * Pushes every queued change in one batch.
 *
 * @returns {Promise<{pushed:number, applied:number, conflicts:number, rejected:number}>}
 */
export async function push() {
	const outbox = await db.listOutbox();
	if (!outbox.length) {
		return { pushed: 0, applied: 0, conflicts: 0, rejected: 0 };
	}

	const deviceUuid = await db.getSetting('deviceUuid', null);
	const changes = outbox.map((entry) => ({
		clientChangeId: entry.clientChangeId,
		entityType: entry.type,
		uuid: entry.uuid,
		operation: entry.operation,
		baseVersion: entry.baseVersion,
		payload: entry.payload
	}));

	const response = await api.push(deviceUuid, changes);
	const results = (response && response.results) || [];
	const byClientId = new Map(results.map((r) => [r.clientChangeId, r]));

	let applied = 0;
	let conflicts = 0;
	let rejected = 0;

	for (const entry of outbox) {
		const result = byClientId.get(entry.clientChangeId);
		if (!result) {
			// The server did not answer this change; leave it queued and try again next time.
			continue;
		}

		if (result.status === 'APPLIED' || result.status === 'REPLAYED') {
			applied += 1;
			await db.deleteOutbox(entry.id);
			if (entry.operation === 'DELETE') {
				await db.deleteRecord(entry.type, entry.uuid);
			} else {
				await db.putRecord({
					type: entry.type,
					uuid: entry.uuid,
					version: result.serverVersion,
					data: entry.payload,
					deleted: 0,
					dirty: 0
				});
			}
		} else if (result.status === 'CONFLICT') {
			conflicts += 1;
			await db.deleteOutbox(entry.id);
			await acceptServerVersion(entry, result.data, result.serverVersion);
			await db.logConflict({
				type: entry.type,
				uuid: entry.uuid,
				operation: entry.operation,
				message: result.message || 'The server had a newer copy.',
				serverVersion: result.serverVersion
			});
		} else {
			rejected += 1;
			await db.deleteOutbox(entry.id);
			if (entry.operation === 'CREATE') {
				// It never existed on the server, so there is nothing to keep a stale copy of.
				await db.deleteRecord(entry.type, entry.uuid);
			} else {
				await db.putRecord({
					type: entry.type,
					uuid: entry.uuid,
					version: entry.baseVersion,
					data: entry.payload,
					deleted: entry.operation === 'DELETE' ? 1 : 0,
					dirty: 0
				});
			}
			await db.logConflict({
				type: entry.type,
				uuid: entry.uuid,
				operation: entry.operation,
				message: result.message || 'The server rejected this change.',
				serverVersion: result.serverVersion
			});
		}
	}

	if (response && response.cursor) {
		await db.setSetting('cursor', response.cursor);
	}
	return { pushed: outbox.length, applied, conflicts, rejected };
}

/** A full round trip, in the order that avoids pointless conflicts: push local work first, then pull
 *  everything, so the pull already reflects what the device just wrote. */
export async function syncNow(onProgress) {
	const pushResult = await push();
	const pullResult = await pull(onProgress);
	return Object.assign({}, pushResult, { pulled: pullResult.changes });
}

export async function pendingCount() {
	const outbox = await db.listOutbox();
	return outbox.length;
}

export { ALL_TYPES };
