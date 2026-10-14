/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
// IndexedDB is the device's copy of the register. It is the only source the UI reads from, so the app
// is fully usable with no network; the sync engine is what reconciles it with the server.
//
// Layout:
//   records  - one row per synced record, keyed [type, uuid]; dirty entries are local edits not yet
//              accepted by the server.
//   outbox   - local changes waiting to be pushed, keyed by a client change id so a retried push is
//              recognised as a replay rather than applied twice.
//   conflicts- a log of changes the server refused, so a CHW can see what happened instead of the
//              edit quietly disappearing.
//   settings - the server URL, the device token, the pull cursor, and the device uuid.

const DB_NAME = 'chwsync-field';
const DB_VERSION = 1;

let dbPromise = null;

export function open() {
	if (dbPromise) {
		return dbPromise;
	}
	dbPromise = new Promise((resolve, reject) => {
		const request = indexedDB.open(DB_NAME, DB_VERSION);
		request.onupgradeneeded = () => {
			const db = request.result;
			if (!db.objectStoreNames.contains('records')) {
				const records = db.createObjectStore('records', { keyPath: ['type', 'uuid'] });
				records.createIndex('type', 'type', { unique: false });
				records.createIndex('dirty', 'dirty', { unique: false });
			}
			if (!db.objectStoreNames.contains('outbox')) {
				const outbox = db.createObjectStore('outbox', { keyPath: 'id', autoIncrement: true });
				outbox.createIndex('type_uuid', ['type', 'uuid'], { unique: false });
			}
			if (!db.objectStoreNames.contains('conflicts')) {
				db.createObjectStore('conflicts', { keyPath: 'id', autoIncrement: true });
			}
			if (!db.objectStoreNames.contains('settings')) {
				db.createObjectStore('settings', { keyPath: 'key' });
			}
		};
		request.onsuccess = () => resolve(request.result);
		request.onerror = () => reject(request.error);
	});
	return dbPromise;
}

function tx(db, store, mode) {
	return db.transaction(store, mode).objectStore(store);
}

function promisify(request) {
	return new Promise((resolve, reject) => {
		request.onsuccess = () => resolve(request.result);
		request.onerror = () => reject(request.error);
	});
}

// ---------------------------------------------------------------- settings

export async function getSetting(key, fallback = null) {
	const db = await open();
	const row = await promisify(tx(db, 'settings', 'readonly').get(key));
	return row === undefined ? fallback : row.value;
}

export async function setSetting(key, value) {
	const db = await open();
	await promisify(tx(db, 'settings', 'readwrite').put({ key, value }));
	return value;
}

// ---------------------------------------------------------------- records

export async function getRecord(type, uuid) {
	const db = await open();
	return promisify(tx(db, 'records', 'readonly').get([type, uuid]));
}

export async function putRecord(record) {
	const db = await open();
	await promisify(tx(db, 'records', 'readwrite').put(record));
	return record;
}

export async function deleteRecord(type, uuid) {
	const db = await open();
	await promisify(tx(db, 'records', 'readwrite').delete([type, uuid]));
}

export async function listRecords(type) {
	const db = await open();
	return promisify(tx(db, 'records', 'readonly').index('type').getAll(type));
}

export async function listDirty() {
	const db = await open();
	const all = await promisify(tx(db, 'records', 'readonly').index('dirty').getAll(IDBKeyRange.only(1)));
	return all;
}

// ---------------------------------------------------------------- outbox

export async function listOutbox() {
	const db = await open();
	return promisify(tx(db, 'outbox', 'readonly').getAll());
}

async function findOutboxEntry(type, uuid) {
	const db = await open();
	const rows = await promisify(tx(db, 'outbox', 'readonly').index('type_uuid').getAll([type, uuid]));
	return rows && rows.length ? rows[0] : null;
}

export async function findOutbox(type, uuid) {
	return findOutboxEntry(type, uuid);
}

export async function addOutbox(entry) {
	const db = await open();
	const id = await promisify(tx(db, 'outbox', 'readwrite').add(entry));
	return Object.assign({ id }, entry);
}

export async function updateOutbox(entry) {
	const db = await open();
	await promisify(tx(db, 'outbox', 'readwrite').put(entry));
	return entry;
}

export async function deleteOutbox(id) {
	const db = await open();
	await promisify(tx(db, 'outbox', 'readwrite').delete(id));
}

/**
 * Queues a change, coalescing it with an unsent change for the same record so editing a patient five
 * times offline still pushes one change. A create followed by edits stays a single create; the
 * operation only escalates to an update once the record is known to the server.
 */
export async function enqueueChange(type, uuid, operation, baseVersion, payload, clientChangeId) {
	const existing = await findOutboxEntry(type, uuid);
	if (existing && existing.operation === 'CREATE') {
		// Still an unsent create: fold the edit into it, keep it a create.
		existing.payload = payload;
		existing.clientChangeId = existing.clientChangeId;
		await updateOutbox(existing);
		return existing;
	}
	if (existing && operation !== 'DELETE') {
		// Still an unsent update: replace the payload.
		existing.payload = payload;
		await updateOutbox(existing);
		return existing;
	}
	return addOutbox({
		clientChangeId: clientChangeId || (uuid + ':' + operation + ':' + Date.now()),
		type,
		uuid,
		operation,
		baseVersion,
		payload,
		createdAt: Date.now()
	});
}

// ---------------------------------------------------------------- conflicts

export async function logConflict(entry) {
	const db = await open();
	await promisify(tx(db, 'conflicts', 'readwrite').add(Object.assign({ loggedAt: Date.now() }, entry)));
}

export async function listConflicts() {
	const db = await open();
	return promisify(tx(db, 'conflicts', 'readonly').getAll());
}

export async function clearConflicts() {
	const db = await open();
	await promisify(tx(db, 'conflicts', 'readwrite').clear());
}
