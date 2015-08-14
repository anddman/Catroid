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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class PhysicsShapeBuilder {

	public static final int BASE_SHAPE_BUILDER_THREAD_PRIORITY = 3;
	public static final float[] ACCURACY_LEVELS = { 0.05f, 0.125f, 0.25f, 0.50f, 0.75f, 1.0f, 4.0f };
	public static final float MAX_ORIGINAL_PIXMAP_SIZE = 512;
	public static final float COORDINATE_SCALING_DECIMAL_ACCURACY = 100.0f;
	private static final String TAG = PhysicsShapeBuilder.class.getSimpleName();
	private static BaseShapeBuilder baseShapeBuilder;
	protected static Thread baseShapeBuilderThread;
	private final Map<String, Shape[]> shapeMap = new HashMap<String, Shape[]>();

	private PhysicsShapeBuilderStrategy strategy = new PhysicsShapeBuilderStrategyFastHull();

	public PhysicsShapeBuilder() {
		if (baseShapeBuilder != null) {
			baseShapeBuilder.abort();
		}
		baseShapeBuilder = new BaseShapeBuilder();
		PhysicsShapeUpdater.instance.clearAll();
	}

	public Shape[] getShape(LookData lookData, float scaleFactor) {
		float accuracyLevel = getAccuracyLevel(scaleFactor);
		String shapeKey = getShapeKey(lookData, accuracyLevel);
		Shape[] shapes;

		synchronized (shapeMap) {
			if (shapeMap.containsKey(shapeKey)) {
				Log.d(TAG, "reusing shape:\t lookData: " + lookData.getLookName() + " : " + shapeKey);
			} else {
				float sizeAdjustmentScaleFactor = getSizeAdjustmentScaleFactor(lookData.getPixmap());
				buildShape(lookData, sizeAdjustmentScaleFactor, ACCURACY_LEVELS[0]);
				baseShapeBuilder.addToQueue(lookData, accuracyLevel, sizeAdjustmentScaleFactor);
				if (baseShapeBuilderThread == null || !baseShapeBuilderThread.isAlive()) {
					baseShapeBuilderThread = new Thread(baseShapeBuilder, "PhysicsBaseShapeBuilderThread");
					baseShapeBuilderThread.setPriority(BASE_SHAPE_BUILDER_THREAD_PRIORITY);
					baseShapeBuilderThread.start();
					Log.d(TAG, "baseShapeBuilderThread (re-)started");
				}
			}
			shapes = shapeMap.get(shapeKey);
		}

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
		return buildShape(lookData, sizeAdjustmentScaleFactor, accuracy, false);
	}

	public Shape[] buildShape(LookData lookData, float sizeAdjustmentScaleFactor, float accuracy, boolean
			informUpdater) {
		Pixmap lookDataPixmap = lookData.getPixmap();
		if (lookDataPixmap == null) {
			Log.w(TAG, "lookDataPixmap for look " + lookData.getLookName() + " is NULL");
			return null;
		}

		synchronized (shapeMap) {
			if (!shapeMap.containsKey(getShapeKey(lookData, accuracy))) {
				Log.d(TAG, "building shape:\t acc: " + accuracy + "\t look: " + lookData.getLookName());
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
					scaledShapes = strategy.build(scaledPixmap, 1.0f);
					// scale parameter of fastHull's build function not used -> scale shapes
					scaledShapes = scaleShapes(scaledShapes, 1 / performanceScaleFactor);
				}
				shapeMap.put(getShapeKey(lookData, accuracy), scaledShapes);
				Log.d(TAG, "shape built:\t acc: " + accuracy + "\t look: " + lookData.getLookName() + " : " +
						getShapeKey(lookData, accuracy));
				if (informUpdater) {
					PhysicsShapeUpdater.instance.inform(lookData, scaledShapes, accuracy);
				}
				if (scaledShapes == null) {
					Log.w(TAG, "scaledShapes for lookData " + lookData.getLookName() + " is NULL");
				}
				return scaledShapes;
			}
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

	private static String getShapeKey(LookData lookData, float scaleFactor) {
		return lookData.getChecksum() + String.format("%3s", String.valueOf((int)(scaleFactor * 100))).replace(" ",
				"0");
	}

	public static Shape[] scaleShapes(Shape[] shapes, float scaleFactor) {
		List<Shape> scaledShapes = new ArrayList<>();
		if (Float.compare(scaleFactor, 0.0f) == 0) {
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

	public boolean isAppropriateShapeAvailable(LookData lookData, float scale) {
		return shapeMap.containsKey(getShapeKey(lookData, getAccuracyLevel(scale)));
	}

	public boolean isThreadRunning(LookData lookData) {
		return baseShapeBuilderThread != null && baseShapeBuilderThread.isAlive();
	}

	private class BaseShapeBuilder implements Runnable {

		private final Comparator<QueuePosition> queuePositionComparator = new Comparator<QueuePosition>() {
			@Override
			public int compare(QueuePosition q1, QueuePosition q2) {
				return Float.compare(q1.getAccuracyLevel(), q2.getAccuracyLevel());
			}
		};
		private PriorityQueue<QueuePosition> highPriorityQueue = new PriorityQueue<QueuePosition>();
		private PriorityQueue<QueuePosition> lowPriorityQueue = new PriorityQueue<QueuePosition>(ACCURACY_LEVELS.length,
				queuePositionComparator);
		private boolean abort = false;

		public BaseShapeBuilder() {
		}

		@Override
		public void run() {
			while (!abort && highPriorityQueue.size() + lowPriorityQueue.size() > 0) {
				buildBaseShapes();
			}
			Log.d(TAG, "baseShapeBuilderThread finished");
		}

		private void buildBaseShapes() {
			QueuePosition q;
			while (!abort) {
				synchronized (highPriorityQueue) {
					q = highPriorityQueue.poll();
				}
				if (q != null) {
					buildShape(q.getLookData(), q.getSizeAdjustmentScaleFactor(), q.getAccuracyLevel(), true);
				} else {
					break;
				}
			}
			if (!abort) {
				synchronized (lowPriorityQueue) {
					q = lowPriorityQueue.poll();
				}
				if (q != null) {
					buildShape(q.getLookData(), q.getSizeAdjustmentScaleFactor(), q.getAccuracyLevel(), true);
				}
			}
		}

		public void abort() {
			this.abort = true;
		}

		public synchronized void addToQueue(LookData lookData, float accuracyLevel, float sizeAdjustmentScaleFactor) {
			ArrayList<QueuePosition> all = new ArrayList<>(highPriorityQueue);
			all.addAll(lowPriorityQueue);
			for (QueuePosition q : all) {
				if (q.getLookData() == lookData) {
					return;
				}
			}
			highPriorityQueue.add(new QueuePosition(lookData, accuracyLevel, sizeAdjustmentScaleFactor));
			for (int i = 0; i < ACCURACY_LEVELS.length; i++) {
				lowPriorityQueue.add(new QueuePosition(lookData, ACCURACY_LEVELS[i], sizeAdjustmentScaleFactor));
			}
		}

		private class QueuePosition implements Comparable<QueuePosition> {

			private final LookData lookData;
			private float accuracyLevel;
			private float sizeAdjustmentScaleFactor;

			public QueuePosition(LookData lookData, float accuracyLevel, float sizeAdjustmentScaleFactor) {
				this.lookData = lookData;
				this.accuracyLevel = accuracyLevel;
				this.sizeAdjustmentScaleFactor = sizeAdjustmentScaleFactor;
			}

			public int compareTo(QueuePosition other) {
				return Float.compare(this.getAccuracyLevel(), other.getAccuracyLevel());
			}

			public LookData getLookData() {
				return this.lookData;
			}

			public float getAccuracyLevel() {
				return this.accuracyLevel;
			}

			public float getSizeAdjustmentScaleFactor() {
				return this.sizeAdjustmentScaleFactor;
			}
		}
	}
}
