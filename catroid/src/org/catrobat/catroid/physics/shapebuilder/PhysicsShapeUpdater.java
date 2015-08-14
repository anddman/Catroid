/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2015 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.physics.shapebuilder;

import android.util.Log;

import com.badlogic.gdx.physics.box2d.Shape;

import org.catrobat.catroid.common.LookData;
import org.catrobat.catroid.physics.PhysicsObject;

import java.util.ArrayList;
import java.util.HashMap;

public class PhysicsShapeUpdater {

	public static final PhysicsShapeUpdater instance = new PhysicsShapeUpdater();
	private static final String TAG = PhysicsShapeUpdater.class.getSimpleName();
	private HashMap<PhysicsObject, Registration> physicsObjectRegistrationMap = new HashMap<PhysicsObject, Registration>();
	private HashMap<String, ArrayList<Registration>> lookDataRegistrationMap = new HashMap<String, ArrayList<Registration>>();
	private HashMap<PhysicsObject, Shape[]> pendingUpdatesMap = new HashMap<PhysicsObject, Shape[]>();
	private boolean updatesPending = false;

	private PhysicsShapeUpdater() {
	}

	public synchronized void register(LookData lookData, PhysicsObject physicsObject, float scale, float desiredAccuracyLevel) {
		Registration reg;
		if (!physicsObjectRegistrationMap.keySet().contains(physicsObject)) {
			Log.d(TAG, "reg: scale: " + scale + "\t desAcc: " + desiredAccuracyLevel + "\t lookData: " + lookData
					.getLookName());
			reg = new Registration(lookData, physicsObject, scale, desiredAccuracyLevel);
			physicsObjectRegistrationMap.put(physicsObject, reg);
		} else {
			reg = physicsObjectRegistrationMap.get(physicsObject);
			lookDataRegistrationMap.get(reg.getLookData().getChecksum()).remove(reg);
			reg.update(lookData, scale, desiredAccuracyLevel);
		}

		ArrayList<Registration> registrations = lookDataRegistrationMap.get(lookData.getChecksum());
		if (registrations == null) {
			registrations = new ArrayList<Registration>();
			lookDataRegistrationMap.put(lookData.getChecksum(), registrations);
		}

		registrations.add(reg);
		Log.d(TAG, physicsObjectRegistrationMap.size() + " physicsObjectRegistrations");
	}

	public synchronized void inform(LookData lookData, Shape[] shapes, float newAccuracyLevel) {
		ArrayList<Registration> registrations = lookDataRegistrationMap.get(lookData.getChecksum());
		if (registrations == null) {
			return;
		}

		for (Registration registration : registrations) {
			registration.setShapesForUpdate(shapes, newAccuracyLevel);
		}
	}

	public synchronized void doUpdateShapes() {
		if (!updatesPending) {
			return;
		}
		ArrayList<PhysicsObject> physicsObjectsToUpdate = new ArrayList<PhysicsObject>(pendingUpdatesMap.keySet());
		for (PhysicsObject physicsObject : physicsObjectsToUpdate) {
			doUpdatePhysicsObjectShapes(physicsObject);
		}
	}

	public synchronized void doUpdatePhysicsObjectShapes(PhysicsObject physicsObject) {
		if (physicsObjectRegistrationMap.containsKey(physicsObject) && pendingUpdatesMap.containsKey(physicsObject)) {
			physicsObject.setShape(pendingUpdatesMap.get(physicsObject));
			pendingUpdatesMap.remove(physicsObject);
			Registration reg = physicsObjectRegistrationMap.get(physicsObject);
			Log.d(TAG, "setting shape: scale " + reg.getScale() + "\t " + reg.getLookData().getLookName());
			if (reg.isDesiredAccuracy()) {
				doUnregister(reg);
			}
		}
	}

	public synchronized void unregister(PhysicsObject physicsObject) {
		Registration reg = physicsObjectRegistrationMap.get(physicsObject);
		if (reg != null) {
			doUnregister(reg);
		}
	}

	private synchronized void doUnregister(Registration registration) {
		Log.d(TAG, "UNreg look: " + registration.getLookData().getLookName());
		pendingUpdatesMap.remove(registration.getPhysicsObject());
		updatesPending = hasUpdatesPending();
		lookDataRegistrationMap.get(registration.getLookData().getChecksum()).remove(registration);
		physicsObjectRegistrationMap.remove(registration.getPhysicsObject());
		Log.d(TAG, physicsObjectRegistrationMap.size() + " physicsObjectRegistrations");
	}

	public synchronized boolean hasUpdatesPending() {
		return pendingUpdatesMap.keySet().size() > 0;
	}

	public synchronized void clearAll() {
		pendingUpdatesMap.clear();
		lookDataRegistrationMap.clear();
		physicsObjectRegistrationMap.clear();
	}

	private class Registration {

		private LookData lookData;
		private PhysicsObject physicsObject;
		private float scale = 1.0f;
		private float desiredAccuracyLevel = 1.0f;
		private float currentAccuracyLevel = Float.NEGATIVE_INFINITY;

		public Registration(LookData lookData, PhysicsObject physicsObject, float scale, float desiredAccuracyLevel) {
			this.lookData = lookData;
			this.physicsObject = physicsObject;
			this.scale = scale;
			this.desiredAccuracyLevel = desiredAccuracyLevel;
		}

		public synchronized void setShapesForUpdate(Shape[] shapes, float newAccuracyLevel) {
			if (Math.abs(desiredAccuracyLevel - newAccuracyLevel) < Math.abs(desiredAccuracyLevel - currentAccuracyLevel)) {
				Log.d(TAG, "prep update: scale: " + this.scale + "\t accLevel: " + newAccuracyLevel + "\t "
						+ lookData.getLookName());
				Shape[] scaledShapes = PhysicsShapeBuilder.scaleShapes(shapes, scale);
				synchronized (pendingUpdatesMap) {
					pendingUpdatesMap.put(physicsObject, scaledShapes);
					updatesPending = hasUpdatesPending();
				}
				this.currentAccuracyLevel = newAccuracyLevel;
			}
		}

		public LookData getLookData() {
			return this.lookData;
		}

		private void setLookData(LookData lookData) {
			if (lookData != this.lookData) {
				this.lookData = lookData;
				this.currentAccuracyLevel = Float.NEGATIVE_INFINITY;
				pendingUpdatesMap.remove(this.physicsObject);
			}
		}

		private synchronized void setScale(float scale) {
			this.scale = scale;
			Shape[] oldShapes = pendingUpdatesMap.get(this.physicsObject);
			if (oldShapes != null) {
				Shape[] newShapes = PhysicsShapeBuilder.scaleShapes(oldShapes, scale);
				pendingUpdatesMap.put(physicsObject, newShapes);
			}
		}

		public synchronized void update(LookData lookData, float scale, float desiredAccuracyLevel) {
			Log.d(TAG, "update reg: scale = " + scale + "\t desAcc: " + desiredAccuracyLevel + "\t lookData = "
							+ lookData.getLookName());
			setLookData(lookData);
			setScale(scale);
			setDesiredAccuracyLevel(desiredAccuracyLevel);
		}

		public PhysicsObject getPhysicsObject() {
			return this.physicsObject;
		}

		public float getScale() {
			return this.scale;
		}

		private void setDesiredAccuracyLevel(float desiredAccuracyLevel) {
			this.desiredAccuracyLevel = desiredAccuracyLevel;
		}

		public boolean isDesiredAccuracy() {
			return Float.compare(desiredAccuracyLevel, currentAccuracyLevel) == 0;
		}
	}
}
