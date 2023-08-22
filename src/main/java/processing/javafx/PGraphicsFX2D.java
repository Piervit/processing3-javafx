/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2015 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.javafx;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PMatrix;
import processing.core.PMatrix2D;
import processing.core.PMatrix3D;
import processing.core.PShape;
import processing.core.PShapeSVG;
import processing.core.PSurface;

public class PGraphicsFX2D extends PGraphics {
	GraphicsContext context;

	static final WritablePixelFormat<IntBuffer> argbFormat = PixelFormat.getIntArgbInstance();

	WritableImage snapshotImage;

	/**
	 * indicate the number of drawing state done by workPath This is created in the
	 * context of migrating workPath variable from com.sun.javafx.geom.Path2D to
	 * GraphicsContext. So it will try to mimic the result of Path2D.getNumCommand()
	 * functions.
	 * 
	 */
	int numStep;
	/**
	 * We need to have the same numStep variable for when we are in contour mode.
	 */
	int numStepContour;

	// Path2D auxPath = new Path2D();
	/**
	 * True if we are in contour mode
	 */
	boolean openContour;
	boolean adjustedForThinLines;
	/// break the shape at the next vertex (next vertex() call is a moveto())
	boolean breakShape;

	private float pathCoordsBuffer[] = new float[6];

	/// coordinates for internal curve calculation
	float[] curveCoordX;
	float[] curveCoordY;
	float[] curveDrawX;
	float[] curveDrawY;

	int transformCount;
	Affine transformStack[] = new Affine[MATRIX_STACK_DEPTH];

//  Line2D.Float line = new Line2D.Float();
//  Ellipse2D.Float ellipse = new Ellipse2D.Float();
//  Rectangle2D.Float rect = new Rectangle2D.Float();
//  Arc2D.Float arc = new Arc2D.Float();
//
//  protected Color tintColorObject;
//
//  protected Color fillColorObject;
//  public boolean fillGradient;
//  public Paint fillGradientObject;
//
//  protected Color strokeColorObject;
//  public boolean strokeGradient;
//  public Paint strokeGradientObject;

	//////////////////////////////////////////////////////////////

	// INTERNAL

	public PGraphicsFX2D() {

	}

	// public void setParent(PApplet parent)

	// public void setPrimary(boolean primary)

	// public void setPath(String path)

	// public void setSize(int width, int height)

	// public void dispose()

	private void numStepInit() {
		if (this.openContour) {
			this.numStepContour = 0;
		} else {
			this.numStep = 0;
		}
	}

	private int getCurrentNumStep() {
		if (this.openContour) {
			return this.numStepContour;
		}
		return this.numStep;
	}

	private void increNumStep() {
		if (this.openContour) {
			this.numStepContour++;
		} else {
			this.numStep++;
		}
	}

	@Override
	public PSurface createSurface() {
		this.surface = new PSurfaceFX(this);
		this.context = (((PSurfaceFX) this.surface).canvas).getGraphicsContext2D();
		return this.surface;
	}

	/** Returns the javafx.scene.canvas.GraphicsContext used by this renderer. */
	@Override
	public Object getNative() {
		return (((PSurfaceFX) this.surface).canvas);
	}

	//////////////////////////////////////////////////////////////

	// FRAME

//  @Override
//  public boolean canDraw() {
//    return true;
//  }

	@Override
	public void beginDraw() {
		this.checkSettings();
		this.resetMatrix(); // reset model matrix
		this.vertexCount = 0;
	}

	@Override
	public void endDraw() {
		this.flush();

		if (!this.primaryGraphics) {
			// TODO this is probably overkill for most tasks...
			this.loadPixels();
		}
	}

	//////////////////////////////////////////////////////////////

	// SETTINGS

	// protected void checkSettings()

	// protected void defaultSettings()

	// protected void reapplySettings()

	//////////////////////////////////////////////////////////////

	// HINT

	// public void hint(int which)

	//////////////////////////////////////////////////////////////

	// SHAPE CREATION

	// protected PShape createShapeFamily(int type)

	// protected PShape createShapePrimitive(int kind, float... p)

	//////////////////////////////////////////////////////////////

	// SHAPE

	@Override
	public void beginShape(int kind) {
		this.shape = kind;
		this.vertexCount = 0;
		this.curveVertexCount = 0;
		this.numStepInit();

		this.context.beginPath();
		// this.auxPath.beginPath();

		this.flushPixels();

		if (this.drawingThinLines()) {
			this.adjustedForThinLines = true;
			this.translate(0.5f, 0.5f);
		}
	}

	// public boolean edge(boolean e)

	// public void normal(float nx, float ny, float nz) {

	// public void textureMode(int mode)

	@Override
	public void texture(PImage image) {
		showMethodWarning("texture");
	}

	@Override
	public void vertex(float x, float y) {
		if (this.vertexCount == this.vertices.length) {
			float temp[][] = new float[this.vertexCount << 1][VERTEX_FIELD_COUNT];
			System.arraycopy(this.vertices, 0, temp, 0, this.vertexCount);
			this.vertices = temp;
			// message(CHATTER, "allocating more vertices " + vertices.length);
		}
		// not everyone needs this, but just easier to store rather
		// than adding another moving part to the code...
		this.vertices[this.vertexCount][X] = x;
		this.vertices[this.vertexCount][Y] = y;
		this.vertexCount++;

		switch (this.shape) {

		case POINTS:
			this.point(x, y);
			break;

		case LINES:
			if ((this.vertexCount % 2) == 0) {
				this.line(this.vertices[this.vertexCount - 2][X], this.vertices[this.vertexCount - 2][Y], x, y);
			}
			break;

		case TRIANGLES:
			if ((this.vertexCount % 3) == 0) {
				this.triangle(this.vertices[this.vertexCount - 3][X], this.vertices[this.vertexCount - 3][Y],
						this.vertices[this.vertexCount - 2][X], this.vertices[this.vertexCount - 2][Y], x, y);
			}
			break;

		case TRIANGLE_STRIP:
			if (this.vertexCount >= 3) {
				this.triangle(this.vertices[this.vertexCount - 2][X], this.vertices[this.vertexCount - 2][Y],
						this.vertices[this.vertexCount - 1][X], this.vertices[this.vertexCount - 1][Y],
						this.vertices[this.vertexCount - 3][X], this.vertices[this.vertexCount - 3][Y]);
			}
			break;

		case TRIANGLE_FAN:
			if (this.vertexCount >= 3) {
				// This is an unfortunate implementation because the stroke for an
				// adjacent triangle will be repeated. However, if the stroke is not
				// redrawn, it will replace the adjacent line (when it lines up
				// perfectly) or show a faint line (when off by a small amount).
				// The alternative would be to wait, then draw the shape as a
				// polygon fill, followed by a series of vertices. But that's a
				// poor method when used with PDF, DXF, or other recording objects,
				// since discrete triangles would likely be preferred.
				this.triangle(this.vertices[0][X], this.vertices[0][Y], this.vertices[this.vertexCount - 2][X],
						this.vertices[this.vertexCount - 2][Y], x, y);
			}
			break;

		case QUAD:
		case QUADS:
			if ((this.vertexCount % 4) == 0) {
				this.quad(this.vertices[this.vertexCount - 4][X], this.vertices[this.vertexCount - 4][Y],
						this.vertices[this.vertexCount - 3][X], this.vertices[this.vertexCount - 3][Y],
						this.vertices[this.vertexCount - 2][X], this.vertices[this.vertexCount - 2][Y], x, y);
			}
			break;

		case QUAD_STRIP:
			// 0---2---4
			// | | |
			// 1---3---5
			if ((this.vertexCount >= 4) && ((this.vertexCount % 2) == 0)) {
				this.quad(this.vertices[this.vertexCount - 4][X], this.vertices[this.vertexCount - 4][Y],
						this.vertices[this.vertexCount - 2][X], this.vertices[this.vertexCount - 2][Y], x, y,
						this.vertices[this.vertexCount - 3][X], this.vertices[this.vertexCount - 3][Y]);
			}
			break;

		case POLYGON:
			if ((this.getCurrentNumStep() == 0) || this.breakShape) {
				this.context.moveTo(x, y);
				this.increNumStep();
				this.breakShape = false;
			} else {
				this.context.lineTo(x, y);
				this.increNumStep();
			}
			break;
		}
	}

	@Override
	public void vertex(float x, float y, float z) {
		showDepthWarningXYZ("vertex");
	}

	@Override
	public void vertex(float[] v) {
		this.vertex(v[X], v[Y]);
	}

	@Override
	public void vertex(float x, float y, float u, float v) {
		showVariationWarning("vertex(x, y, u, v)");
	}

	@Override
	public void vertex(float x, float y, float z, float u, float v) {
		showDepthWarningXYZ("vertex");
	}

	@Override
	public void beginContour() {
		if (this.openContour) {
			PGraphics.showWarning("Already called beginContour()");
			return;
		}

		this.openContour = true;
		if (this.getCurrentNumStep() > 0) { // first contour does not break
			this.breakShape = true;
		}

	}

	@Override
	public void endContour() {
		if (!this.openContour) {
			PGraphics.showWarning("Need to call beginContour() first");
			return;
		}

		this.openContour = false;
		if (this.getCurrentNumStep() > 0) {
			this.context.closePath();
		}

	}

	@Override
	public void endShape(int mode) {
		if (this.openContour) { // correct automagically, notify user
			this.endContour();
			PGraphics.showWarning("Missing endContour() before endShape()");
		}
		if (this.getCurrentNumStep() > 0) {
			if (this.shape == POLYGON) {
				if (mode == CLOSE) {
					this.context.closePath();
					this.increNumStep();
				}
				// DELETED by Pierre Vittet .
				// if (this.auxPath.getNumCommands() > 0) {
				// this.workPath.append(this.auxPath, false);
				// }
				this.drawShape();
			}
		}
		this.shape = 0;
		if (this.adjustedForThinLines) {
			this.adjustedForThinLines = false;
			this.translate(-0.5f, -0.5f);
		}
		this.loaded = false;
	}

	private void drawShape() {
		if (this.fill) {
			this.context.fill();
		}
		if (this.stroke) {
			this.context.stroke();
		}
	}

	//////////////////////////////////////////////////////////////

	// CLIPPING

	@Override
	protected void clipImpl(float x1, float y1, float x2, float y2) {
		// g2.setClip(new Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1));
		showTodoWarning("clip()", 3274);
	}

	@Override
	public void noClip() {
		// g2.setClip(null);
		showTodoWarning("noClip()", 3274);
	}

	//////////////////////////////////////////////////////////////

	// BLEND

	@Override
	protected void blendModeImpl() {
		BlendMode mode = BlendMode.SRC_OVER;
		switch (this.blendMode) {
		case REPLACE:
			showWarning("blendMode(REPLACE) is not supported");
			break;
		case BLEND:
			break; // this is SRC_OVER, the default
		case ADD:
			mode = BlendMode.ADD;
			break; // everyone's favorite
		case SUBTRACT:
			showWarning("blendMode(SUBTRACT) is not supported");
			break;
		case LIGHTEST:
			mode = BlendMode.LIGHTEN;
			break;
		case DARKEST:
			mode = BlendMode.DARKEN;
			break;
		case DIFFERENCE:
			mode = BlendMode.DIFFERENCE;
			break;
		case EXCLUSION:
			mode = BlendMode.EXCLUSION;
			break;
		case MULTIPLY:
			mode = BlendMode.MULTIPLY;
			break;
		case SCREEN:
			mode = BlendMode.SCREEN;
			break;
		case OVERLAY:
			mode = BlendMode.OVERLAY;
			break;
		case HARD_LIGHT:
			mode = BlendMode.HARD_LIGHT;
			break;
		case SOFT_LIGHT:
			mode = BlendMode.SOFT_LIGHT;
			break;
		case DODGE:
			mode = BlendMode.COLOR_DODGE;
			break;
		case BURN:
			mode = BlendMode.COLOR_BURN;
			break;
		}
		this.context.setGlobalBlendMode(mode);
	}

	//////////////////////////////////////////////////////////////

	// BEZIER VERTICES

	@Override
	protected void bezierVertexCheck() {
		if ((this.shape == 0) || (this.shape != POLYGON)) {
			throw new RuntimeException(
					"beginShape() or beginShape(POLYGON) " + "must be used before bezierVertex() or quadraticVertex()");
		}
		if (this.getCurrentNumStep() == 0) {
			throw new RuntimeException(
					"vertex() must be used at least once " + "before bezierVertex() or quadraticVertex()");
		}
	}

	@Override
	public void bezierVertex(float x1, float y1, float x2, float y2, float x3, float y3) {
		this.bezierVertexCheck();
		this.context.bezierCurveTo(x1, y1, x2, y2, x3, y3);
		this.increNumStep();
	}

	@Override
	public void bezierVertex(float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
		showDepthWarningXYZ("bezierVertex");
	}

	//////////////////////////////////////////////////////////////

	// QUADRATIC BEZIER VERTICES

	@Override
	public void quadraticVertex(float ctrlX, float ctrlY, float endX, float endY) {
		this.bezierVertexCheck();
		this.context.quadraticCurveTo(ctrlX, ctrlY, endX, endY);
		this.increNumStep();
	}

	@Override
	public void quadraticVertex(float x2, float y2, float z2, float x4, float y4, float z4) {
		showDepthWarningXYZ("quadVertex");
	}

	//////////////////////////////////////////////////////////////

	// CURVE VERTICES

	@Override
	protected void curveVertexSegment(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
		if (this.curveCoordX == null) {
			this.curveCoordX = new float[4];
			this.curveCoordY = new float[4];
			this.curveDrawX = new float[4];
			this.curveDrawY = new float[4];
		}

		this.curveCoordX[0] = x1;
		this.curveCoordY[0] = y1;

		this.curveCoordX[1] = x2;
		this.curveCoordY[1] = y2;

		this.curveCoordX[2] = x3;
		this.curveCoordY[2] = y3;

		this.curveCoordX[3] = x4;
		this.curveCoordY[3] = y4;

		this.curveToBezierMatrix.mult(this.curveCoordX, this.curveDrawX);
		this.curveToBezierMatrix.mult(this.curveCoordY, this.curveDrawY);

		// since the paths are continuous,
		// only the first point needs the actual moveto
		if (this.getCurrentNumStep() == 0) {
			this.context.moveTo(this.curveDrawX[0], this.curveDrawY[0]);
			this.increNumStep();
			this.breakShape = false;
		}

		this.context.bezierCurveTo(this.curveDrawX[1], this.curveDrawY[1], this.curveDrawX[2], this.curveDrawY[2],
				this.curveDrawX[3], this.curveDrawY[3]);
	}

	@Override
	public void curveVertex(float x, float y, float z) {
		showDepthWarningXYZ("curveVertex");
	}

	//////////////////////////////////////////////////////////////

	// RENDERER

	@Override
	public void flush() {
		this.flushPixels();
	}

	protected void flushPixels() {
		boolean hasPixels = this.modified && (this.pixels != null);
		if (hasPixels) {
			// If the user has been manipulating individual pixels,
			// the changes need to be copied to the screen before
			// drawing any new geometry.
			int mx1 = this.getModifiedX1();
			int mx2 = this.getModifiedX2();
			int my1 = this.getModifiedY1();
			int my2 = this.getModifiedY2();
			int mw = mx2 - mx1;
			int mh = my2 - my1;

			if (this.pixelDensity == 1) {
				PixelWriter pw = this.context.getPixelWriter();
				pw.setPixels(mx1, my1, mw, mh, argbFormat, this.pixels, mx1 + (my1 * this.pixelWidth), this.pixelWidth);
			} else {
				// The only way to push all the pixels is to draw a scaled-down image
				if ((this.snapshotImage == null) || (this.snapshotImage.getWidth() != this.pixelWidth)
						|| (this.snapshotImage.getHeight() != this.pixelHeight)) {
					this.snapshotImage = new WritableImage(this.pixelWidth, this.pixelHeight);
				}

				PixelWriter pw = this.snapshotImage.getPixelWriter();
				pw.setPixels(mx1, my1, mw, mh, argbFormat, this.pixels, mx1 + (my1 * this.pixelWidth), this.pixelWidth);
				this.context.save();
				this.resetMatrix();
				this.context.scale(1d / this.pixelDensity, 1d / this.pixelDensity);
				this.context.drawImage(this.snapshotImage, mx1, my1, mw, mh, mx1, my1, mw, mh);
				this.context.restore();
			}
		}

		this.modified = false;
	}

	protected void beforeContextDraw() {
		this.flushPixels();
		this.loaded = false;
	}

	//////////////////////////////////////////////////////////////

	// POINT, LINE, TRIANGLE, QUAD

	@Override
	public void point(float x, float y) {
		if (this.stroke) {
//      if (strokeWeight > 1) {
			this.line(x, y, x + EPSILON, y + EPSILON);
//      } else {
//        set((int) screenX(x, y), (int) screenY(x, y), strokeColor);
//      }
		}
	}

	@Override
	public void line(float x1, float y1, float x2, float y2) {
		this.beforeContextDraw();
		if (this.drawingThinLines()) {
			x1 += 0.5f;
			x2 += 0.5f;
			y1 += 0.5f;
			y2 += 0.5f;
		}
		this.context.strokeLine(x1, y1, x2, y2);
	}

	@Override
	public void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
		this.beforeContextDraw();
		if (this.drawingThinLines()) {
			x1 += 0.5f;
			x2 += 0.5f;
			x3 += 0.5f;
			y1 += 0.5f;
			y2 += 0.5f;
			y3 += 0.5f;
		}
		this.context.beginPath();
		this.context.moveTo(x1, y1);
		this.context.lineTo(x2, y2);
		this.context.lineTo(x3, y3);
		this.context.closePath();
		if (this.fill) {
			this.context.fill();
		}
		if (this.stroke) {
			this.context.stroke();
		}
	}

	@Override
	public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
		this.beforeContextDraw();
		if (this.drawingThinLines()) {
			x1 += 0.5f;
			x2 += 0.5f;
			x3 += 0.5f;
			x4 += 0.5f;
			y1 += 0.5f;
			y2 += 0.5f;
			y3 += 0.5f;
			y4 += 0.5f;
		}
		this.context.beginPath();
		this.context.moveTo(x1, y1);
		this.context.lineTo(x2, y2);
		this.context.lineTo(x3, y3);
		this.context.lineTo(x4, y4);
		this.context.closePath();
		if (this.fill) {
			this.context.fill();
		}
		if (this.stroke) {
			this.context.stroke();
		}
	}

	//////////////////////////////////////////////////////////////

	// RECT

	// public void rectMode(int mode)

	// public void rect(float a, float b, float c, float d)

	@Override
	protected void rectImpl(float x1, float y1, float x2, float y2) {
		this.beforeContextDraw();
		if (this.drawingThinLines()) {
			x1 += 0.5f;
			x2 += 0.5f;
			y1 += 0.5f;
			y2 += 0.5f;
		}
		if (this.fill) {
			this.context.fillRect(x1, y1, x2 - x1, y2 - y1);
		}
		if (this.stroke) {
			this.context.strokeRect(x1, y1, x2 - x1, y2 - y1);
		}
	}

	//////////////////////////////////////////////////////////////

	// ELLIPSE

	// public void ellipseMode(int mode)

	// public void ellipse(float a, float b, float c, float d)

	@Override
	protected void ellipseImpl(float x, float y, float w, float h) {
		this.beforeContextDraw();
		if (this.drawingThinLines()) {
			x += 0.5f;
			y += 0.5f;
		}
		if (this.fill) {
			this.context.fillOval(x, y, w, h);
		}
		if (this.stroke) {
			this.context.strokeOval(x, y, w, h);
		}
	}

	//////////////////////////////////////////////////////////////

	// ARC

	// public void arc(float a, float b, float c, float d,
	// float start, float stop)

	@Override
	protected void arcImpl(float x, float y, float w, float h, float start, float stop, int mode) {
		this.beforeContextDraw();

		if (this.drawingThinLines()) {
			x += 0.5f;
			y += 0.5f;
		}

		// 0 to 90 in java would be 0 to -90 for p5 renderer
		// but that won't work, so -90 to 0?
		start = -start;
		stop = -stop;

		float sweep = stop - start;

		// The defaults, before 2.0b7, were to stroke as Arc2D.OPEN, and then fill
		// using Arc2D.PIE. That's a little wonky, but it's here for compatability.
		ArcType fillMode = ArcType.ROUND; // Arc2D.PIE
		ArcType strokeMode = ArcType.OPEN;

		if (mode == OPEN) {
			fillMode = ArcType.OPEN;

		} else if (mode == PIE) {
			strokeMode = ArcType.ROUND; // PIE

		} else if (mode == CHORD) {
			fillMode = ArcType.CHORD;
			strokeMode = ArcType.CHORD;
		}

		if (this.fill) {
			this.context.fillArc(x, y, w, h, PApplet.degrees(start), PApplet.degrees(sweep), fillMode);
		}
		if (this.stroke) {
			this.context.strokeArc(x, y, w, h, PApplet.degrees(start), PApplet.degrees(sweep), strokeMode);
		}
	}

	//////////////////////////////////////////////////////////////

	// BOX

	// public void box(float size)

	@Override
	public void box(float w, float h, float d) {
		showMethodWarning("box");
	}

	//////////////////////////////////////////////////////////////

	// SPHERE

	// public void sphereDetail(int res)

	// public void sphereDetail(int ures, int vres)

	@Override
	public void sphere(float r) {
		showMethodWarning("sphere");
	}

	//////////////////////////////////////////////////////////////

	// BEZIER

	// public float bezierPoint(float a, float b, float c, float d, float t)

	// public float bezierTangent(float a, float b, float c, float d, float t)

	// protected void bezierInitCheck()

	// protected void bezierInit()

	/** Ignored (not needed) by this renderer. */
	@Override
	public void bezierDetail(int detail) {
	}

	// public void bezier(float x1, float y1,
	// float x2, float y2,
	// float x3, float y3,
	// float x4, float y4)

	// public void bezier(float x1, float y1, float z1,
	// float x2, float y2, float z2,
	// float x3, float y3, float z3,
	// float x4, float y4, float z4)

	//////////////////////////////////////////////////////////////

	// CURVE

	// public float curvePoint(float a, float b, float c, float d, float t)

	// public float curveTangent(float a, float b, float c, float d, float t)

	/** Ignored (not needed) by this renderer. */
	@Override
	public void curveDetail(int detail) {
	}

	// public void curveTightness(float tightness)

	// protected void curveInitCheck()

	// protected void curveInit()

	// public void curve(float x1, float y1,
	// float x2, float y2,
	// float x3, float y3,
	// float x4, float y4)

	// public void curve(float x1, float y1, float z1,
	// float x2, float y2, float z2,
	// float x3, float y3, float z3,
	// float x4, float y4, float z4)

	//////////////////////////////////////////////////////////////

	// SMOOTH

//  @Override
//  public void smooth() {
//    smooth = true;
//
//    if (quality == 0) {
//      quality = 4;  // change back to bicubic
//    }
//  }

//  @Override
//  public void smooth(int quality) {
////    this.quality = quality;
////    if (quality == 0) {
////      noSmooth();
////    } else {
////      smooth();
////    }
//    showMissingWarning("smooth");
//  }
//
//
//  @Override
//  public void noSmooth() {
//    showMissingWarning("noSmooth");
//  }

	//////////////////////////////////////////////////////////////

	// IMAGE

	// public void imageMode(int mode)

	// public void image(PImage image, float x, float y)

	// public void image(PImage image, float x, float y, float c, float d)

	// public void image(PImage image,
	// float a, float b, float c, float d,
	// int u1, int v1, int u2, int v2)

	/**
	 * Handle renderer-specific image drawing.
	 */
	@Override
	protected void imageImpl(PImage who, float x1, float y1, float x2, float y2, int u1, int v1, int u2, int v2) {
		// Image not ready yet, or an error
		if ((who.width <= 0) || (who.height <= 0)) {
			return;
		}

		ImageCache cash = (ImageCache) this.getCache(who);

		// Nuke the cache if the image was resized
		if (cash != null) {
			if ((who.pixelWidth != cash.image.getWidth()) || (who.pixelHeight != cash.image.getHeight())) {
				cash = null;
			}
		}

		if (cash == null) {
			// System.out.println("making new image cache");
			cash = new ImageCache(); // who);
			this.setCache(who, cash);
			who.updatePixels(); // mark the whole thing for update
			who.setModified();
		}

		// If image previously was tinted, or the color changed
		// or the image was tinted, and tint is now disabled
		if ((this.tint && !cash.tinted) || (this.tint && (cash.tintedColor != this.tintColor))
				|| (!this.tint && cash.tinted)) {
			// For tint change, mark all pixels as needing update.
			who.updatePixels();
		}

		if (who.isModified()) {
			if (who.pixels == null) {
				// This might be a PGraphics that hasn't been drawn to yet.
				// Can't just bail because the cache has been created above.
				// https://github.com/processing/processing/issues/2208
				who.pixels = new int[who.pixelWidth * who.pixelHeight];
			}
			cash.update(who, this.tint, this.tintColor);
			who.setModified(false);
		}

		u1 *= who.pixelDensity;
		v1 *= who.pixelDensity;
		u2 *= who.pixelDensity;
		v2 *= who.pixelDensity;

		this.context.drawImage(((ImageCache) this.getCache(who)).image, u1, v1, u2 - u1, v2 - v1, x1, y1, x2 - x1,
				y2 - y1);
	}

	static class ImageCache {
		boolean tinted;
		int tintedColor;
		int[] tintedTemp; // one row of tinted pixels
		// BufferedImage image;
		WritableImage image;

		/**
		 * Update the pixels of the cache image. Already determined that the tint has
		 * changed, or the pixels have changed, so should just go through with the
		 * update without further checks.
		 */
		public void update(PImage source, boolean tint, int tintColor) {
			// int bufferType = BufferedImage.TYPE_INT_ARGB;
			int targetType = ARGB;
			boolean opaque = (tintColor & 0xFF000000) == 0xFF000000;
			if (source.format == RGB) {
				if (!tint || (tint && opaque)) {
					// bufferType = BufferedImage.TYPE_INT_RGB;
					targetType = RGB;
				}
			}
//      boolean wrongType = (image != null) && (image.getType() != bufferType);
//      if ((image == null) || wrongType) {
//        image = new BufferedImage(source.width, source.height, bufferType);
//      }
			// Must always use an ARGB image, otherwise will write zeros
			// in the alpha channel when drawn to the screen.
			// https://github.com/processing/processing/issues/2030
//      if (image == null) {
//        image = new BufferedImage(source.width, source.height,
//                                  BufferedImage.TYPE_INT_ARGB);
//      }
			if (this.image == null) {
				this.image = new WritableImage(source.pixelWidth, source.pixelHeight);
			}

			// WritableRaster wr = image.getRaster();
			PixelWriter pw = this.image.getPixelWriter();
			if (tint) {
				if ((this.tintedTemp == null) || (this.tintedTemp.length != source.pixelWidth)) {
					this.tintedTemp = new int[source.pixelWidth];
				}
				int a2 = (tintColor >> 24) & 0xff;
//        System.out.println("tint color is " + a2);
//        System.out.println("source.pixels[0] alpha is " + (source.pixels[0] >>> 24));
				int r2 = (tintColor >> 16) & 0xff;
				int g2 = (tintColor >> 8) & 0xff;
				int b2 = (tintColor) & 0xff;

				// if (bufferType == BufferedImage.TYPE_INT_RGB) {
				if (targetType == RGB) {
					// The target image is opaque, meaning that the source image has no
					// alpha (is not ARGB), and the tint has no alpha.
					int index = 0;
					for (int y = 0; y < source.pixelHeight; y++) {
						for (int x = 0; x < source.pixelWidth; x++) {
							int argb1 = source.pixels[index++];
							int r1 = (argb1 >> 16) & 0xff;
							int g1 = (argb1 >> 8) & 0xff;
							int b1 = (argb1) & 0xff;

							// Prior to 2.1, the alpha channel was commented out here,
							// but can't remember why (just thought unnecessary b/c of RGB?)
							// https://github.com/processing/processing/issues/2030
							this.tintedTemp[x] = 0xFF000000 | (((r2 * r1) & 0xff00) << 8) | ((g2 * g1) & 0xff00)
									| (((b2 * b1) & 0xff00) >> 8);
						}
						// wr.setDataElements(0, y, source.width, 1, tintedTemp);
						pw.setPixels(0, y, source.pixelWidth, 1, argbFormat, this.tintedTemp, 0, source.pixelWidth);
					}
					// could this be any slower?
//          float[] scales = { tintR, tintG, tintB };
//          float[] offsets = new float[3];
//          RescaleOp op = new RescaleOp(scales, offsets, null);
//          op.filter(image, image);

					// } else if (bufferType == BufferedImage.TYPE_INT_ARGB) {
				} else if (targetType == ARGB) {
					if ((source.format == RGB) && ((tintColor & 0xffffff) == 0xffffff)) {
						int hi = tintColor & 0xff000000;
						int index = 0;
						for (int y = 0; y < source.pixelHeight; y++) {
							for (int x = 0; x < source.pixelWidth; x++) {
								this.tintedTemp[x] = hi | (source.pixels[index++] & 0xFFFFFF);
							}
							// wr.setDataElements(0, y, source.width, 1, tintedTemp);
							pw.setPixels(0, y, source.pixelWidth, 1, argbFormat, this.tintedTemp, 0, source.pixelWidth);
						}
					} else {
						int index = 0;
						for (int y = 0; y < source.pixelHeight; y++) {
							if (source.format == RGB) {
								int alpha = tintColor & 0xFF000000;
								for (int x = 0; x < source.pixelWidth; x++) {
									int argb1 = source.pixels[index++];
									int r1 = (argb1 >> 16) & 0xff;
									int g1 = (argb1 >> 8) & 0xff;
									int b1 = (argb1) & 0xff;
									this.tintedTemp[x] = alpha | (((r2 * r1) & 0xff00) << 8) | ((g2 * g1) & 0xff00)
											| (((b2 * b1) & 0xff00) >> 8);
								}
							} else if (source.format == ARGB) {
								for (int x = 0; x < source.pixelWidth; x++) {
									int argb1 = source.pixels[index++];
									int a1 = (argb1 >> 24) & 0xff;
									int r1 = (argb1 >> 16) & 0xff;
									int g1 = (argb1 >> 8) & 0xff;
									int b1 = (argb1) & 0xff;
									this.tintedTemp[x] = (((a2 * a1) & 0xff00) << 16) | (((r2 * r1) & 0xff00) << 8)
											| ((g2 * g1) & 0xff00) | (((b2 * b1) & 0xff00) >> 8);
								}
							} else if (source.format == ALPHA) {
								int lower = tintColor & 0xFFFFFF;
								for (int x = 0; x < source.pixelWidth; x++) {
									int a1 = source.pixels[index++];
									this.tintedTemp[x] = (((a2 * a1) & 0xff00) << 16) | lower;
								}
							}
							// wr.setDataElements(0, y, source.width, 1, tintedTemp);
							pw.setPixels(0, y, source.pixelWidth, 1, argbFormat, this.tintedTemp, 0, source.pixelWidth);
						}
					}
					// Not sure why ARGB images take the scales in this order...
//          float[] scales = { tintR, tintG, tintB, tintA };
//          float[] offsets = new float[4];
//          RescaleOp op = new RescaleOp(scales, offsets, null);
//          op.filter(image, image);
				}
			} else { // !tint
				if ((targetType == RGB) && ((source.pixels[0] >> 24) == 0)) {
					// If it's an RGB image and the high bits aren't set, need to set
					// the high bits to opaque because we're drawing ARGB images.
					source.filter(OPAQUE);
					// Opting to just manipulate the image here, since it shouldn't
					// affect anything else (and alpha(get(x, y)) should return 0xff).
					// Wel also make no guarantees about the values of the pixels array
					// in a PImage and how the high bits will be set.
				}
				// If no tint, just shove the pixels on in there verbatim
				// wr.setDataElements(0, 0, source.width, source.height, source.pixels);
				// System.out.println("moving the big one");
				pw.setPixels(0, 0, source.pixelWidth, source.pixelHeight, argbFormat, source.pixels, 0,
						source.pixelWidth);
			}
			this.tinted = tint;
			this.tintedColor = tintColor;

//      GraphicsConfiguration gc = parent.getGraphicsConfiguration();
//      compat = gc.createCompatibleImage(image.getWidth(),
//                                        image.getHeight(),
//                                        Transparency.TRANSLUCENT);
//
//      Graphics2D g = compat.createGraphics();
//      g.drawImage(image, 0, 0, null);
//      g.dispose();
		}
	}

	//////////////////////////////////////////////////////////////

	// SHAPE

	// public void shapeMode(int mode)

	// public void shape(PShape shape)

	// public void shape(PShape shape, float x, float y)

	// public void shape(PShape shape, float x, float y, float c, float d)

	//////////////////////////////////////////////////////////////

	// SHAPE I/O

	@Override
	public PShape loadShape(String filename) {
		return this.loadShape(filename, null);
	}

	@Override
	public PShape loadShape(String filename, String options) {
		String extension = PApplet.getExtension(filename);
		if ("svg".equals(extension) || "svgz".equals(extension)) {
			return new PShapeSVG(this.parent.loadXML(filename));
		}
		PGraphics.showWarning("Unsupported format: " + filename);
		return null;
	}

	//////////////////////////////////////////////////////////////

	// TEXT ATTRIBTUES

	protected FontCache fontCache = new FontCache();

	// Is initialized when defaultFontOrDeath() is called
	// and mirrors PGraphics.textFont field
	protected FontInfo textFontInfo;

	@Override
	protected PFont createFont(String name, float size, boolean smooth, char[] charset) {
		PFont font = super.createFont(name, size, smooth, charset);
		if (font.isStream()) {
			this.fontCache.nameToFilename.put(font.getName(), name);
		}
		return font;
	}

	@Override
	protected void defaultFontOrDeath(String method, float size) {
		super.defaultFontOrDeath(method, size);
		this.handleTextFont(this.textFont, size);
	}

	@Override
	protected boolean textModeCheck(int mode) {
		return mode == MODEL;
	}

	@Override
	public float textAscent() {
		if (this.textFont == null) {
			this.defaultFontOrDeath("textAscent");
		}
		if (this.textFontInfo.font == null) {
			return super.textAscent();
		}
		return this.textFontInfo.ascent;
	}

	@Override
	public float textDescent() {
		if (this.textFont == null) {
			this.defaultFontOrDeath("textDescent");
		}
		if (this.textFontInfo.font == null) {
			return super.textDescent();
		}
		return this.textFontInfo.descent;
	}

	static final class FontInfo {
		// TODO: this should be based on memory consumption
		// this should be enough e.g. for all grays and alpha combos
		static final int MAX_CACHED_COLORS_PER_FONT = 1 << 16;

		// used only when there is native font
		Font font;
		float ascent;
		float descent;

		// used only when there is no native font
		// maps 32-bit color to the arrays of tinted glyph images
		Map<Integer, PImage[]> tintCache;
	}

	static final class FontCache {
		static final int MAX_CACHE_SIZE = 512;

		// keeps track of filenames of fonts loaded from ttf and otf files
		Map<String, String> nameToFilename = new HashMap<>();

		// keeps track of fonts which should be rendered as pictures
		// so we don't go through native font search process every time
		final HashSet<String> nonNativeNames = new HashSet<>();

		// keeps all created fonts for reuse up to MAX_CACHE_SIZE limit
		// when the limit is reached, the least recently used font is removed
		// TODO: this should be based on memory consumtion
		final LinkedHashMap<Key, FontInfo> cache = new LinkedHashMap<Key, FontInfo>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Key, FontInfo> eldest) {
				return this.size() > MAX_CACHE_SIZE;
			}
		};

		// key for retrieving fonts from cache; don't use for insertion,
		// every font has to have its own new Key instance
		final Key retrievingKey = new Key();

		// text node used for measuring sizes of text
		final Text measuringText = new Text();

		FontInfo get(String name, float size) {
			if (this.nonNativeNames.contains(name)) {
				// Don't have native font, using glyph images.
				// Size is set to zero, because all sizes of this font
				// should share one FontInfo with one tintCache.
				size = 0;
			}
			this.retrievingKey.name = name;
			this.retrievingKey.size = size;
			return this.cache.get(this.retrievingKey);
		}

		void put(String name, float size, FontInfo fontInfo) {
			if (fontInfo.font == null) {
				// Don't have native font, using glyph images.
				// Size is set to zero, because all sizes of this font
				// should share one FontInfo with one tintCache.
				this.nonNativeNames.add(name);
				size = 0;
			}
			Key key = new Key();
			key.name = name;
			key.size = size;
			this.cache.put(key, fontInfo);
		}

		FontInfo createFontInfo(Font font) {
			FontInfo result = new FontInfo();
			result.font = font;
			if (font != null) {
				// measure ascent and descent
				this.measuringText.setFont(result.font);
				this.measuringText.setText(" ");
				float lineHeight = (float) this.measuringText.getLayoutBounds().getHeight();
				result.ascent = (float) this.measuringText.getBaselineOffset();
				result.descent = lineHeight - result.ascent;
			}
			return result;
		}

		static final class Key {
			String name;
			float size;

			@Override
			public boolean equals(Object o) {
				if (this == o) {
					return true;
				}
				if ((o == null) || (this.getClass() != o.getClass())) {
					return false;
				}
				Key that = (Key) o;
				if (Float.compare(that.size, this.size) != 0) {
					return false;
				}
				return this.name.equals(that.name);
			}

			@Override
			public int hashCode() {
				int result = this.name.hashCode();
				result = (31 * result) + (this.size != +0.0f ? Float.floatToIntBits(this.size) : 0);
				return result;
			}
		}
	}

	///////////////////////////////////////////////////////////////

	// TEXT

	// None of the variations of text() are overridden from PGraphics.

	//////////////////////////////////////////////////////////////

	// TEXT IMPL

	@Override
	protected void textFontImpl(PFont which, float size) {
		this.handleTextFont(which, size);
		this.handleTextSize(size);
	}

	@Override
	protected void textSizeImpl(float size) {
		this.handleTextFont(this.textFont, size);
		this.handleTextSize(size);
	}

	/**
	 * FX specific. When setting font or size, new font has to be created. Both
	 * textFontImpl and textSizeImpl call this one.
	 * 
	 * @param which font to be set, not null
	 * @param size  size to be set, greater than zero
	 */
	protected void handleTextFont(PFont which, float size) {
		this.textFont = which;

		String fontName = which.getName();
		String fontPsName = which.getPostScriptName();

		this.textFontInfo = this.fontCache.get(fontName, size);
		if (this.textFontInfo == null) {
			Font font = null;

			if (which.isStream()) {
				// Load from ttf or otf file
				String filename = this.fontCache.nameToFilename.get(fontName);
				font = Font.loadFont(this.parent.createInput(filename), size);
			}

			if (font == null) {
				// Look up font name
				font = new Font(fontName, size);
				if (!fontName.equalsIgnoreCase(font.getName())) {
					// Look up font postscript name
					font = new Font(fontPsName, size);
					if (!fontPsName.equalsIgnoreCase(font.getName())) {
						font = null; // Done with it
					}
				}
			}

			if ((font == null) && (which.getNative() != null)) {
				// Ain't got nothing, but AWT has something, so glyph images are not
				// going to be used for this font; go with the default font then
				font = new Font(size);
			}

			this.textFontInfo = this.fontCache.createFontInfo(font);
			this.fontCache.put(fontName, size, this.textFontInfo);
		}

		this.context.setFont(this.textFontInfo.font);
	}

	@Override
	protected void textLineImpl(char[] buffer, int start, int stop, float x, float y) {
		if (this.textFontInfo.font == null) {
			super.textLineImpl(buffer, start, stop, x, y);
		} else {
			this.context.fillText(new String(buffer, start, stop - start), x, y);
		}
	}

	protected PImage getTintedGlyphImage(PFont.Glyph glyph, int tintColor) {
		if (this.textFontInfo.tintCache == null) {
			this.textFontInfo.tintCache = new LinkedHashMap<Integer, PImage[]>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Integer, PImage[]> eldest) {
					return this.size() > FontInfo.MAX_CACHED_COLORS_PER_FONT;
				}
			};
		}
		PImage[] tintedGlyphs = this.textFontInfo.tintCache.get(tintColor);
		int index = glyph.index;
		if ((tintedGlyphs == null) || (tintedGlyphs.length <= index)) {
			PImage[] newArray = new PImage[this.textFont.getGlyphCount()];
			if (tintedGlyphs != null) {
				System.arraycopy(tintedGlyphs, 0, newArray, 0, tintedGlyphs.length);
			}
			tintedGlyphs = newArray;
			this.textFontInfo.tintCache.put(tintColor, tintedGlyphs);
		}
		PImage tintedGlyph = tintedGlyphs[index];
		if (tintedGlyph == null) {
			tintedGlyph = glyph.image.copy();
			tintedGlyphs[index] = tintedGlyph;
		}
		return tintedGlyph;
	}

	@Override
	protected void textCharImpl(char ch, float x, float y) { // , float z) {
		PFont.Glyph glyph = this.textFont.getGlyph(ch);
		if (glyph != null) {
			if (this.textMode == MODEL) {
				float high = glyph.height / (float) this.textFont.getSize();
				float bwidth = glyph.width / (float) this.textFont.getSize();
				float lextent = glyph.leftExtent / (float) this.textFont.getSize();
				float textent = glyph.topExtent / (float) this.textFont.getSize();

				float x1 = x + (lextent * this.textSize);
				float y1 = y - (textent * this.textSize);
				float x2 = x1 + (bwidth * this.textSize);
				float y2 = y1 + (high * this.textSize);

				PImage glyphImage = (this.fillColor == 0xFFFFFFFF) ? glyph.image
						: this.getTintedGlyphImage(glyph, this.fillColor);

				this.textCharModelImpl(glyphImage, x1, y1, x2, y2, glyph.width, glyph.height);
			}
		} else if ((ch != ' ') && (ch != 127)) {
			showWarning("No glyph found for the " + ch + " (\\u" + PApplet.hex(ch, 4) + ") character");
		}
	}

	@Override
	protected float textWidthImpl(char[] buffer, int start, int stop) {
		if (this.textFont == null) {
			this.defaultFontOrDeath("textWidth");
		}

		if (this.textFontInfo.font == null) {
			return super.textWidthImpl(buffer, start, stop);
		}

		this.fontCache.measuringText.setFont(this.textFontInfo.font);
		this.fontCache.measuringText.setText(new String(buffer, start, stop - start));
		return (float) this.fontCache.measuringText.getLayoutBounds().getWidth();
	}

	//////////////////////////////////////////////////////////////

	// MATRIX STACK

	@Override
	public void pushMatrix() {
		if (this.transformCount == this.transformStack.length) {
			throw new RuntimeException(
					"pushMatrix() cannot use push more than " + this.transformStack.length + " times");
		}
		this.transformStack[this.transformCount] = this.context.getTransform(this.transformStack[this.transformCount]);
		this.transformCount++;
	}

	@Override
	public void popMatrix() {
		if (this.transformCount == 0) {
			throw new RuntimeException("missing a pushMatrix() " + "to go with that popMatrix()");
		}
		this.transformCount--;
		this.context.setTransform(this.transformStack[this.transformCount]);
	}

	//////////////////////////////////////////////////////////////

	// MATRIX TRANSFORMS

	@Override
	public void translate(float tx, float ty) {
		this.context.translate(tx, ty);
	}

	// public void translate(float tx, float ty, float tz)

	@Override
	public void rotate(float angle) {
		this.context.rotate(PApplet.degrees(angle));
	}

	@Override
	public void rotateX(float angle) {
		showDepthWarning("rotateX");
	}

	@Override
	public void rotateY(float angle) {
		showDepthWarning("rotateY");
	}

	@Override
	public void rotateZ(float angle) {
		showDepthWarning("rotateZ");
	}

	@Override
	public void rotate(float angle, float vx, float vy, float vz) {
		showVariationWarning("rotate");
	}

	@Override
	public void scale(float s) {
		this.context.scale(s, s);
	}

	@Override
	public void scale(float sx, float sy) {
		this.context.scale(sx, sy);
	}

	@Override
	public void scale(float sx, float sy, float sz) {
		showDepthWarningXYZ("scale");
	}

	@Override
	public void shearX(float angle) {
		Affine temp = new Affine();
		temp.appendShear(Math.tan(angle), 0);
		this.context.transform(temp);
	}

	@Override
	public void shearY(float angle) {
		Affine temp = new Affine();
		temp.appendShear(0, Math.tan(angle));
		this.context.transform(temp);
	}

	//////////////////////////////////////////////////////////////

	// MATRIX MORE

	@Override
	public void resetMatrix() {
		this.context.setTransform(new Affine());
	}

	// public void applyMatrix(PMatrix2D source)

	@Override
	public void applyMatrix(float n00, float n01, float n02, float n10, float n11, float n12) {
		this.context.transform(n00, n10, n01, n11, n02, n12);
	}

	// public void applyMatrix(PMatrix3D source)

	@Override
	public void applyMatrix(float n00, float n01, float n02, float n03, float n10, float n11, float n12, float n13,
			float n20, float n21, float n22, float n23, float n30, float n31, float n32, float n33) {
		showVariationWarning("applyMatrix");
	}

	//////////////////////////////////////////////////////////////

	// MATRIX GET/SET

	@Override
	public PMatrix getMatrix() {
		return this.getMatrix((PMatrix2D) null);
	}

	@Override
	public PMatrix2D getMatrix(PMatrix2D target) {
		if (target == null) {
			target = new PMatrix2D();
		}
		// double[] transform = new double[6];
		// TODO This is not tested; apparently Affine is a full 3x4
		Affine t = this.context.getTransform(); // .getMatrix(transform);
//    target.set((float) transform[0], (float) transform[2], (float) transform[4],
//               (float) transform[1], (float) transform[3], (float) transform[5]);
		target.set((float) t.getMxx(), (float) t.getMxy(), (float) t.getTx(), (float) t.getMyx(), (float) t.getMyy(),
				(float) t.getTy());
		return target;
	}

	@Override
	public PMatrix3D getMatrix(PMatrix3D target) {
		showVariationWarning("getMatrix");
		return target;
	}

	// public void setMatrix(PMatrix source)

	@Override
	public void setMatrix(PMatrix2D source) {
		this.context.setTransform(source.m00, source.m10, source.m01, source.m11, source.m02, source.m12);
	}

	@Override
	public void setMatrix(PMatrix3D source) {
		showVariationWarning("setMatrix");
	}

	@Override
	public void printMatrix() {
		this.getMatrix((PMatrix2D) null).print();
	}

//  //////////////////////////////////////////////////////////////
//
//  // CAMERA and PROJECTION
//
//  // Inherit the plaintive warnings from PGraphics
//
//
//  //public void beginCamera()
//  //public void endCamera()
//  //public void camera()
//  //public void camera(float eyeX, float eyeY, float eyeZ,
//  //                   float centerX, float centerY, float centerZ,
//  //                   float upX, float upY, float upZ)
//  //public void printCamera()
//
//  //public void ortho()
//  //public void ortho(float left, float right,
//  //                  float bottom, float top,
//  //                  float near, float far)
//  //public void perspective()
//  //public void perspective(float fov, float aspect, float near, float far)
//  //public void frustum(float left, float right,
//  //                    float bottom, float top,
//  //                    float near, float far)
//  //public void printProjection()

	//////////////////////////////////////////////////////////////

	// SCREEN and MODEL transforms

	@Override
	public float screenX(float x, float y) {
		return (float) this.context.getTransform().transform(x, y).getX();
	}

	@Override
	public float screenY(float x, float y) {
		return (float) this.context.getTransform().transform(x, y).getY();
	}

	@Override
	public float screenX(float x, float y, float z) {
		showDepthWarningXYZ("screenX");
		return 0;
	}

	@Override
	public float screenY(float x, float y, float z) {
		showDepthWarningXYZ("screenY");
		return 0;
	}

	@Override
	public float screenZ(float x, float y, float z) {
		showDepthWarningXYZ("screenZ");
		return 0;
	}

	// public float modelX(float x, float y, float z)

	// public float modelY(float x, float y, float z)

	// public float modelZ(float x, float y, float z)

//  //////////////////////////////////////////////////////////////
//
//  // STYLE
//
//  // pushStyle(), popStyle(), style() and getStyle() inherited.

	//////////////////////////////////////////////////////////////

	// STROKE CAP/JOIN/WEIGHT

	@Override
	public void strokeCap(int cap) {
		super.strokeCap(cap);
		if (this.strokeCap == ROUND) {
			this.context.setLineCap(StrokeLineCap.ROUND);
		} else if (this.strokeCap == PROJECT) {
			this.context.setLineCap(StrokeLineCap.SQUARE);
		} else {
			this.context.setLineCap(StrokeLineCap.BUTT);
		}
	}

	@Override
	public void strokeJoin(int join) {
		super.strokeJoin(join);
		if (this.strokeJoin == MITER) {
			this.context.setLineJoin(StrokeLineJoin.MITER);
		} else if (this.strokeJoin == ROUND) {
			this.context.setLineJoin(StrokeLineJoin.ROUND);
		} else {
			this.context.setLineJoin(StrokeLineJoin.BEVEL);
		}
	}

	@Override
	public void strokeWeight(float weight) {
		super.strokeWeight(weight);
		this.context.setLineWidth(weight);
	}

	//////////////////////////////////////////////////////////////

	// STROKE

	// noStroke() and stroke() inherited from PGraphics.

	@Override
	protected void strokeFromCalc() {
		super.strokeFromCalc();
		this.context.setStroke(new Color(this.strokeR, this.strokeG, this.strokeB, this.strokeA));
	}

	protected boolean drawingThinLines() {
		// align strokes to pixel centers when drawing thin lines
		return this.stroke && (this.strokeWeight == 1);
	}

	//////////////////////////////////////////////////////////////

	// TINT

	// noTint() and tint() inherited from PGraphics.

	//////////////////////////////////////////////////////////////

	// FILL

	// noFill() and fill() inherited from PGraphics.

	@Override
	protected void fillFromCalc() {
		super.fillFromCalc();
		this.context.setFill(new Color(this.fillR, this.fillG, this.fillB, this.fillA));
	}

//  //////////////////////////////////////////////////////////////
//
//  // MATERIAL PROPERTIES
//
//
//  //public void ambient(int rgb)
//  //public void ambient(float gray)
//  //public void ambient(float x, float y, float z)
//  //protected void ambientFromCalc()
//  //public void specular(int rgb)
//  //public void specular(float gray)
//  //public void specular(float x, float y, float z)
//  //protected void specularFromCalc()
//  //public void shininess(float shine)
//  //public void emissive(int rgb)
//  //public void emissive(float gray)
//  //public void emissive(float x, float y, float z )
//  //protected void emissiveFromCalc()
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // LIGHTS
//
//
//  //public void lights()
//  //public void noLights()
//  //public void ambientLight(float red, float green, float blue)
//  //public void ambientLight(float red, float green, float blue,
//  //                         float x, float y, float z)
//  //public void directionalLight(float red, float green, float blue,
//  //                             float nx, float ny, float nz)
//  //public void pointLight(float red, float green, float blue,
//  //                       float x, float y, float z)
//  //public void spotLight(float red, float green, float blue,
//  //                      float x, float y, float z,
//  //                      float nx, float ny, float nz,
//  //                      float angle, float concentration)
//  //public void lightFalloff(float constant, float linear, float quadratic)
//  //public void lightSpecular(float x, float y, float z)
//  //protected void lightPosition(int num, float x, float y, float z)
//  //protected void lightDirection(int num, float x, float y, float z)

	//////////////////////////////////////////////////////////////

	// BACKGROUND

	@Override
	public void backgroundImpl() {

		// if pixels are modified, we don't flush them (just mark them flushed)
		// because they would be immediatelly overwritten by the background anyway
		this.modified = false;
		this.loaded = false;

		// Save drawing context (transform, fill, blend mode, etc.)
		this.context.save();

		// Reset transform to identity
		this.context.setTransform(new Affine());

		// This only takes into account cases where this is the primary surface.
		// Not sure what we do with offscreen anyway.
		this.context.setFill(new Color(this.backgroundR, this.backgroundG, this.backgroundB, this.backgroundA));
		this.context.setGlobalBlendMode(BlendMode.SRC_OVER);
		this.context.fillRect(0, 0, this.width, this.height);

		// Restore drawing context (transform, fill, blend mode, etc.)
		this.context.restore();
	}

//  //////////////////////////////////////////////////////////////
//
//  // COLOR MODE
//
//  // All colorMode() variations are inherited from PGraphics.
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // COLOR CALC
//
//  // colorCalc() and colorCalcARGB() inherited from PGraphics.
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // COLOR DATATYPE STUFFING
//
//  // final color() variations inherited.
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // COLOR DATATYPE EXTRACTION
//
//  // final methods alpha, red, green, blue,
//  // hue, saturation, and brightness all inherited.
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // COLOR DATATYPE INTERPOLATION
//
//  // both lerpColor variants inherited.
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // BEGIN/END RAW
//
//
//  @Override
//  public void beginRaw(PGraphics recorderRaw) {
//    showMethodWarning("beginRaw");
//  }
//
//
//  @Override
//  public void endRaw() {
//    showMethodWarning("endRaw");
//  }
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // WARNINGS and EXCEPTIONS
//
//  // showWarning and showException inherited.
//
//
//
//  //////////////////////////////////////////////////////////////
//
//  // RENDERER SUPPORT QUERIES
//
//
//  //public boolean displayable()  // true
//
//
//  //public boolean is2D()  // true
//
//
//  //public boolean is3D()  // false

	//////////////////////////////////////////////////////////////

	// PIMAGE METHODS

	@Override
	public void loadPixels() {
		if ((this.pixels == null) || (this.pixels.length != (this.pixelWidth * this.pixelHeight))) {
			this.pixels = new int[this.pixelWidth * this.pixelHeight];
			this.loaded = false;
		}

		if (!this.loaded) {
			if ((this.snapshotImage == null) || (this.snapshotImage.getWidth() != this.pixelWidth)
					|| (this.snapshotImage.getHeight() != this.pixelHeight)) {
				this.snapshotImage = new WritableImage(this.pixelWidth, this.pixelHeight);
			}

			SnapshotParameters sp = null;
			if (this.pixelDensity != 1) {
				sp = new SnapshotParameters();
				sp.setTransform(Transform.scale(this.pixelDensity, this.pixelDensity));
			}
			this.snapshotImage = ((PSurfaceFX) this.surface).canvas.snapshot(sp, this.snapshotImage);
			PixelReader pr = this.snapshotImage.getPixelReader();
			pr.getPixels(0, 0, this.pixelWidth, this.pixelHeight, argbFormat, this.pixels, 0, this.pixelWidth);

			this.loaded = true;
			this.modified = false;
		}
	}

	//////////////////////////////////////////////////////////////

	// GET/SET PIXELS

	@Override
	public int get(int x, int y) {
		this.loadPixels();
		return super.get(x, y);
	}

	@Override
	protected void getImpl(int sourceX, int sourceY, int sourceWidth, int sourceHeight, PImage target, int targetX,
			int targetY) {
		this.loadPixels();
		super.getImpl(sourceX, sourceY, sourceWidth, sourceHeight, target, targetX, targetY);
	}

	@Override
	public void set(int x, int y, int argb) {
		this.loadPixels();
		super.set(x, y, argb);
	}

	@Override
	protected void setImpl(PImage sourceImage, int sourceX, int sourceY, int sourceWidth, int sourceHeight, int targetX,
			int targetY) {
		sourceImage.loadPixels();

		int sourceOffset = sourceX + (sourceImage.pixelWidth * sourceY);

		PixelWriter pw = this.context.getPixelWriter();
		pw.setPixels(targetX, targetY, sourceWidth, sourceHeight, argbFormat, sourceImage.pixels, sourceOffset,
				sourceImage.pixelWidth);

		// Let's keep them loaded
		if (this.loaded) {
			int sourceStride = sourceImage.pixelWidth;
			int targetStride = this.pixelWidth;
			int targetOffset = targetX + (targetY * targetStride);
			for (int i = 0; i < sourceHeight; i++) {
				System.arraycopy(sourceImage.pixels, sourceOffset + (i * sourceStride), this.pixels,
						targetOffset + (i * targetStride), sourceWidth);
			}
		}
	}

	//////////////////////////////////////////////////////////////

	// MASK

	static final String MASK_WARNING = "mask() cannot be used on the main drawing surface";

	@Override
	public void mask(PImage alpha) {
		showWarning(MASK_WARNING);
	}

	//////////////////////////////////////////////////////////////

	// FILTER

	// Because the PImage versions call loadPixels() and
	// updatePixels(), no need to override anything here.

	// public void filter(int kind)

	// public void filter(int kind, float param)

	//////////////////////////////////////////////////////////////

	// COPY

//  @Override
//  public void copy(int sx, int sy, int sw, int sh,
//                   int dx, int dy, int dw, int dh) {
//    if ((sw != dw) || (sh != dh)) {
//      g2.drawImage(image, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
//
//    } else {
//      dx = dx - sx;  // java2d's "dx" is the delta, not dest
//      dy = dy - sy;
//      g2.copyArea(sx, sy, sw, sh, dx, dy);
//    }
//  }

//  @Override
//  public void copy(PImage src,
//                   int sx, int sy, int sw, int sh,
//                   int dx, int dy, int dw, int dh) {
//    g2.drawImage((Image) src.getNative(),
//                 dx, dy, dx + dw, dy + dh,
//                 sx, sy, sx + sw, sy + sh, null);
//  }

	//////////////////////////////////////////////////////////////

	// BLEND

	// static public int blendColor(int c1, int c2, int mode)

	// public void blend(int sx, int sy, int sw, int sh,
	// int dx, int dy, int dw, int dh, int mode)

	// public void blend(PImage src,
	// int sx, int sy, int sw, int sh,
	// int dx, int dy, int dw, int dh, int mode)

	//////////////////////////////////////////////////////////////

	// SAVE

	// public void save(String filename)

	//////////////////////////////////////////////////////////////

	/**
	 * Display a warning that the specified method is simply unavailable.
	 */
	static public void showTodoWarning(String method, int issue) {
		showWarning(method + "() is not yet available: " + "https://github.com/processing/processing/issues/" + issue);
	}
}
