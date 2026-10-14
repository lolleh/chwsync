/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
// Thin client for the module's sync API. It knows nothing about IndexedDB - it is just the wire.
//
// The base URL is derived from where this file is served: the app lives at
// <context>/moduleResources/chwsync/owa/, and the API at <context>/ws/chwsync/v1. Deriving it means the app
// works whether OpenMRS is at /openmrs or at the root, with no build-time configuration.

let token = null;
let serverBase = null;

export function defaultBaseUrl() {
	const path = self.location.pathname;
	const marker = '/moduleResources/';
	const index = path.indexOf(marker);
	const context = index >= 0 ? path.substring(0, index) : path.replace(/\/[^/]*$/, '');
	return context + '/ws/chwsync/v1';
}

export function configure(options) {
	if (options && options.baseUrl) {
		serverBase = options.baseUrl.replace(/\/$/, '');
	} else if (!serverBase) {
		serverBase = defaultBaseUrl();
	}
	if (options && 'token' in options) {
		token = options.token;
	}
	return serverBase;
}

export function getToken() {
	return token;
}

export function setToken(value) {
	token = value;
}

export function baseUrl() {
	return serverBase || defaultBaseUrl();
}

export class ApiError extends Error {
	constructor(status, code, message) {
		super(message || code || ('HTTP ' + status));
		this.status = status;
		this.code = code;
	}
}

async function request(method, path, { body, auth = true } = {}) {
	const headers = { 'Accept': 'application/json' };
	if (body !== undefined) {
		headers['Content-Type'] = 'application/json';
	}
	if (auth && token) {
		headers['Authorization'] = 'Bearer ' + token;
	}
	let response;
	try {
		response = await fetch(baseUrl() + path, {
			method,
			headers,
			body: body === undefined ? undefined : JSON.stringify(body)
		});
	} catch (networkError) {
		throw new ApiError(0, 'network', 'The server could not be reached.');
	}

	if (response.status === 204) {
		return null;
	}

	const text = await response.text();
	let payload = null;
	if (text) {
		try {
			payload = JSON.parse(text);
		} catch (parseError) {
			payload = null;
		}
	}

	if (!response.ok) {
		const code = payload && payload.code ? payload.code : 'http_' + response.status;
		const message = payload && payload.message ? payload.message : ('HTTP ' + response.status);
		throw new ApiError(response.status, code, message);
	}
	return payload;
}

export function manifest() {
	return request('GET', '/manifest', { auth: false });
}

export function login(credentials) {
	return request('POST', '/auth/login', { body: credentials, auth: false });
}

export function me() {
	return request('GET', '/auth/me');
}

export function logout() {
	return request('POST', '/auth/logout');
}

export function pull(since, types, limit) {
	const params = new URLSearchParams();
	if (since) {
		params.set('since', since);
	}
	if (types && types.length) {
		params.set('types', types.join(','));
	}
	if (limit) {
		params.set('limit', String(limit));
	}
	const query = params.toString();
	return request('GET', '/sync/pull' + (query ? '?' + query : ''));
}

export function push(deviceUuid, changes) {
	return request('POST', '/sync/push', { body: { deviceUuid, changes } });
}

export function fetchRecord(type, uuid) {
	return request('GET', '/sync/record/' + encodeURIComponent(type) + '/' + encodeURIComponent(uuid));
}

export function listDevices() {
	return request('GET', '/devices');
}

export function reference() {
	return request('GET', '/reference');
}

export function searchConcepts(query) {
	return request('GET', '/reference/concepts?q=' + encodeURIComponent(query));
}
