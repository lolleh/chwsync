/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
// Small formatting helpers shared by the views. Kept dependency-free so the shell has nothing to
// download beyond these files.

export function escapeHtml(value) {
	if (value === null || value === undefined) {
		return '';
	}
	return String(value)
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
		.replace(/'/g, '&#39;');
}

export function patientName(patient) {
	if (!patient) {
		return 'Unknown patient';
	}
	const names = Array.isArray(patient.names) ? patient.names : [];
	const preferred = names.find((n) => n.preferred) || names[0];
	if (!preferred) {
		const identifier = (patient.identifiers || [])[0];
		return identifier ? identifier.identifier : 'Unnamed patient';
	}
	return [preferred.givenName, preferred.middleName, preferred.familyName]
		.filter(Boolean)
		.join(' ');
}

export function patientIdentifier(patient) {
	const identifiers = Array.isArray(patient.identifiers) ? patient.identifiers : [];
	const preferred = identifiers.find((i) => i.preferred) || identifiers[0];
	return preferred ? preferred.identifier : '';
}

export function formatDate(value) {
	if (!value) {
		return '';
	}
	const date = typeof value === 'string' ? new Date(value) : value;
	if (isNaN(date.getTime())) {
		return String(value);
	}
	return date.toLocaleDateString();
}

export function formatDateTime(value) {
	if (!value) {
		return '';
	}
	const date = typeof value === 'string' ? new Date(value) : value;
	if (isNaN(date.getTime())) {
		return String(value);
	}
	return date.toLocaleString();
}

export function toWireDateTime(date) {
	const pad = (n) => String(n).padStart(2, '0');
	return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
		+ 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
}

export function toWireDate(date) {
	const pad = (n) => String(n).padStart(2, '0');
	return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
}

export function uuid() {
	if (self.crypto && self.crypto.randomUUID) {
		return self.crypto.randomUUID();
	}
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
		const r = (Math.random() * 16) | 0;
		const v = c === 'x' ? r : (r & 0x3) | 0x8;
		return v.toString(16);
	});
}
