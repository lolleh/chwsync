/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.OpenmrsObject;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.chwsync.api.event.ChwSyncChangeEventListener;
import org.openmrs.module.chwsync.api.service.ChwSyncService;

/**
 * Starts and stops this module.
 * <p>
 * The one thing that must happen here, rather than through Spring alone, is subscribing the change
 * listener to core events. The feed is only authoritative if it hears about every facility-side
 * write, so the subscription is made explicitly and torn down explicitly, instead of relying on the
 * event module to discover the listener bean. If the subscription cannot be made the module still
 * starts - a device can still pull and push its own records - but the gap is logged loudly, because
 * it means facility-side edits will not reach devices until it is fixed.
 */
public class ChwSyncActivator extends BaseModuleActivator {
	
	private static final Log log = LogFactory.getLog(ChwSyncActivator.class);
	
	private ChwSyncChangeEventListener listener;
	
	@Override
	public void started() {
		try {
			listener = new ChwSyncChangeEventListener();
			listener.setSyncService(Context.getService(ChwSyncService.class));
			List<String> actions = listener.subscribeToActions();
			for (Class<? extends OpenmrsObject> type : listener.subscribeToObjects()) {
				Event.subscribe(type, actions, listener);
			}
			log.info("CHW module started; subscribed to core change events for " + listener.subscribeToObjects().size()
			        + " types");
		}
		catch (RuntimeException e) {
			log.error("CHW module started, but could not subscribe to core change events. Facility-side patient, "
			        + "encounter, visit and obs edits will not reach devices until the event module is running and the "
			        + "module is restarted.",
			    e);
		}
	}
	
	@Override
	public void stopped() {
		unsubscribe();
		log.info("CHW module stopped");
	}
	
	private void unsubscribe() {
		if (listener == null) {
			return;
		}
		try {
			List<Event.Action> actions = new ArrayList<Event.Action>();
			for (String action : listener.subscribeToActions()) {
				actions.add(Event.Action.valueOf(action));
			}
			for (Class<? extends OpenmrsObject> type : listener.subscribeToObjects()) {
				Event.unsubscribe(type, actions, listener);
			}
		}
		catch (RuntimeException e) {
			// The event module may already be gone; nothing useful to do and nothing worth failing over.
			log.warn("CHW module could not cleanly unsubscribe from core change events", e);
		}
		finally {
			listener = null;
		}
	}
}
