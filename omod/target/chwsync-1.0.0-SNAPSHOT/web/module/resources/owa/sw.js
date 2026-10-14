/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
/*
 * Service worker for the CHW Field PWA.
 *
 * Its only job is to make the app shell available with no network at all: the CHW opens the app in a
 * village with no signal and still sees their patients. The sync API is never cached - a cached API
 * response would be indistinguishable from a fresh one and could show a stale patient register as if
 * it were current - so this only ever intercepts the module's own static files.
 */
const CACHE = 'chwsync-shell-v1';
const SHELL = [
	'./',
	'./index.html',
	'./app.css',
	'./manifest.webmanifest',
	'./icon.svg',
	'./js/app.js',
	'./js/api.js',
	'./js/db.js',
	'./js/sync.js',
	'./js/format.js'
];

self.addEventListener('install', (event) => {
	event.waitUntil(
		caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting())
	);
});

self.addEventListener('activate', (event) => {
	event.waitUntil(
		caches.keys().then((keys) => Promise.all(
			keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))
		)).then(() => self.clients.claim())
	);
});

self.addEventListener('fetch', (event) => {
	const request = event.request;
	if (request.method !== 'GET') {
		return;
	}
	const url = new URL(request.url);
	if (url.origin !== self.location.origin) {
		return;
	}
	// Only the shell is cached; the API lives under /ws/ and must always hit the network.
	if (!url.pathname.includes('/moduleResources/chwsync/')) {
		return;
	}

	event.respondWith(
		caches.match(request).then((cached) => {
			const network = fetch(request).then((response) => {
				if (response && response.ok) {
					const copy = response.clone();
					caches.open(CACHE).then((cache) => cache.put(request, copy));
				}
				return response;
			}).catch(() => cached);
			return cached || network;
		})
	);
});
