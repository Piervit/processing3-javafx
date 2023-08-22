/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2014-15 The Processing Foundation

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

package processing.core;

public interface PSurface {
	/**
	 * Minimum dimensions for the window holding an applet. This varies between
	 * platforms, Mac OS X 10.3 (confirmed with 10.7 and Java 6) can do any height
	 * but requires at least 128 pixels width. Windows XP has another set of
	 * limitations. And for all I know, Linux probably allows window sizes to be
	 * negative numbers.
	 */
	static public final int MIN_WINDOW_WIDTH = 128;
	static public final int MIN_WINDOW_HEIGHT = 128;

	// renderer that doesn't draw to the screen
	public void initOffscreen(PApplet sketch);

	// considering removal in favor of separate Component classes for appropriate
	// renderers
	// (i.e. for Java2D or a generic Image surface, but not PDF, debatable for GL or
	// FX)
	// public Component initComponent(PApplet sketch);

	// public Frame initFrame(PApplet sketch, Color backgroundColor,
//  public void initFrame(PApplet sketch, int backgroundColor,
//                        int deviceIndex, boolean fullScreen, boolean spanDisplays);
	public void initFrame(PApplet sketch);

	/**
	 * Get the native window object associated with this drawing surface. For
	 * Java2D, this will be an AWT Frame object. For OpenGL, the window. The data
	 * returned here is subject to the whims of the renderer, and using this method
	 * means you're willing to deal with underlying implementation changes and that
	 * you won't throw a fit like a toddler if your code breaks sometime in the
	 * future.
	 */
	public Object getNative();

	//

	// Just call these on an AWT Frame object stored in PApplet.
	// Silly, but prevents a lot of rewrite and extra methods for little benefit.
	// However, maybe prevents us from having to document the 'frame' variable?

	/** Set the window (and dock, or whatever necessary) title. */

	//
	// public void placeFullScreen(boolean hideStop);
	public void placePresent(int stopColor);

	// sets displayWidth/Height inside PApplet
	// public void checkDisplaySize();

//  /**
//   * Called by {@link PApplet#createGraphics} to initialize the
//   * {@link PGraphics#image} object with an image that's compatible with this
//   * drawing surface/display/hardware.
//   * @param gr PGraphics object whose image will be set
//   * @param wide
//   * @param high
//   */
	// create pixel buffer (pulled out for offscreen graphics)
	// public void initImage(PGraphics gr, int wide, int high);
	// create pixel buffer, called from allocate() to produce a compatible image for
	// rendering efficiently
//  public void initImage(PGraphics gr);

	// public Component getComponent();

//  /**
//   * Sometimes smoothing must be set at the drawing surface level
//   * not just inside the renderer itself.
//   */
//  public void setSmooth(int level);

	public void setFrameRate(float fps);

//  // called on the first frame so that the now-visible drawing surface can
//  // receive key and mouse events
//  public void requestFocus();

//  // finish rendering to the screen (called by PApplet)
//  public void blit();

	//

	public void setCursor(int kind);

	public void setCursor(PImage image, int hotspotX, int hotspotY);

	public void showCursor();

	public void hideCursor();

	//

	/** Start the animation thread */
	public void startThread();

	/**
	 * On the next trip through the animation thread, things should go sleepy-bye.
	 * Does not pause the thread immediately because that needs to happen on the
	 * animation thread itself, so fires on the next trip through draw().
	 */
	public void pauseThread();

	public void resumeThread();

	/**
	 * Stop the animation thread (set it null)
	 * 
	 * @return false if already stopped
	 */
	public boolean stopThread();

	public boolean isStopped();

	/**
	 * public void setupExternalMessages() { this.stage.xProperty().addListener(new
	 * ChangeListener<Number>() {
	 * 
	 * @Override public void changed(ObservableValue<? extends Number> value, Number
	 *           oldX, Number newX) {
	 *           PSurfaceFX.this.sketch.frameMoved(newX.intValue(),
	 *           PSurfaceFX.this.stage.yProperty().intValue()); } });
	 * 
	 *           this.stage.yProperty().addListener(new ChangeListener<Number>() {
	 * @Override public void changed(ObservableValue<? extends Number> value, Number
	 *           oldY, Number newY) {
	 *           PSurfaceFX.this.sketch.frameMoved(PSurfaceFX.this.stage.xProperty().intValue(),
	 *           newY.intValue()); } });
	 * 
	 *           this.stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
	 * @Override public void handle(WindowEvent we) { PSurfaceFX.this.sketch.exit();
	 *           } }); }
	 **/
	void setSize(int wide, int high);
}
