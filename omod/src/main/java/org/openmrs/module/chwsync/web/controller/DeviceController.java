/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.dto.AuthDtos;
import org.openmrs.module.chwsync.api.model.ChwSyncDevice;
import org.openmrs.module.chwsync.api.service.ChwSyncDeviceService;
import org.openmrs.module.chwsync.api.sync.Json;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of the handsets that hold a copy of the patient register.
 * <p>
 * The list exists so an administrator can see which devices are out there, and the deactivate
 * action exists so a lost or stolen one can be cut off. Deactivating revokes every token bound to
 * the device, which is the only step that actually takes the data out of reach; marking the row
 * inactive on its own would be bookkeeping.
 */
@RestController
public class DeviceController extends BaseSyncController {
	
	/** Every registered device, newest first, with the user each belongs to. */
	@RequestMapping(value = ChwSyncConstants.REST_NAMESPACE + "/devices", method = RequestMethod.GET)
	public List<AuthDtos.DeviceView> list(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization) {
		return asManager(authorization, new DeviceWork<List<AuthDtos.DeviceView>>() {
			
			@Override
			public List<AuthDtos.DeviceView> run() {
				List<AuthDtos.DeviceView> views = new ArrayList<AuthDtos.DeviceView>();
				for (ChwSyncDevice device : Context.getService(ChwSyncDeviceService.class).listDevices()) {
					views.add(toView(device));
				}
				return views;
			}
		});
	}
	
	/** Deactivates a device and revokes its tokens: what "this phone is gone" means. */
	@RequestMapping(value = ChwSyncConstants.REST_NAMESPACE + "/devices/{uuid}/deactivate", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization,
	        @PathVariable("uuid") final String uuid) {
		asManager(authorization, new DeviceWork<Void>() {
			
			@Override
			public Void run() {
				ChwSyncDeviceService deviceService = Context.getService(ChwSyncDeviceService.class);
				if (deviceService.getDevice(uuid) == null) {
					throw new NotFoundException("No device with uuid " + uuid);
				}
				deviceService.setActive(uuid, false);
				deviceService.revokeDevice(uuid);
				return null;
			}
		});
	}
	
	/** Puts a previously deactivated device back into service. The old tokens stay revoked. */
	@RequestMapping(value = ChwSyncConstants.REST_NAMESPACE + "/devices/{uuid}/activate", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void activate(@RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization,
	        @PathVariable("uuid") final String uuid) {
		asManager(authorization, new DeviceWork<Void>() {
			
			@Override
			public Void run() {
				ChwSyncDeviceService deviceService = Context.getService(ChwSyncDeviceService.class);
				if (deviceService.getDevice(uuid) == null) {
					throw new NotFoundException("No device with uuid " + uuid);
				}
				deviceService.setActive(uuid, true);
				return null;
			}
		});
	}
	
	/**
	 * A small, unauthenticated description of the API and the records it carries.
	 * <p>
	 * Clients read this before they have a token, to check they are talking to a compatible server and
	 * to learn the record types without hard-coding them twice.
	 */
	@RequestMapping(value = ChwSyncConstants.REST_NAMESPACE + "/manifest", method = RequestMethod.GET)
	public Map<String, Object> manifest() {
		Map<String, Object> manifest = new LinkedHashMap<String, Object>();
		manifest.put("module", ChwSyncConstants.MODULE_ID);
		manifest.put("apiVersion", "v1");
		manifest.put("namespace", ChwSyncConstants.REST_NAMESPACE_URL);
		List<String> types = new ArrayList<String>();
		for (SyncEntityType type : SyncEntityType.values()) {
			types.add(type.getWireName());
		}
		manifest.put("entityTypes", types);
		manifest.put("serverTime", Json.formatDateTime(new java.util.Date()));
		return manifest;
	}
	
	private static AuthDtos.DeviceView toView(ChwSyncDevice device) {
		AuthDtos.DeviceView view = new AuthDtos.DeviceView();
		view.setUuid(device.getUuid());
		view.setUserId(device.getUserId());
		if (device.getUserId() != null) {
			User user = Context.getUserService().getUser(device.getUserId());
			if (user != null) {
				view.setUsername(user.getUsername());
			}
		}
		view.setName(device.getName());
		view.setPlatform(device.getPlatform());
		view.setAppVersion(device.getAppVersion());
		view.setActive(device.isActive());
		view.setDateCreated(Json.formatDateTime(device.getDateCreated()));
		view.setLastSeen(Json.formatDateTime(device.getLastSeen()));
		return view;
	}
}
