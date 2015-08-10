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

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;

import org.catrobat.catroid.common.LookData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhysicsShapeBuilder {

	public static final int BASE_SHAPE_BUILDER_THREAD_PRIORITY = 3;
	public static final float[] ACCURACY_LEVELS = { 0.05f, 0.125f, 0.25f, 0.50f, 0.75f, 1.0f, 4.0f };
	public static final float MAX_ORIGINAL_PIXMAP_SIZE = 256.0f;
	public static final float COORDINATE_SCALING_DECIMAL_ACCURACY = 100.0f;
	private static final String TAG = PhysicsShapeBuilder.class.getSimpleName();
	private final Map<String, Shape[]> shapeMap = new HashMap<String, Shape[]>();
	private final Map<String, Thread> activeShapeBuilderThreadMap = new HashMap<String, Thread>();
	private PhysicsShapeBuilderStrategy strategy = new PhysicsShapeBuilderStrategyFastHull();

	public PhysicsShapeBuilder() {
	}

	public synchronized Shape[] getShape(LookData lookData, float scaleFactor) {
		float accuracyLevel = getAccuracyLevel(scaleFactor);
		String key = getShapeKey(lookData, accuracyLevel);

		if (shapeMap.containsKey(key)) {
			Log.d(TAG, "PhysicsShapeBuilder.getShape(): reusing shape " + key);
		} else if (!activeShapeBuilderThreadMap.containsKey(getThreadKey(lookData))) {
			float sizeAdjustmentScaleFactor = getSizeAdjustmentScaleFactor(lookData.getPixmap());
			buildShape(lookData, sizeAdjustmentScaleFactor, ACCURACY_LEVELS[0]);
			buildShape(lookData, sizeAdjustmentScaleFactor, getAccuracyLevel(scaleFactor));
			BaseShapeBuilder baseShapeBuilder = new BaseShapeBuilder(this, lookData, sizeAdjustmentScaleFactor);
			Thread baseShapeBuilderThread = new Thread(baseShapeBuilder, "PhysicsBaseShapeBuilderThread");
			baseShapeBuilderThread.setPriority(BASE_SHAPE_BUILDER_THREAD_PRIORITY);
			baseShapeBuilderThread.start();
			activeShapeBuilderThreadMap.put(getThreadKey(lookData), baseShapeBuilderThread);
		}

		Shape[] shapes = shapeMap.get(key);
		if (shapes == null) {
			int accuracyLevelIndex = getAccuracyLevelIndex(accuracyLevel);
			if (accuracyLevelIndex > 0) {
				accuracyLevel = ACCURACY_LEVELS[accuracyLevelIndex - 1];
				shapes = getShape(lookData, accuracyLevel);
			} else {
				Log.w(TAG, "no shapes found for " + lookData.getLookName());
				return null; // TODO[physics]: THIS NOT GOOD
			}
		}
		return scaleShapes(shapes, scaleFactor);
	}

	public float getSizeAdjustmentScaleFactor(Pixmap pixmap) {
		if (pixmap == null) {
			return 0.0f;
		}

		float sizeAdjustmentFactor = 1.0f;
		int width = pixmap.getWidth();
		int height = pixmap.getHeight();

		if (width > MAX_ORIGINAL_PIXMAP_SIZE || height > MAX_ORIGINAL_PIXMAP_SIZE) {
			if (width > height) {
				sizeAdjustmentFactor = MAX_ORIGINAL_PIXMAP_SIZE / width;
			} else {
				sizeAdjustmentFactor = MAX_ORIGINAL_PIXMAP_SIZE / height;
			}
		}
		return sizeAdjustmentFactor;
	}

	public Shape[] buildShape(LookData lookData, float sizeAdjustmentScaleFactor, float accuracy) {
		Pixmap lookDataPixmap = lookData.getPixmap();
		if (lookDataPixmap == null) {
			return null;
		}

		if (!shapeMap.containsKey(getShapeKey(lookData, accuracy))) {
			Log.d(TAG, "building shape: " + lookData.getLookName());
			int width = lookDataPixmap.getWidth();
			int height = lookDataPixmap.getHeight();
			float performanceScaleFactor = sizeAdjustmentScaleFactor * accuracy;
			int scaledWidth = Math.round(width * performanceScaleFactor);
			int scaledHeight = Math.round(height * performanceScaleFactor);

			Shape[] scaledShapes = null;
			if (scaledWidth > 0 && scaledHeight > 0) {
				Pixmap.setFilter(Pixmap.Filter.NearestNeighbour);
				//Pixmap.setFilter(Pixmap.Filter.BiLinear);
				Pixmap scaledPixmap = new Pixmap(scaledWidth, scaledHeight, lookDataPixmap.getFormat());
				scaledPixmap.drawPixmap(lookDataPixmap, 0, 0, width, height, 0, 0, scaledWidth, scaledHeight);
				synchronized (this) {
					scaledShapes = strategy.build(scaledPixmap, 1.0f);
				}
				// scale parameter of fastHull's build function not used -> scale shapes
				scaledShapes = scaleShapes(scaledShapes, 1 / performanceScaleFactor);
			}
			shapeMap.put(getShapeKey(lookData, accuracy), scaledShapes);
			PhysicsShapeUpdater.instance.update(lookData, scaledShapes, accuracy);
			return scaledShapes;
		}
		return null;
	}

	public static float getAccuracyLevel(float scaleFactor) {
		return ACCURACY_LEVELS[getAccuracyLevelIndex(scaleFactor)];
	}

	public static int getAccuracyLevelIndex(float scaleFactor) {
		if (ACCURACY_LEVELS.length > 1) {
			for (int i = 1; i < ACCURACY_LEVELS.length; i++) {
				if (scaleFactor < Math.abs(ACCURACY_LEVELS[i - 1] + ACCURACY_LEVELS[i]) / 2.0f) {
					return i - 1;
				}
			}
			return ACCURACY_LEVELS.length - 1;
		}
		return 0;
	}

	private String getShapeKey(LookData lookData, float scaleFactor) {
		return lookData.getChecksum() + (int) (scaleFactor * 100);
	}

	public static Shape[] scaleShapes(Shape[] shapes, float scaleFactor) {
		List<Shape> scaledShapes = new ArrayList<>();
		if (scaleFactor == 0.0f) {
			return null;
		}

		if (shapes != null) {
			for (Shape shape : shapes) {
				List<Vector2> vertices = new ArrayList<Vector2>();

				PolygonShape polygon = (PolygonShape) shape;
				for (int index = 0; index < polygon.getVertexCount(); index++) {
					Vector2 vertex = new Vector2();
					polygon.getVertex(index, vertex);
					vertex = scaleCoordinate(vertex, scaleFactor);
					vertices.add(vertex);
				}

				PolygonShape polygonShape = new PolygonShape();
				polygonShape.set(vertices.toArray(new Vector2[vertices.size()]));
				scaledShapes.add(polygonShape);
			}
		}

		return scaledShapes.toArray(new Shape[scaledShapes.size()]);
	}

	public static Vector2 scaleCoordinate(Vector2 vertex, float scaleFactor) {
		Vector2 v = new Vector2(vertex);
		v.x = scaleCoordinate(v.x, scaleFactor);
		v.y = scaleCoordinate(v.y, scaleFactor);
		return v;
	}

	public static float scaleCoordinate(float coord, float factor) {
		return Math.round(coord * factor * COORDINATE_SCALING_DECIMAL_ACCURACY) / COORDINATE_SCALING_DECIMAL_ACCURACY;
	}

	public String getThreadKey(LookData lookData) {
		return lookData.getChecksum();
	}

	public void cleanupFinishedThread(LookData lookData) {
		activeShapeBuilderThreadMap.put(getThreadKey(lookData), null);
	}

	public boolean isThreadRunning(LookData lookData) {
		return activeShapeBuilderThreadMap.get(getThreadKey(lookData)) != null;
	}

	private static class BaseShapeBuilder implements Runnable {

		private PhysicsShapeBuilder physicsShapeBuilder;
		private LookData lookData;
		private float sizeAdjustmentScaleFactor;

		public BaseShapeBuilder(PhysicsShapeBuilder physicsShapeBuilder, LookData lookData, float sizeAdjustmentScaleFactor) {
			this.physicsShapeBuilder = physicsShapeBuilder;
			this.lookData = lookData;
			this.sizeAdjustmentScaleFactor = sizeAdjustmentScaleFactor;
		}

		@Override
		public void run() {
			buildBaseShapes(lookData, sizeAdjustmentScaleFactor);
			physicsShapeBuilder.cleanupFinishedThread(lookData);
		}

		private void buildBaseShapes(LookData lookData, float sizeAdjustmentScaleFactor) {
			for (int i = 0; i < ACCURACY_LEVELS.length; i++) {
				physicsShapeBuilder.buildShape(lookData, sizeAdjustmentScaleFactor, ACCURACY_LEVELS[i]);
			}
		}
	}
}
