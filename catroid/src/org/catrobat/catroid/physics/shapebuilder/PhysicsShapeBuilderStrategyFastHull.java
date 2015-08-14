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

import org.catrobat.catroid.physics.PhysicsWorldConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public final class PhysicsShapeBuilderStrategyFastHull implements PhysicsShapeBuilderStrategy {

	private static final String TAG = PhysicsShapeBuilderStrategyFastHull.class.getSimpleName();
	private static final int MINIMUM_PIXEL_ALPHA_VALUE = 1;

	@Override
	public synchronized Shape[] build(final Pixmap pixmap, final float scale) {
		synchronized (pixmap) {
			if (pixmap == null) {
				return null;
			}

			int width = pixmap.getWidth();
			int height = pixmap.getHeight();
			float coordinateAdjustmentValue = 1.0f;
			Stack<Vector2> convexHull = new Stack<Vector2>();

			Vector2 point = new Vector2(width, height);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < point.x; x++) {
					if ((pixmap.getPixel(x, y) & 0xff) >= MINIMUM_PIXEL_ALPHA_VALUE) {
						point = new Vector2(x, y);
						addPoint(convexHull, point);
						break;
					}
				}
			}

			if (convexHull.isEmpty()) {
				return null;
			}

			for (int x = (int) point.x; x < width; x++) {
				for (int y = height - 1; y > point.y; y--) {
					if ((pixmap.getPixel(x, y) & 0xff) >= MINIMUM_PIXEL_ALPHA_VALUE) {
						point = new Vector2(x, y);
						addPoint(convexHull, new Vector2(x, y + coordinateAdjustmentValue));
						break;
					}
				}
			}

			Vector2 firstPoint = convexHull.firstElement();
			for (int y = (int) point.y; y >= firstPoint.y; y--) {
				for (int x = width - 1; x > point.x; x--) {
					if ((pixmap.getPixel(x, y) & 0xff) >= MINIMUM_PIXEL_ALPHA_VALUE) {
						point = new Vector2(x, y);
						addPoint(convexHull, new Vector2(x + coordinateAdjustmentValue, y + coordinateAdjustmentValue));
						break;
					}
				}
			}

			for (int x = (int) point.x; x > firstPoint.x; x--) {
				for (int y = (int) firstPoint.y; y < point.y; y++) {
					if ((pixmap.getPixel(x, y) & 0xff) >= MINIMUM_PIXEL_ALPHA_VALUE) {
						point = new Vector2(x, y);
						addPoint(convexHull, new Vector2(x + coordinateAdjustmentValue, y));
						break;
					}
				}
			}

			if (convexHull.size() > 2) {
				removeNonConvexPoints(convexHull, firstPoint);
			}

			Log.d(TAG, "pixmap: " + pixmap.getWidth() + "\t x \t" + pixmap.getHeight());
			for (Vector2 v : convexHull) {
				if (Math.abs(v.x) > pixmap.getWidth() || Math.abs(v.y) > pixmap.getHeight()) {
					Log.w(TAG, "undiv-outlier: " + v.x + "\t " + v.y);
				}
			}

			Shape[] dividedShapes = divideShape(convexHull.toArray(new Vector2[convexHull.size()]), width, height);

			Vector2 tmp = new Vector2();
			Vector2 v;
			PolygonShape p;
			List<Vector2> outliers = new ArrayList<Vector2>();
			for (Shape s : dividedShapes) {
				p = (PolygonShape) s;
				for (int i = 0; i < p.getVertexCount(); i++) {
					p.getVertex(i, tmp);
					v = PhysicsWorldConverter.convertBox2dToNormalVector(tmp);
					if (Math.abs(v.x) > (pixmap.getWidth()/2)+1 || Math.abs(v.y) > (pixmap.getHeight()/2)+1) {
						Log.w(TAG, "div-outlier: " + v.x + "\t " + v.y);
						outliers.add(tmp);
					}
				}
				//if (outliers.size() > 0) {
				//	List<Vector2> vertices = new ArrayList<>();
				//	PolygonShape cleaned = new PolygonShape();
				//	for (int i = 0; i < p.getVertexCount(); i++) {
				//		p.getVertex(i, tmp);
				//		if (!outliers.contains(tmp)) {
				//			vertices.add(tmp);
				//		}
				//	}
				//	cleaned.set(vertices.toArray(new Vector2[3]));
				//}
			}

			return dividedShapes;
		}
	}

	private synchronized void addPoint(Stack<Vector2> convexHull, Vector2 point) {
		removeNonConvexPoints(convexHull, point);
		convexHull.add(point);
	}

	private synchronized void removeNonConvexPoints(Stack<Vector2> convexHull, Vector2 newTop) {
		while (convexHull.size() > 1) {
			Vector2 top = convexHull.peek();
			Vector2 secondTop = convexHull.get(convexHull.size() - 2);

			if (leftTurn(secondTop, top, newTop)) {
				break;
			}

			if (top.y > newTop.y && top.y > secondTop.y) {
				break;
			}

			convexHull.pop();
		}
	}

	private synchronized boolean leftTurn(Vector2 a, Vector2 b, Vector2 c) {
		return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) < 0;
	}

	private synchronized Shape[] divideShape(Vector2[] convexPoints, int width, int height) {
		for (int index = 0; index < convexPoints.length; index++) {
			Vector2 point = convexPoints[index];
			point.x -= width / 2.0f;
			point.y = height / 2.0f - point.y;
			convexPoints[index] = PhysicsWorldConverter.convertCatroidToBox2dVector(point);
		}

		if (convexPoints.length < 9) {
			PolygonShape polygon = new PolygonShape();
			polygon.set(convexPoints);
			return new Shape[] { polygon };
		}

		List<Shape> shapes = new ArrayList<Shape>(convexPoints.length / 6 + 1);
		List<Vector2> pointsPerShape = new ArrayList<Vector2>(8);

		Vector2 rome = convexPoints[0];
		int index = 1;
		while (index < convexPoints.length - 1) {
			int k = index + 7;

			int remainingPointsCount = convexPoints.length - index;
			if (remainingPointsCount > 7 && remainingPointsCount < 9) {
				k -= 3;
			}

			pointsPerShape.add(rome);
			for (; index < k && index < convexPoints.length; index++) {
				pointsPerShape.add(convexPoints[index]);
			}

			PolygonShape polygon = new PolygonShape();
			polygon.set(pointsPerShape.toArray(new Vector2[pointsPerShape.size()]));
			shapes.add(polygon);

			pointsPerShape.clear();
			index--;
		}

		return shapes.toArray(new Shape[shapes.size()]);
	}
}
