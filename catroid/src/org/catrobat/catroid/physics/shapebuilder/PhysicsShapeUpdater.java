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
	private HashMap<LookData, ArrayList<Registration>> lookDataRegistrationMap = new HashMap<LookData, ArrayList<Registration>>();

	private PhysicsShapeUpdater() {
	}

	public synchronized void register(LookData lookData, PhysicsObject physicsObject, float scale, float desiredAccuracyLevel) {
		Registration reg = physicsObjectRegistrationMap.get(physicsObject);
		if (reg == null) {
			reg = new Registration(lookData, physicsObject, scale, desiredAccuracyLevel);
			physicsObjectRegistrationMap.put(physicsObject, reg);
		} else {
			lookDataRegistrationMap.get(reg.getLookData()).remove(physicsObject);
			reg.update(lookData, scale);
		}

		ArrayList<Registration> registrations = lookDataRegistrationMap.get(lookData);
		if (registrations == null) {
			registrations = new ArrayList<Registration>();
			lookDataRegistrationMap.put(lookData, registrations);
		}
		registrations.add(reg);
	}

	public synchronized void update(LookData lookData, Shape[] shapes, float newAccuracyLevel) {
		ArrayList<Registration> registrations = lookDataRegistrationMap.get(lookData);
		ArrayList<Registration> unregisterList = new ArrayList<Registration>();

		for (Registration registration : registrations) {
			registration.updateShapes(shapes, newAccuracyLevel);
			if (registration.isDesiredAccuracy()) {
				unregisterList.add(registration);
			}
		}

		for (Registration unreg: unregisterList) {
			unregister(unreg);
		}
	}

	private synchronized void unregister(Registration registration) {
		physicsObjectRegistrationMap.remove(registration.getPhysicsObject());
		lookDataRegistrationMap.get(registration.getLookData()).remove(registration);
	}


	private class Registration {

		private LookData lookData;
		private PhysicsObject physicsObject;
		private float scale = 1.0f;
		private float desiredAccuracyLevel = 1.0f;
		private float currentAccuracyLevel = Float.MIN_VALUE;

		public Registration(LookData lookData, PhysicsObject physicsObject, float scale, float desiredAccuracyLevel) {
			this.lookData = lookData;
			this.physicsObject = physicsObject;
			this.scale = scale;
			this.desiredAccuracyLevel = desiredAccuracyLevel;
		}

		public synchronized void updateShapes(Shape[] shapes, float newAccuracyLevel) {
			if (Math.abs(desiredAccuracyLevel - newAccuracyLevel) < Math.abs(desiredAccuracyLevel - currentAccuracyLevel)) {
				Log.d(TAG, "updating lookData: " + lookData.getLookName());
				shapes = PhysicsShapeBuilder.scaleShapes(shapes, scale);
				physicsObject.setShape(shapes);
				this.currentAccuracyLevel = newAccuracyLevel;
			}
		}

		public LookData getLookData() {
			return this.lookData;
		}

		public void setLookData(LookData lookData) {
			this.lookData = lookData;
			this.currentAccuracyLevel = Float.MIN_VALUE;
		}

		public void update(LookData lookData, float scale) {
			setLookData(lookData);
			this.scale = scale;
		}

		public PhysicsObject getPhysicsObject() {
			return this.physicsObject;
		}

		public boolean isDesiredAccuracy() {
			return Float.compare(desiredAccuracyLevel, currentAccuracyLevel) == 0;
		}
	}
}