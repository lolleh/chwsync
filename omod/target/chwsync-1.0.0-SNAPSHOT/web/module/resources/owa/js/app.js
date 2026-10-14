/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
// The application shell and all views. It is deliberately plain: no framework, no build step, so the
// same files that are served are the ones a browser in the field runs offline.

import * as api from './api.js';
import * as db from './db.js';
import * as sync from './sync.js';
import { escapeHtml, patientName, patientIdentifier, formatDate, formatDateTime, toWireDate, toWireDateTime, uuid } from './format.js';

const state = {
	tab: 'patients',
	reference: null,
	patientSearch: '',
	currentPatient: null,
	online: navigator.onLine
};

// ---------------------------------------------------------------- tiny DOM helpers

function el(html) {
	const template = document.createElement('template');
	template.innerHTML = html.trim();
	return template.content.firstElementChild;
}

function view() {
	return document.getElementById('view');
}

function toast(message, isError) {
	const root = document.getElementById('toast-root');
	root.innerHTML = '';
	const node = el('<div class="toast' + (isError ? ' error' : '') + '"></div>');
	node.textContent = message;
	root.appendChild(node);
	window.clearTimeout(toast._timer);
	toast._timer = window.setTimeout(() => { root.innerHTML = ''; }, isError ? 5000 : 3000);
}

function setStatus() {
	const status = document.getElementById('status');
	const pending = state.pendingCount || 0;
	if (!state.online) {
		status.textContent = pending ? 'Offline · ' + pending + ' to sync' : 'Offline';
		status.className = 'status offline';
	} else if (pending) {
		status.textContent = pending + ' pending';
		status.className = 'status pending';
	} else {
		status.textContent = 'Up to date';
		status.className = 'status';
	}
}

async function refreshPending() {
	try {
		state.pendingCount = await sync.pendingCount();
	} catch (e) {
		state.pendingCount = 0;
	}
	setStatus();
}

// ---------------------------------------------------------------- modal

function closeModal() {
	document.getElementById('modal-root').innerHTML = '';
}

function showModal(title, bodyHtml, options = {}) {
	const root = document.getElementById('modal-root');
	root.innerHTML = '';
	const modal = el(
		'<div class="modal-backdrop"><div class="modal">'
		+ '<h2>' + escapeHtml(title) + '</h2>'
		+ '<form id="modal-form">' + bodyHtml
		+ '<div class="actions">'
		+ '<button type="button" class="ghost" id="modal-cancel">Close</button>'
		+ '<button type="submit">' + escapeHtml(options.submitLabel || 'Save') + '</button>'
		+ '</div></form></div></div>'
	);
	root.appendChild(modal);
	const form = modal.querySelector('#modal-form');
	modal.querySelector('#modal-cancel').addEventListener('click', closeModal);
	modal.addEventListener('click', (event) => {
		if (event.target === modal) {
			closeModal();
		}
	});
	form.addEventListener('submit', async (event) => {
		event.preventDefault();
		try {
			const keepOpen = await options.onSubmit(form);
			if (keepOpen !== false) {
				closeModal();
			}
		} catch (error) {
			toast(error.message || String(error), true);
		}
	});
	return modal;
}

function confirmDialog(message, onYes) {
	showModal('Please confirm', '<p>' + escapeHtml(message) + '</p>', {
		submitLabel: 'Confirm',
		onSubmit: onYes
	});
}

// ---------------------------------------------------------------- persistence

async function persistRecord(type, recordUuid, data) {
	const existing = await db.getRecord(type, recordUuid);
	const baseVersion = existing ? existing.version : null;
	const operation = existing && existing.version !== null && existing.version !== undefined ? 'UPDATE' : 'CREATE';
	await db.putRecord({ type, uuid: recordUuid, version: baseVersion, data, deleted: 0, dirty: 1, changedAt: new Date().toISOString() });
	await db.enqueueChange(type, recordUuid, operation, baseVersion, data);
	await refreshPending();
}

async function voidRecord(type, recordUuid) {
	const existing = await db.getRecord(type, recordUuid);
	if (!existing) {
		return;
	}
	const pending = await db.findOutbox(type, recordUuid);
	if (pending && pending.operation === 'CREATE') {
		// Never reached the server; removing it locally is the whole story.
		await db.deleteOutbox(pending.id);
		await db.deleteRecord(type, recordUuid);
	} else {
		await db.deleteRecord(type, recordUuid);
		await db.enqueueChange(type, recordUuid, 'DELETE', existing.version, null);
	}
	await refreshPending();
}

// ---------------------------------------------------------------- reference data

async function loadReference(force) {
	if (state.reference && !force) {
		return state.reference;
	}
	const cached = await db.getSetting('reference', null);
	state.reference = cached;
	if (state.online) {
		try {
			const fresh = await api.reference();
			state.reference = fresh;
			await db.setSetting('reference', fresh);
		} catch (e) {
			// Offline or not permitted: fall back to whatever was cached.
		}
	}
	return state.reference;
}

function optionsFrom(items, selected) {
	const list = items || [];
	if (!list.length) {
		return '<option value="">(none available)</option>';
	}
	return list.map((item) => {
		const isSelected = selected && item.uuid === selected ? ' selected' : '';
		return '<option value="' + escapeHtml(item.uuid) + '"' + isSelected + '>' + escapeHtml(item.name) + '</option>';
	}).join('');
}

// ---------------------------------------------------------------- login

function renderLogin() {
	document.getElementById('tabs').hidden = true;
	const node = view();
	node.innerHTML = '';
	node.appendChild(el(
		'<div class="card">'
		+ '<h2>Sign in</h2>'
		+ '<p class="sub">Your password is used once, to get a token for this device. It is not stored on the phone.</p>'
		+ '<form id="login-form">'
		+ '<label for="serverUrl">Server</label>'
		+ '<input id="serverUrl" name="serverUrl" value="' + escapeHtml(api.baseUrl()) + '">'
		+ '<label for="username">Username</label>'
		+ '<input id="username" name="username" autocomplete="username" autocapitalize="none">'
		+ '<label for="password">Password</label>'
		+ '<input id="password" name="password" type="password" autocomplete="current-password">'
		+ '<label for="deviceName">Device name</label>'
		+ '<input id="deviceName" name="deviceName" value="' + escapeHtml(defaultDeviceName()) + '">'
		+ '<div class="actions" style="margin-top:16px"><button type="submit">Sign in</button></div>'
		+ '</form></div>'
	));
	const form = node.querySelector('#login-form');
	form.addEventListener('submit', async (event) => {
		event.preventDefault();
		const submit = form.querySelector('button[type="submit"]');
		submit.disabled = true;
		try {
			const baseUrl = form.serverUrl.value.trim();
			api.configure({ baseUrl });
			const deviceUuid = await db.getSetting('deviceUuid', null) || uuid();
			await db.setSetting('deviceUuid', deviceUuid);
			const response = await api.login({
				username: form.username.value.trim(),
				password: form.password.value,
				deviceUuid,
				deviceName: form.deviceName.value.trim(),
				platform: 'web',
				appVersion: '1.0.0'
			});
			api.setToken(response.token);
			await db.setSetting('token', response.token);
			await db.setSetting('user', {
				username: response.username,
				displayName: response.displayName,
				userUuid: response.userUuid,
				expiresAt: response.expiresAt
			});
			await db.setSetting('baseUrl', baseUrl);
			toast('Signed in as ' + response.displayName);
			await bootAfterLogin();
		} catch (error) {
			toast(error.message || 'Sign in failed', true);
		} finally {
			submit.disabled = false;
		}
	});
}

function defaultDeviceName() {
	const platform = navigator.platform || 'device';
	return 'CHW ' + platform;
}

// ---------------------------------------------------------------- boot / navigation

async function bootAfterLogin() {
	document.getElementById('tabs').hidden = false;
	await loadReference(false);
	state.tab = 'patients';
	markActiveTab();
	await renderTab();
}

function markActiveTab() {
	document.querySelectorAll('#tabs button').forEach((button) => {
		button.classList.toggle('active', button.dataset.tab === state.tab);
	});
}

function switchTab(tab) {
	state.tab = tab;
	state.currentPatient = null;
	markActiveTab();
	renderTab();
}

async function renderTab() {
	const node = view();
	node.innerHTML = '<div class="empty">Loading…</div>';
	if (state.tab === 'patients') {
		await renderPatients();
	} else if (state.tab === 'households') {
		await renderHouseholds();
	} else if (state.tab === 'appointments') {
		await renderAppointments();
	} else if (state.tab === 'referrals') {
		await renderReferrals();
	} else if (state.tab === 'sync') {
		await renderSync();
	}
}

// ---------------------------------------------------------------- patients

async function renderPatients() {
	const records = await db.listRecords('patient');
	const needle = state.patientSearch.toLowerCase();
	const filtered = records.filter((record) => {
		if (record.deleted) {
			return false;
		}
		if (!needle) {
			return true;
		}
		return (patientName(record.data) + ' ' + patientIdentifier(record.data)).toLowerCase().includes(needle);
	}).sort((a, b) => patientName(a.data).localeCompare(patientName(b.data)));

	const node = view();
	node.innerHTML = '';
	node.appendChild(el(
		'<div class="toolbar">'
		+ '<input id="patient-search" placeholder="Search name or ID" value="' + escapeHtml(state.patientSearch) + '" style="flex:2">'
		+ '<button id="new-patient">Register</button>'
		+ '</div>'
	));

	if (!filtered.length) {
		node.appendChild(el('<div class="empty">' + (records.length ? 'No patients match.' : 'No patients yet. Tap Register to add the first one.') + '</div>'));
	} else {
		const card = el('<div class="card"></div>');
		for (const record of filtered) {
			const activeVisits = (await db.listRecords('visit')).filter((v) => !v.deleted && v.data && v.data.patient === record.uuid).length;
			const item = el(
				'<div class="list-item">'
				+ '<div><div class="title">' + escapeHtml(patientName(record.data)) + '</div>'
				+ '<div class="meta">' + escapeHtml(patientIdentifier(record.data) || 'no identifier') + '</div></div>'
				+ '<div style="text-align:right">'
				+ (record.dirty ? '<span class="flag dirty">unsynced</span> ' : '')
				+ (activeVisits ? '<span class="flag synced">' + activeVisits + ' visit</span>' : '')
				+ '</div></div>'
			);
			item.addEventListener('click', () => openPatient(record.uuid));
			card.appendChild(item);
		}
		node.appendChild(card);
	}

	node.querySelector('#patient-search').addEventListener('input', (event) => {
		state.patientSearch = event.target.value;
		renderPatients();
	});
	node.querySelector('#new-patient').addEventListener('click', () => openPatientForm(null));
}

function findSubUuid(list, filterFn) {
	const item = (list || []).find(filterFn);
	return item ? item.uuid : null;
}

async function openPatientForm(record) {
	await loadReference(false);
	const data = record ? record.data : {};
	const name = (data.names || [])[0] || {};
	const identifier = (data.identifiers || [])[0] || {};
	const address = (data.addresses || [])[0] || {};
	const ref = state.reference || {};
	const requiredType = (ref.identifierTypes || []).find((t) => t.required) || (ref.identifierTypes || [])[0];

	showModal(record ? 'Edit patient' : 'Register patient',
		'<label>Given name</label><input name="givenName" value="' + escapeHtml(name.givenName || '') + '" required>'
		+ '<label>Family name</label><input name="familyName" value="' + escapeHtml(name.familyName || '') + '">'
		+ '<div class="row"><div><label>Sex</label><select name="gender">'
		+ ['', 'M', 'F', 'O'].map((g) => '<option value="' + g + '"' + (data.gender === g ? ' selected' : '') + '>' + (g || '—') + '</option>').join('')
		+ '</select></div><div><label>Date of birth</label><input type="date" name="birthdate" value="' + escapeHtml(data.birthdate || '') + '"></div></div>'
		+ '<label><input type="checkbox" name="birthdateEstimated"' + (data.birthdateEstimated ? ' checked' : '') + '> Date of birth is estimated</label>'
		+ '<label>Identifier</label><input name="identifier" value="' + escapeHtml(identifier.identifier || '') + '">'
		+ '<label>Identifier type</label><select name="identifierType">' + optionsFrom(ref.identifierTypes, identifier.identifierType || (requiredType && requiredType.uuid)) + '</select>'
		+ '<label>Village / address</label><input name="address1" value="' + escapeHtml(address.address1 || '') + '">'
		+ '<div class="row"><div><label>District</label><input name="countyDistrict" value="' + escapeHtml(address.countyDistrict || '') + '"></div>'
		+ '<div><label>Province</label><input name="stateProvince" value="' + escapeHtml(address.stateProvince || '') + '"></div></div>',
		{
			submitLabel: record ? 'Save' : 'Register',
			onSubmit: async (form) => {
				const patientUuid = record ? record.uuid : uuid();
				const identifierValue = form.identifier.value.trim();
				const existingIdentifierUuid = findSubUuid(data.identifiers, (i) => true);
				const payload = {
					uuid: patientUuid,
					gender: form.gender.value || null,
					birthdate: form.birthdate.value || null,
					birthdateEstimated: form.birthdateEstimated.checked,
					names: [{
						uuid: findSubUuid(data.names, (n) => true) || uuid(),
						givenName: form.givenName.value.trim(),
						familyName: form.familyName.value.trim(),
						preferred: true
					}],
					identifiers: (identifierValue || existingIdentifierUuid) ? [{
						uuid: existingIdentifierUuid || uuid(),
						identifier: identifierValue,
						identifierType: form.identifierType.value || null,
						preferred: true
					}] : [],
					addresses: (form.address1.value.trim() || form.countyDistrict.value.trim()) ? [{
						uuid: findSubUuid(data.addresses, (a) => true) || uuid(),
						address1: form.address1.value.trim(),
						countyDistrict: form.countyDistrict.value.trim(),
						stateProvince: form.stateProvince.value.trim()
					}] : [],
					attributes: data.attributes || []
				};
				if (!payload.names[0].givenName) {
					toast('A given name is required.', true);
					return false;
				}
				await persistRecord('patient', patientUuid, payload);
				toast(record ? 'Patient updated' : 'Patient saved on this device');
				if (record) {
					openPatient(patientUuid);
				} else {
					renderTab();
				}
				return true;
			}
		});
}

async function openPatient(patientUuid) {
	const record = await db.getRecord('patient', patientUuid);
	if (!record) {
		toast('That patient is no longer on this device.', true);
		renderTab();
		return;
	}
	state.currentPatient = patientUuid;
	const patients = await db.listRecords('patient');
	const visits = (await db.listRecords('visit')).filter((v) => !v.deleted && v.data.patient === patientUuid);
	const encounters = (await db.listRecords('encounter')).filter((e) => !e.deleted && e.data.patient === patientUuid);
	const appointments = (await db.listRecords('appointment')).filter((a) => !a.deleted && a.data.patient === patientUuid);
	const referrals = (await db.listRecords('referral')).filter((r) => !r.deleted && r.data.patient === patientUuid);
	const households = (await db.listRecords('household')).filter((h) => !h.deleted
		&& (h.data.headPatient === patientUuid || (h.data.members || []).some((m) => m.patient === patientUuid)));

	const node = view();
	node.innerHTML = '';
	const header = el(
		'<div class="card">'
		+ '<div class="toolbar" style="margin-bottom:6px"><button class="ghost" id="back">← All patients</button></div>'
		+ '<h2>' + escapeHtml(patientName(record.data)) + '</h2>'
		+ '<div class="sub">' + escapeHtml(patientIdentifier(record.data) || 'no identifier')
		+ ' · ' + escapeHtml(record.data.gender || 'sex unknown')
		+ (record.data.birthdate ? ' · born ' + escapeHtml(formatDate(record.data.birthdate)) : '')
		+ (record.dirty ? ' · <span class="flag dirty">unsynced</span>' : '') + '</div>'
		+ '<div class="toolbar" style="margin-top:12px">'
		+ '<button id="new-visit">Start visit</button>'
		+ '<button class="secondary" id="new-encounter">Record vitals</button>'
		+ '<button class="secondary" id="new-appointment">Appointment</button>'
		+ '<button class="secondary" id="new-referral">Referral</button>'
		+ '<button class="secondary" id="edit-patient">Edit</button>'
		+ '<button class="danger" id="void-patient">Remove</button>'
		+ '</div></div>'
	);
	node.appendChild(header);
	header.querySelector('#back').addEventListener('click', () => { state.currentPatient = null; renderPatients(); });
	header.querySelector('#edit-patient').addEventListener('click', () => openPatientForm(record));
	header.querySelector('#new-visit').addEventListener('click', () => openVisitForm(patientUuid));
	header.querySelector('#new-encounter').addEventListener('click', () => openEncounterForm(patientUuid, visits));
	header.querySelector('#new-appointment').addEventListener('click', () => openAppointmentForm(null, patientUuid));
	header.querySelector('#new-referral').addEventListener('click', () => openReferralForm(null, patientUuid));
	header.querySelector('#void-patient').addEventListener('click', () => confirmDialog('Remove this patient from the device and the server?', async () => {
		await voidRecord('patient', patientUuid);
		toast('Patient removed');
		renderPatients();
	}));

	node.appendChild(section('Visits', visits, (v) => visitLabel(v.data), (v) => toast('Visit ' + formatDateTime(v.data.startDatetime))));
	node.appendChild(section('Encounters', encounters, (e) => encounterLabel(e.data), (e) => toast('Recorded ' + formatDateTime(e.data.encounterDatetime))));
	node.appendChild(section('Appointments', appointments, (a) => escapeHtml(formatDateTime(a.data.appointmentDate)) + ' — ' + escapeHtml(a.data.reason || 'follow-up'), null));
	node.appendChild(section('Referrals', referrals, (r) => escapeHtml(r.data.facility || 'referral') + ' — ' + escapeHtml(r.data.reason || ''), null));
	if (households.length) {
		node.appendChild(section('Households', households, (h) => escapeHtml(h.data.name || 'household'), null));
	}
}

function section(title, records, labelFn, clickFn) {
	const card = el('<div class="card"><h2>' + escapeHtml(title) + '</h2></div>');
	if (!records.length) {
		card.appendChild(el('<div class="empty" style="padding:12px">Nothing yet.</div>'));
		return card;
	}
	for (const record of records) {
		const item = el('<div class="list-item"><div><div class="title">' + labelFn(record) + '</div>'
			+ '<div class="meta">' + escapeHtml(formatDateTime(record.changedAt || '')) + '</div></div>'
			+ (record.dirty ? '<span class="flag dirty">unsynced</span>' : '') + '</div>');
		if (clickFn) {
			item.addEventListener('click', () => clickFn(record));
		}
		card.appendChild(item);
	}
	return card;
}

function visitLabel(data) {
	return 'Visit' + (data.startDatetime ? ' · ' + formatDateTime(data.startDatetime) : '') + (data.stopDatetime ? ' (closed)' : ' (open)');
}

function encounterLabel(data) {
	const obs = data.obs || [];
	return 'Encounter · ' + obs.length + ' observation' + (obs.length === 1 ? '' : 's');
}

// ---------------------------------------------------------------- visits & encounters

async function openVisitForm(patientUuid) {
	await loadReference(false);
	const ref = state.reference || {};
	const data = { patient: patientUuid };
	showModal('Start a visit',
		'<label>Visit type</label><select name="visitType">' + optionsFrom(ref.visitTypes) + '</select>'
		+ '<label>Location</label><select name="location">' + optionsFrom(ref.locations) + '</select>'
		+ '<label>Start</label><input type="datetime-local" name="startDatetime" value="' + escapeHtml(toWireDateTime(new Date()).substring(0, 16)) + '">',
		{
			submitLabel: 'Start visit',
			onSubmit: async (form) => {
				const visitUuid = uuid();
				const payload = {
					uuid: visitUuid,
					patient: patientUuid,
					visitType: form.visitType.value || null,
					location: form.location.value || null,
					startDatetime: form.startDatetime.value ? form.startDatetime.value.substring(0, 19) : toWireDateTime(new Date())
				};
				await persistRecord('visit', visitUuid, payload);
				toast('Visit started on this device');
				openPatient(patientUuid);
				return true;
			}
		});
}

async function openEncounterForm(patientUuid, visits) {
	await loadReference(false);
	const ref = state.reference || {};
	const openVisits = (visits || []).filter((v) => !v.data.stopDatetime);
	showModal('Record vitals',
		'<label>Encounter type</label><select name="encounterType">' + optionsFrom(ref.encounterTypes) + '</select>'
		+ '<label>Visit</label><select name="visit"><option value="">(no visit)</option>' + optionsFrom(openVisits.map((v) => ({ uuid: v.uuid, name: 'Visit ' + formatDateTime(v.data.startDatetime) }))) + '</select>'
		+ '<label>Location</label><select name="location">' + optionsFrom(ref.locations) + '</select>'
		+ '<label>Date and time</label><input type="datetime-local" name="encounterDatetime" value="' + escapeHtml(toWireDateTime(new Date()).substring(0, 16)) + '">'
		+ '<label>Observations</label><div id="obs-rows"></div>'
		+ '<button type="button" class="secondary" id="add-obs">Add observation</button>',
		{
			submitLabel: 'Save encounters',
			onSubmit: async (form) => {
				const obs = [];
				form.querySelectorAll('.obs-row').forEach((row) => {
					const concept = row.querySelector('[name="concept"]').value.trim();
					const value = row.querySelector('[name="value"]').value.trim();
					const type = row.querySelector('[name="valueType"]').value;
					if (!concept || !value) {
						return;
					}
					const obsEntry = { uuid: uuid(), concept, obsDatetime: form.encounterDatetime.value.substring(0, 19) };
					if (type === 'numeric') {
						obsEntry.valueNumeric = Number(value);
					} else if (type === 'boolean') {
						obsEntry.valueBoolean = value === 'true';
					} else {
						obsEntry.valueText = value;
					}
					obs.push(obsEntry);
				});
				if (!obs.length) {
					toast('Add at least one observation.', true);
					return false;
				}
				const encounterUuid = uuid();
				const payload = {
					uuid: encounterUuid,
					patient: patientUuid,
					encounterType: form.encounterType.value || null,
					location: form.location.value || null,
					visit: form.visit.value || null,
					encounterDatetime: form.encounterDatetime.value.substring(0, 19),
					providers: (ref.provider ? [{ provider: ref.provider.uuid, role: null }] : []),
					obs
				};
				await persistRecord('encounter', encounterUuid, payload);
				toast('Vitals saved on this device');
				openPatient(patientUuid);
				return true;
			}
		});

	const rows = document.getElementById('obs-rows');
	const addRow = () => {
		const row = el(
			'<div class="obs-row">'
			+ '<div class="obs-concept"><input name="concept" placeholder="Concept uuid" list="concept-list"></div>'
			+ '<div class="obs-value"><input name="value" placeholder="Value"></div>'
			+ '<select name="valueType"><option value="numeric">number</option><option value="text">text</option><option value="boolean">yes/no</option></select>'
			+ '<button type="button" class="ghost">×</button></div>'
		);
		row.querySelector('button').addEventListener('click', () => row.remove());
		rows.appendChild(row);
	};
	document.getElementById('add-obs').addEventListener('click', addRow);
	addRow();
}

// ---------------------------------------------------------------- appointments

async function renderAppointments() {
	const records = (await db.listRecords('appointment')).filter((r) => !r.deleted)
		.sort((a, b) => String(a.data.appointmentDate || '').localeCompare(String(b.data.appointmentDate || '')));
	const patients = await patientIndex();
	const node = view();
	node.innerHTML = '';
	node.appendChild(el('<div class="toolbar"><button id="new-appointment">Schedule appointment</button></div>'));
	const card = el('<div class="card"></div>');
	if (!records.length) {
		card.appendChild(el('<div class="empty">No appointments scheduled.</div>'));
	} else {
		for (const record of records) {
			const item = el('<div class="list-item"><div><div class="title">' + escapeHtml(patients[record.data.patient] || 'Unknown patient') + '</div>'
				+ '<div class="meta">' + escapeHtml(formatDateTime(record.data.appointmentDate)) + ' · ' + escapeHtml(record.data.reason || '') + '</div></div>'
				+ '<span class="badge">' + escapeHtml(record.data.status || 'scheduled') + '</span></div>');
			item.addEventListener('click', () => openAppointmentForm(record));
			card.appendChild(item);
		}
	}
	node.appendChild(card);
	node.querySelector('#new-appointment').addEventListener('click', () => openAppointmentForm(null));
}

async function openAppointmentForm(record, presetPatient) {
	const patients = await db.listRecords('patient');
	const data = record ? record.data : { patient: presetPatient || '' };
	showModal(record ? 'Edit appointment' : 'Schedule appointment',
		'<label>Patient</label><select name="patient">' + patientOptions(patients, data.patient) + '</select>'
		+ '<label>Date and time</label><input type="datetime-local" name="appointmentDate" value="' + escapeHtml((data.appointmentDate || toWireDateTime(new Date())).substring(0, 16)) + '">'
		+ '<label>Reason</label><input name="reason" value="' + escapeHtml(data.reason || '') + '">'
		+ '<label>Status</label><select name="status">'
		+ ['scheduled', 'completed', 'missed', 'cancelled'].map((s) => '<option value="' + s + '"' + (data.status === s ? ' selected' : '') + '>' + s + '</option>').join('')
		+ '</select><label>Notes</label><textarea name="notes">' + escapeHtml(data.notes || '') + '</textarea>',
		{
			submitLabel: 'Save',
			onSubmit: async (form) => {
				if (!form.patient.value) {
					toast('Choose a patient.', true);
					return false;
				}
				const appointmentUuid = record ? record.uuid : uuid();
				await persistRecord('appointment', appointmentUuid, {
					uuid: appointmentUuid,
					patient: form.patient.value,
					appointmentDate: form.appointmentDate.value.substring(0, 19),
					reason: form.reason.value.trim(),
					status: form.status.value,
					notes: form.notes.value.trim()
				});
				toast('Appointment saved');
				renderTab();
				return true;
			}
		});
}

// ---------------------------------------------------------------- referrals

async function renderReferrals() {
	const records = (await db.listRecords('referral')).filter((r) => !r.deleted);
	const patients = await patientIndex();
	const node = view();
	node.innerHTML = '';
	node.appendChild(el('<div class="toolbar"><button id="new-referral">New referral</button></div>'));
	const card = el('<div class="card"></div>');
	if (!records.length) {
		card.appendChild(el('<div class="empty">No referrals.</div>'));
	} else {
		for (const record of records) {
			const urgency = record.data.urgency || 'routine';
			const item = el('<div class="list-item"><div><div class="title">' + escapeHtml(patients[record.data.patient] || 'Unknown patient') + '</div>'
				+ '<div class="meta">' + escapeHtml(record.data.facility || '') + ' · ' + escapeHtml(formatDateTime(record.data.referralDate)) + '</div></div>'
				+ '<span class="badge ' + (urgency === 'emergency' || urgency === 'urgent' ? 'high' : (urgency === 'priority' ? 'medium' : '')) + '">' + escapeHtml(urgency) + '</span></div>');
			item.addEventListener('click', () => openReferralForm(record));
			card.appendChild(item);
		}
	}
	node.appendChild(card);
	node.querySelector('#new-referral').addEventListener('click', () => openReferralForm(null));
}

async function openReferralForm(record, presetPatient) {
	const patients = await db.listRecords('patient');
	const data = record ? record.data : { patient: presetPatient || '' };
	showModal(record ? 'Edit referral' : 'New referral',
		'<label>Patient</label><select name="patient">' + patientOptions(patients, data.patient) + '</select>'
		+ '<label>Facility</label><input name="facility" value="' + escapeHtml(data.facility || '') + '">'
		+ '<label>Reason</label><input name="reason" value="' + escapeHtml(data.reason || '') + '">'
		+ '<label>Urgency</label><select name="urgency">'
		+ ['routine', 'priority', 'urgent', 'emergency'].map((u) => '<option value="' + u + '"' + (data.urgency === u ? ' selected' : '') + '>' + u + '</option>').join('')
		+ '</select><label>Status</label><select name="status">'
		+ ['pending', 'accepted', 'completed', 'declined'].map((s) => '<option value="' + s + '"' + (data.status === s ? ' selected' : '') + '>' + s + '</option>').join('')
		+ '</select><label>Date</label><input type="datetime-local" name="referralDate" value="' + escapeHtml((data.referralDate || toWireDateTime(new Date())).substring(0, 16)) + '">'
		+ '<label>Notes</label><textarea name="notes">' + escapeHtml(data.notes || '') + '</textarea>',
		{
			submitLabel: 'Save',
			onSubmit: async (form) => {
				if (!form.patient.value) {
					toast('Choose a patient.', true);
					return false;
				}
				const referralUuid = record ? record.uuid : uuid();
				await persistRecord('referral', referralUuid, {
					uuid: referralUuid,
					patient: form.patient.value,
					facility: form.facility.value.trim(),
					reason: form.reason.value.trim(),
					urgency: form.urgency.value,
					status: form.status.value,
					referralDate: form.referralDate.value.substring(0, 19),
					notes: form.notes.value.trim()
				});
				toast('Referral saved');
				renderTab();
				return true;
			}
		});
}

// ---------------------------------------------------------------- households

async function renderHouseholds() {
	const records = (await db.listRecords('household')).filter((r) => !r.deleted);
	const node = view();
	node.innerHTML = '';
	node.appendChild(el('<div class="toolbar"><button id="new-household">New household</button></div>'));
	const card = el('<div class="card"></div>');
	if (!records.length) {
		card.appendChild(el('<div class="empty">No households.</div>'));
	} else {
		for (const record of records) {
			const members = (record.data.members || []).length;
			const item = el('<div class="list-item"><div><div class="title">' + escapeHtml(record.data.name || 'Household') + '</div>'
				+ '<div class="meta">' + escapeHtml(record.data.village || '') + ' · ' + members + ' member' + (members === 1 ? '' : 's') + '</div></div>'
				+ (record.dirty ? '<span class="flag dirty">unsynced</span>' : '') + '</div>');
			item.addEventListener('click', () => openHouseholdForm(record));
			card.appendChild(item);
		}
	}
	node.appendChild(card);
	node.querySelector('#new-household').addEventListener('click', () => openHouseholdForm(null));
}

async function openHouseholdForm(record) {
	const patients = await db.listRecords('patient');
	const data = record ? record.data : { members: [] };
	const memberRows = (data.members || []).map((member) => householdMemberRow(patients, member.patient, member.relationship, member.headOfHousehold)).join('');
	showModal(record ? 'Edit household' : 'New household',
		'<label>Name</label><input name="name" value="' + escapeHtml(data.name || '') + '">'
		+ '<label>Village</label><input name="village" value="' + escapeHtml(data.village || '') + '">'
		+ '<label>Members</label><div id="member-rows">' + memberRows + '</div>'
		+ '<button type="button" class="secondary" id="add-member">Add member</button>',
		{
			submitLabel: 'Save',
			onSubmit: async (form) => {
				const members = [];
				form.querySelectorAll('.member-row').forEach((row) => {
					const patient = row.querySelector('[name="memberPatient"]').value;
					if (patient) {
						members.push({
							patient,
							relationship: row.querySelector('[name="relationship"]').value.trim(),
							headOfHousehold: row.querySelector('[name="head"]').checked
						});
					}
				});
				const householdUuid = record ? record.uuid : uuid();
				const head = members.find((m) => m.headOfHousehold);
				await persistRecord('household', householdUuid, {
					uuid: householdUuid,
					name: form.name.value.trim(),
					village: form.village.value.trim(),
					headPatient: head ? head.patient : null,
					members
				});
				toast('Household saved');
				renderTab();
				return true;
			}
		});
	const rows = document.getElementById('member-rows');
	document.getElementById('add-member').addEventListener('click', () => {
		rows.insertAdjacentHTML('beforeend', householdMemberRow(patients, '', '', false));
	});
}

function householdMemberRow(patients, selectedPatient, relationship, head) {
	return '<div class="member-row"><div class="row">'
		+ '<select name="memberPatient">' + patientOptions(patients, selectedPatient, true) + '</select>'
		+ '<input name="relationship" placeholder="relationship" value="' + escapeHtml(relationship || '') + '">'
		+ '</div><label><input type="checkbox" name="head"' + (head ? ' checked' : '') + '> Head of household</label></div>';
}

// ---------------------------------------------------------------- shared patient helpers

async function patientIndex() {
	const patients = await db.listRecords('patient');
	const index = {};
	for (const record of patients) {
		index[record.uuid] = patientName(record.data);
	}
	return index;
}

function patientOptions(patients, selected, allowEmpty) {
	const head = allowEmpty ? '<option value="">— select patient —</option>' : '<option value="">Choose…</option>';
	return head + patients.filter((p) => !p.deleted).map((p) => {
		const label = patientName(p.data) + (patientIdentifier(p.data) ? ' (' + patientIdentifier(p.data) + ')' : '');
		return '<option value="' + escapeHtml(p.uuid) + '"' + (p.uuid === selected ? ' selected' : '') + '>' + escapeHtml(label) + '</option>';
	}).join('');
}

// ---------------------------------------------------------------- sync tab

async function renderSync() {
	const user = await db.getSetting('user', null);
	const cursor = await db.getSetting('cursor', null);
	const conflicts = await db.listConflicts();
	const node = view();
	node.innerHTML = '';
	node.appendChild(el(
		'<div class="card">'
		+ '<h2>This device</h2>'
		+ '<div class="sub">' + escapeHtml(user ? (user.displayName || user.username) : 'not signed in') + '</div>'
		+ '<div class="sub">Last pulled: ' + escapeHtml(cursor ? formatDateTime(cursor) : 'never') + '</div>'
		+ '<div class="toolbar" style="margin-top:12px">'
		+ '<button id="sync-now">Sync now</button>'
		+ '<button class="secondary" id="refresh-ref">Refresh reference data</button>'
		+ '<button class="ghost" id="logout">Sign out</button>'
		+ '</div></div>'
	));

	const conflictCard = el('<div class="card"><h2>Needs attention</h2></div>');
	if (!conflicts.length) {
		conflictCard.appendChild(el('<div class="empty">No conflicts. Everything this device recorded has been accepted.</div>'));
	} else {
		for (const conflict of conflicts) {
			conflictCard.appendChild(el('<div class="list-item"><div><div class="title">' + escapeHtml(conflict.type + ' ' + conflict.operation.toLowerCase()) + '</div>'
				+ '<div class="meta">' + escapeHtml(conflict.message) + '</div></div></div>'));
		}
		conflictCard.appendChild(el('<div class="toolbar" style="margin-top:10px"><button class="ghost" id="clear-conflicts">Clear</button></div>'));
	}
	node.appendChild(conflictCard);

	node.querySelector('#sync-now').addEventListener('click', doSync);
	node.querySelector('#refresh-ref').addEventListener('click', async () => {
		await loadReference(true);
		toast('Reference data refreshed');
	});
	node.querySelector('#logout').addEventListener('click', () => confirmDialog('Sign out and stop syncing this device?', async () => {
		try {
			await api.logout();
		} catch (e) { /* token may already be dead */ }
		await db.setSetting('token', null);
		api.setToken(null);
		renderLogin();
	}));
	const clear = node.querySelector('#clear-conflicts');
	if (clear) {
		clear.addEventListener('click', async () => {
			await db.clearConflicts();
			renderSync();
		});
	}
}

async function doSync() {
	if (!state.online) {
		toast('No connection. Your work is saved and will sync later.', true);
		return;
	}
	const button = document.getElementById('sync-now');
	if (button) {
		button.disabled = true;
		button.textContent = 'Syncing…';
	}
	try {
		const result = await sync.syncNow((progress) => {
			if (button) {
				button.textContent = 'Pulled ' + progress.changes + '…';
			}
		});
		await refreshPending();
		toast('Synced: ' + result.applied + ' sent, ' + result.pulled + ' received'
			+ (result.conflicts ? ', ' + result.conflicts + ' conflict' + (result.conflicts === 1 ? '' : 's') : ''));
		if (state.tab === 'sync') {
			renderSync();
		} else {
			renderTab();
		}
	} catch (error) {
		if (error.status === 401) {
			await db.setSetting('token', null);
			api.setToken(null);
			toast('Your session expired. Please sign in again.', true);
			renderLogin();
		} else {
			toast(error.message || 'Sync failed', true);
		}
	} finally {
		if (button) {
			button.disabled = false;
			button.textContent = 'Sync now';
		}
	}
}

// ---------------------------------------------------------------- startup

async function registerServiceWorker() {
	if ('serviceWorker' in navigator) {
		try {
			await navigator.serviceWorker.register('./sw.js', { scope: './' });
		} catch (e) {
			// A failed registration only costs offline shell caching; the app still runs.
		}
	}
}

async function boot() {
	await db.open();
	const baseUrl = await db.getSetting('baseUrl', null);
	api.configure(baseUrl ? { baseUrl } : {});
	const token = await db.getSetting('token', null);
	api.setToken(token);

	document.querySelectorAll('#tabs button').forEach((button) => {
		button.addEventListener('click', () => switchTab(button.dataset.tab));
	});
	window.addEventListener('online', () => { state.online = true; setStatus(); toast('Back online'); });
	window.addEventListener('offline', () => { state.online = false; setStatus(); });
	state.online = navigator.onLine;

	await registerServiceWorker();
	await refreshPending();

	if (!token) {
		renderLogin();
		return;
	}
	document.getElementById('tabs').hidden = false;
	markActiveTab();
	await loadReference(false);
	await renderTab();
	// A quiet sync on launch keeps the register fresh without the user asking.
	if (state.online) {
		doSync();
	}
}

boot().catch((error) => {
	view().innerHTML = '<div class="empty">The app could not start: ' + escapeHtml(error.message || String(error)) + '</div>';
});
