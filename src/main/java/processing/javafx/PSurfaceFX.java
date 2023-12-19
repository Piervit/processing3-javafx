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

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.SceneAntialiasing;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;
import processing.core.PSurface;

/**
 * This is the main part of the javafx code. It create the canvas and insert the
 * PApplet in it.
 */
public class PSurfaceFX implements PSurface {
	/* The generic PApplet */
	PApplet sketch;

	/* The component that will allow drawing. */
	PGraphicsFX2D fx;

	/*
	 * The canvas containing the drawing and used as javaFx Node exported by the
	 * library.
	 */
	Canvas canvas;

	Thread exceptionHandlerThread;
	Pane root;

	// final Animation animation;
	float frameRate = 30;
	private ReentrantLock run_lock;
	Runnable run_drawing;
	private AtomicBoolean is_running = new AtomicBoolean();
	ScheduledFuture<?> animationHandle;

	ScheduledExecutorService schedule_drawer;

	private SynchronousQueue<Throwable> drawExceptionQueue = new SynchronousQueue<>();

	public PSurfaceFX(PGraphicsFX2D graphics) {
		this.fx = graphics;
		this.canvas = new ResizableCanvas();
		this.run_lock = new ReentrantLock();
		this.is_running.set(false);

		this.run_drawing = new Runnable() {
			@Override
			public void run() {
				if (PSurfaceFX.this.is_running.get()) {
					return;
				}
				PSurfaceFX.this.is_running.set(true);
				Platform.runLater(() -> {
					try {
						PSurfaceFX.this.sketch.handleDraw();
					} catch (Throwable e) {
						// Let exception handler thread crash with our exception
						PSurfaceFX.this.drawExceptionQueue.offer(e);
						// Stop animating right now so nothing runs afterwards
						// and crash frame can be for example traced by println()
						// PSurfaceFX.this.stopThread();
						return;
					} finally {
						PSurfaceFX.this.is_running.set(false);
					}

				});

				if (PSurfaceFX.this.sketch.exitCalled()) {
					Platform.exit();
				}
			}
		};

	}

	@Override
	public Object getNative() {
		return this.root;
	}

	/**
	 * Pierre Vittet Not used anymore, do nothing.
	 * 
	 * @param str
	 */
	public void setTitle(String str) {
		return;
	}

	/**
	 * Pierre Vittet Not used anymore, do nothing.
	 * 
	 * @param str
	 */
	public void setResizable(boolean b) {
		return;
	}

	/**
	 * Pierre Vittet Not used anymore, do nothing.
	 * 
	 * @param str
	 */
	public void setAlwaysOnTop(boolean b) {
		return;
	}

	public void setVisible(boolean b) {
		this.root.setVisible(b);
		this.canvas.setVisible(b);
	}

	/**
	 * The InternalGraphWindows is used to make the canvas easily resizable.
	 */
	class InternalGraphWindows extends VBox {

		private final Canvas content;
		private final Canvas rightBorder;
		private final Canvas bottomBorder;

		Double deltaXBase;
		Double deltaYBase;

		public InternalGraphWindows(final Canvas content) {
			super();
			this.setFillWidth(true);
			this.content = content;
			this.rightBorder = new Canvas(5.0, content.getHeight());
			this.rightBorder.setStyle("-fx-cursor: h-resize;");
			this.bottomBorder = new Canvas(content.getWidth(), 5);
			this.bottomBorder.setStyle("-fx-cursor: v-resize;");

			this.rightBorder.setOnMouseReleased(event -> {
				this.deltaXBase = null;
				PSurfaceFX.this.resumeThread();
			});
			this.rightBorder.setOnMouseDragged(event -> {
				PSurfaceFX.this.pauseThread();
				final double deltaX = event.getScreenX();
				if (this.deltaXBase == null) {
					this.deltaXBase = deltaX;
				}
				this.content.setWidth((this.content.getWidth() + deltaX) - this.deltaXBase);
				this.bottomBorder.setWidth(this.content.getWidth());
				this.deltaXBase = deltaX;
			});
			this.rightBorder.setOnMouseReleased(event -> {
				this.deltaXBase = null;
				PSurfaceFX.this.resumeThread();
			});
			this.bottomBorder.setOnMouseDragged(event -> {
				PSurfaceFX.this.pauseThread();
				final double deltaY = event.getScreenY();
				if (this.deltaYBase == null) {
					this.deltaYBase = deltaY;
				}
				this.content.setHeight((this.content.getHeight() + deltaY) - this.deltaYBase);
				this.rightBorder.setHeight(this.content.getHeight());
				this.deltaYBase = deltaY;
			});
			HBox horz_content = new HBox();
			horz_content.getChildren().add(this.content);
			horz_content.getChildren().add(this.rightBorder);
			this.getChildren().add(horz_content);
			this.getChildren().add(this.bottomBorder);
			this.rightBorder.setVisible(true);
			this.bottomBorder.setVisible(true);
			// Register an event handler for a single node and a specific event type

		}

	}

	class ResizableCanvas extends Canvas {

		public ResizableCanvas() {
			this.widthProperty().addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> value, Number oldWidth, Number newWidth) {
//          sketch.width = newWidth.intValue();
					PSurfaceFX.this.sketch.setSize(newWidth.intValue(), PSurfaceFX.this.sketch.height);
//          draw();
					PSurfaceFX.this.fx.setSize(PSurfaceFX.this.sketch.width, PSurfaceFX.this.sketch.height);
				}
			});
			this.heightProperty().addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> value, Number oldHeight, Number newHeight) {
//          sketch.height = newHeight.intValue();
					PSurfaceFX.this.sketch.setSize(PSurfaceFX.this.sketch.width, newHeight.intValue());
//          draw();
					PSurfaceFX.this.fx.setSize(PSurfaceFX.this.sketch.width, PSurfaceFX.this.sketch.height);
				}
			});

			// addEventHandler(eventType, eventHandler);

			EventHandler<MouseEvent> mouseHandler = new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent e) {
					PSurfaceFX.this.fxMouseEvent(e);
				}
			};

			this.setOnMousePressed(mouseHandler);
			this.setOnMouseReleased(mouseHandler);
			this.setOnMouseClicked(mouseHandler);
			this.setOnMouseEntered(mouseHandler);
			this.setOnMouseExited(mouseHandler);

			this.setOnMouseDragged(mouseHandler);
			this.setOnMouseMoved(mouseHandler);

			this.setOnScroll(new EventHandler<ScrollEvent>() {
				@Override
				public void handle(ScrollEvent e) {
					PSurfaceFX.this.fxScrollEvent(e);
				}
			});

			EventHandler<KeyEvent> keyHandler = new EventHandler<KeyEvent>() {
				@Override
				public void handle(KeyEvent e) {
					PSurfaceFX.this.fxKeyEvent(e);
				}
			};

			this.setOnKeyPressed(keyHandler);
			this.setOnKeyReleased(keyHandler);
			this.setOnKeyTyped(keyHandler);

			this.setFocusTraversable(false); // prevent tab from de-focusing

			this.focusedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> value, Boolean oldValue, Boolean newValue) {
					if (newValue.booleanValue()) {
						PSurfaceFX.this.sketch.focused = true;
						PSurfaceFX.this.sketch.focusGained();
					} else {
						PSurfaceFX.this.sketch.focused = false;
						PSurfaceFX.this.sketch.focusLost();
					}
				}
			});
		}

		@Override
		public boolean isResizable() {
			return true;
		}

		@Override
		public double prefWidth(double height) {
			return this.getWidth();
		}

		@Override
		public double prefHeight(double width) {
			return this.getHeight();
		}

	}

	@Override
	public void initOffscreen(PApplet sketch) {
	}

	/**
	 * In the standard processing 3 project, PApplicationFX was the class extending
	 * ApplicationFX which was launching the full javafx application.
	 * 
	 * This is very reduced now that we just export the canvas and the class should
	 * probably removed and the code exported as standard PSurfaceFX code. But for
	 * now it is not done, and maybe it can still be usefull if we need to
	 * multithread some stuff.
	 */
	public class PApplicationFX {
		public PSurfaceFX surface;

		public PApplicationFX(PSurfaceFX sr) {
			this.surface = sr;
			this.start();
		}

		public void start() {

			PApplet sketch = this.surface.sketch;

			if ((sketch.pixelDensity == 2)) {// && (renderScale < 2)) {
				sketch.pixelDensity = 1;
				sketch.g.pixelDensity = 1;
				System.err.println("pixelDensity(2) is not available for this display");
			}

			// Use AWT display code, because FX orders screens in different way
			GraphicsDevice displayDevice = null;

			GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();

			int displayNum = sketch.sketchDisplay();
			if (displayNum > 0) { // if -1, use the default device
				GraphicsDevice[] devices = environment.getScreenDevices();
				if (displayNum <= devices.length) {
					displayDevice = devices[displayNum - 1];
				} else {
					System.err.format("Display %d does not exist, " + "using the default display instead.%n",
							displayNum);
					for (int i = 0; i < devices.length; i++) {
						System.err.format("Display %d is %s%n", (i + 1), devices[i]);
					}
				}
			}
			if (displayDevice == null) {
				displayDevice = environment.getDefaultScreenDevice();
			}

			boolean fullScreen = sketch.sketchFullScreen();
			boolean spanDisplays = sketch.sketchDisplay() == PConstants.SPAN;

			java.awt.Rectangle primaryScreenRect = displayDevice.getDefaultConfiguration().getBounds();
			java.awt.Rectangle screenRect = primaryScreenRect;
			if (fullScreen || spanDisplays) {
				double minX = screenRect.getMinX();
				double maxX = screenRect.getMaxX();
				double minY = screenRect.getMinY();
				double maxY = screenRect.getMaxY();
				if (spanDisplays) {
					for (GraphicsDevice s : environment.getScreenDevices()) {
						java.awt.Rectangle bounds = s.getDefaultConfiguration().getBounds();
						minX = Math.min(minX, bounds.getMinX());
						maxX = Math.max(maxX, bounds.getMaxX());
						minY = Math.min(minY, bounds.getMinY());
						maxY = Math.max(maxY, bounds.getMaxY());
					}
				}
				screenRect = new java.awt.Rectangle((int) minX, (int) minY, (int) (maxX - minX), (int) (maxY - minY));
			}

			sketch.displayWidth = (int) screenRect.getWidth();
			sketch.displayHeight = (int) screenRect.getHeight();

			int sketchWidth = sketch.sketchWidth();
			int sketchHeight = sketch.sketchHeight();

			if (fullScreen || spanDisplays) {
				sketchWidth = (int) (screenRect.getWidth());// / uiScale);
				sketchHeight = (int) (screenRect.getHeight()); // / uiScale);

			}

			int smooth = sketch.sketchSmooth();

			// Workaround for https://bugs.openjdk.java.net/browse/JDK-8136495
			// https://github.com/processing/processing/issues/3823
			if (((PApplet.platform == PConstants.MACOSX) || (PApplet.platform == PConstants.LINUX))
					&& (PApplet.javaVersionName.compareTo("1.8.0_60") >= 0)
					&& (PApplet.javaVersionName.compareTo("1.8.0_72") < 0)) {
				System.err.println("smooth() disabled for JavaFX with this Java version due to Oracle bug");
				System.err.println("https://github.com/processing/processing/issues/3795");
				smooth = 0;
			}

			SceneAntialiasing sceneAntialiasing = (smooth == 0) ? SceneAntialiasing.DISABLED
					: SceneAntialiasing.BALANCED;
			PSurfaceFX.this.canvas.setWidth(sketchWidth);
			PSurfaceFX.this.canvas.setHeight(sketchHeight);
			PSurfaceFX.this.canvas.setVisible(true);
			// PSurfaceFX.this.stage.setScene(new Scene(stackPane, width, height, false,
			// sceneAntialiasing));

			// initFrame in different thread is waiting for
			// the stage, assign it only when it is all set up
			// surface.stage = PSurfaceFX.this.stage;

			PSurfaceFX.this.schedule_drawer = Executors.newScheduledThreadPool(1);
			PSurfaceFX.this.animationHandle = PSurfaceFX.this.schedule_drawer
					.scheduleAtFixedRate(PSurfaceFX.this.run_drawing, 34, 34, TimeUnit.MILLISECONDS);
		}

		public void pause() {
			PSurfaceFX.this.schedule_drawer.shutdown();
		}

		public void resume() {
			PSurfaceFX.this.schedule_drawer = Executors.newScheduledThreadPool(1);
			PSurfaceFX.this.animationHandle = PSurfaceFX.this.schedule_drawer
					.scheduleAtFixedRate(PSurfaceFX.this.run_drawing, 34, 34, TimeUnit.MILLISECONDS);
		}

		public void stop() throws Exception {
			this.surface.sketch.dispose();
		}

	}

	// public Frame initFrame(PApplet sketch, java.awt.Color backgroundColor,
	@Override
	public void initFrame(PApplet sketch) {/*
											 * , int backgroundColor, int deviceIndex, boolean fullScreen, boolean
											 * spanDisplays) {
											 */
		this.sketch = sketch;
		new PApplicationFX(this);
		this.root = new InternalGraphWindows(this.canvas);
		// wait for stage to be initialized on its own thread before continuing
//		while (this.stage == null) {
//			try {
//				// System.out.println("waiting for launch");
//				Thread.sleep(5);
//			} catch (InterruptedException e) {
//			}
		// }

		this.startExceptionHandlerThread();

	}

	private void startExceptionHandlerThread() {
		this.exceptionHandlerThread = new Thread(() -> {
			Throwable drawException;
			try {
				drawException = this.drawExceptionQueue.take();
			} catch (InterruptedException e) {
				return;
			}
			// Adapted from PSurfaceJOGL
			if (drawException != null) {
				if (drawException instanceof ThreadDeath) {
//            System.out.println("caught ThreadDeath");
//            throw (ThreadDeath)cause;
				} else if (drawException instanceof RuntimeException) {
					throw (RuntimeException) drawException;
				} else if (drawException instanceof UnsatisfiedLinkError) {
					throw new UnsatisfiedLinkError(drawException.getMessage());
				} else {
					throw new RuntimeException(drawException);
				}
			}
		});
		this.exceptionHandlerThread.setDaemon(true);
		this.exceptionHandlerThread.setName("Processing-FX-ExceptionHandler");
		this.exceptionHandlerThread.start();
	}

	private void stopExceptionHandlerThread() {
		this.exceptionHandlerThread.interrupt();

	}

	// http://download.java.net/jdk8/jfxdocs/javafx/stage/Stage.html#setFullScreenExitHint-java.lang.String-
	// http://download.java.net/jdk8/jfxdocs/javafx/stage/Stage.html#setFullScreenExitKeyCombination-javafx.scene.input.KeyCombination-
	@Override
	public void placePresent(int stopColor) {
		// TODO Auto-generated method stub
		PApplet.hideMenuBar();
	}

	@Override
	public void setSize(int wide, int high) {
		// When the surface is set to resizable via surface.setResizable(true),
		// a crash may occur if the user sets the window to size zero.
		// https://github.com/processing/processing/issues/5052
		if (high <= 0) {
			high = 1;
		}
		if (wide <= 0) {
			wide = 1;
		}

		// System.out.format("%s.setSize(%d, %d)%n", getClass().getSimpleName(), width,
		// height);
		// Scene scene = this.stage.getScene();
		// double decorH = this.stage.getWidth() - scene.getWidth();
		// double decorV = this.stage.getHeight() - scene.getHeight();
		// this.stage.setPrefWidth(wide + decorH);
		// this.stage.setPrefHeight(high + decorV);
		this.fx.setSize(wide, high);
	}

//  public Component getComponent() {
//    return null;
//  }

	public void setSmooth(int level) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setFrameRate(float fps) {
		// setting rate to negative so that event fires at the start of
		// the key frame and first frame is drawn immediately
		if (fps > 0) {
			this.frameRate = fps;
		}
	}

	Cursor lastCursor = Cursor.DEFAULT;

	@Override
	public void setCursor(int kind) {
		Cursor c;
		switch (kind) {
		case PConstants.ARROW:
			c = Cursor.DEFAULT;
			break;
		case PConstants.CROSS:
			c = Cursor.CROSSHAIR;
			break;
		case PConstants.HAND:
			c = Cursor.HAND;
			break;
		case PConstants.MOVE:
			c = Cursor.MOVE;
			break;
		case PConstants.TEXT:
			c = Cursor.TEXT;
			break;
		case PConstants.WAIT:
			c = Cursor.WAIT;
			break;
		default:
			c = Cursor.DEFAULT;
			break;
		}
		this.lastCursor = c;
		this.canvas.getScene().setCursor(c);
	}

	@Override
	public void setCursor(PImage image, int hotspotX, int hotspotY) {
		int w = image.pixelWidth;
		int h = image.pixelHeight;
		WritableImage im = new WritableImage(w, h);
		im.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), image.pixels, 0, w);
		ImageCursor c = new ImageCursor(im, hotspotX, hotspotY);
		this.lastCursor = c;
		this.canvas.getScene().setCursor(c);
	}

	@Override
	public void showCursor() {
		this.canvas.getScene().setCursor(this.lastCursor);
	}

	@Override
	public void hideCursor() {
		this.canvas.getScene().setCursor(Cursor.NONE);
	}

	@Override
	public void startThread() {
		// this.animation.play();
	}

	@Override
	public void pauseThread() {
		this.sketch.pause();
		// this.animation.pause();
	}

	@Override
	public void resumeThread() {
		this.sketch.resume();
	}

	@Override
	public boolean stopThread() {
		this.animationHandle.cancel(true);
		this.stopExceptionHandlerThread();
		this.sketch.exit();
		return true;
	}

	@Override
	public boolean isStopped() {
		return false;
	}

	static Map<EventType<? extends MouseEvent>, Integer> mouseMap = new HashMap<EventType<? extends MouseEvent>, Integer>();
	static {
		mouseMap.put(MouseEvent.MOUSE_PRESSED, processing.event.MouseEvent.PRESS);
		mouseMap.put(MouseEvent.MOUSE_RELEASED, processing.event.MouseEvent.RELEASE);
		mouseMap.put(MouseEvent.MOUSE_CLICKED, processing.event.MouseEvent.CLICK);
		mouseMap.put(MouseEvent.MOUSE_DRAGGED, processing.event.MouseEvent.DRAG);
		mouseMap.put(MouseEvent.MOUSE_MOVED, processing.event.MouseEvent.MOVE);
		mouseMap.put(MouseEvent.MOUSE_ENTERED, processing.event.MouseEvent.ENTER);
		mouseMap.put(MouseEvent.MOUSE_EXITED, processing.event.MouseEvent.EXIT);
	}

	protected void fxMouseEvent(MouseEvent fxEvent) {
		// the 'amount' is the number of button clicks for a click event,
		// or the number of steps/clicks on the wheel for a mouse wheel event.
		int count = fxEvent.getClickCount();

		int action = mouseMap.get(fxEvent.getEventType());

		int modifiers = 0;
		if (fxEvent.isShiftDown()) {
			modifiers |= processing.event.Event.SHIFT;
		}
		if (fxEvent.isControlDown()) {
			modifiers |= processing.event.Event.CTRL;
		}
		if (fxEvent.isMetaDown()) {
			modifiers |= processing.event.Event.META;
		}
		if (fxEvent.isAltDown()) {
			modifiers |= processing.event.Event.ALT;
		}

		int button = 0;
		switch (fxEvent.getButton()) {
		case PRIMARY:
			button = PConstants.LEFT;
			break;
		case SECONDARY:
			button = PConstants.RIGHT;
			break;
		case MIDDLE:
			button = PConstants.CENTER;
			break;
		case NONE:
			// not currently handled
			break;
		}

		// long when = nativeEvent.getWhen(); // from AWT
		long when = System.currentTimeMillis();
		int x = (int) fxEvent.getX(); // getSceneX()?
		int y = (int) fxEvent.getY();

		this.sketch.postEvent(new processing.event.MouseEvent(fxEvent, when, action, modifiers, x, y, button, count));
	}

	// https://docs.oracle.com/javase/8/javafx/api/javafx/scene/input/ScrollEvent.html
	protected void fxScrollEvent(ScrollEvent fxEvent) {
		// the number of steps/clicks on the wheel for a mouse wheel event.
		int count = (int) -(fxEvent.getDeltaY() / fxEvent.getMultiplierY());

		int action = processing.event.MouseEvent.WHEEL;

		int modifiers = 0;
		if (fxEvent.isShiftDown()) {
			modifiers |= processing.event.Event.SHIFT;
		}
		if (fxEvent.isControlDown()) {
			modifiers |= processing.event.Event.CTRL;
		}
		if (fxEvent.isMetaDown()) {
			modifiers |= processing.event.Event.META;
		}
		if (fxEvent.isAltDown()) {
			modifiers |= processing.event.Event.ALT;
		}

		// FX does not supply button info
		int button = 0;

		long when = System.currentTimeMillis();
		int x = (int) fxEvent.getX(); // getSceneX()?
		int y = (int) fxEvent.getY();

		this.sketch.postEvent(new processing.event.MouseEvent(fxEvent, when, action, modifiers, x, y, button, count));
	}

	protected void fxKeyEvent(javafx.scene.input.KeyEvent fxEvent) {
		int action = 0;
		EventType<? extends KeyEvent> et = fxEvent.getEventType();
		if (et == KeyEvent.KEY_PRESSED) {
			action = processing.event.KeyEvent.PRESS;
		} else if (et == KeyEvent.KEY_RELEASED) {
			action = processing.event.KeyEvent.RELEASE;
		} else if (et == KeyEvent.KEY_TYPED) {
			action = processing.event.KeyEvent.TYPE;
		}

		int modifiers = 0;
		if (fxEvent.isShiftDown()) {
			modifiers |= processing.event.Event.SHIFT;
		}
		if (fxEvent.isControlDown()) {
			modifiers |= processing.event.Event.CTRL;
		}
		if (fxEvent.isMetaDown()) {
			modifiers |= processing.event.Event.META;
		}
		if (fxEvent.isAltDown()) {
			modifiers |= processing.event.Event.ALT;
		}

		long when = System.currentTimeMillis();

		char keyChar = this.getKeyChar(fxEvent);
		int keyCode = this.getKeyCode(fxEvent);
		this.sketch.postEvent(new processing.event.KeyEvent(fxEvent, when, action, modifiers, keyChar, keyCode));
	}

	@SuppressWarnings("deprecation")
	private int getKeyCode(KeyEvent fxEvent) {
		if (fxEvent.getEventType() == KeyEvent.KEY_TYPED) {
			return 0;
		}

		KeyCode kc = fxEvent.getCode();
		switch (kc) {
		case ALT_GRAPH:
			return PConstants.ALT;
		default:
			break;
		}
		return kc.getCode();
	}

	@SuppressWarnings("deprecation")
	private char getKeyChar(KeyEvent fxEvent) {
		KeyCode kc = fxEvent.getCode();

		// Overriding chars for some
		// KEY_PRESSED and KEY_RELEASED events
		switch (kc) {
		case UP:
		case KP_UP:
		case DOWN:
		case KP_DOWN:
		case LEFT:
		case KP_LEFT:
		case RIGHT:
		case KP_RIGHT:
		case ALT:
		case ALT_GRAPH:
		case CONTROL:
		case SHIFT:
		case CAPS:
		case META:
		case WINDOWS:
		case CONTEXT_MENU:
		case HOME:
		case PAGE_UP:
		case PAGE_DOWN:
		case END:
		case PAUSE:
		case PRINTSCREEN:
		case INSERT:
		case NUM_LOCK:
		case SCROLL_LOCK:
		case F1:
		case F2:
		case F3:
		case F4:
		case F5:
		case F6:
		case F7:
		case F8:
		case F9:
		case F10:
		case F11:
		case F12:
			return PConstants.CODED;
		case ENTER:
			return '\n';
		case DIVIDE:
			return '/';
		case MULTIPLY:
			return '*';
		case SUBTRACT:
			return '-';
		case ADD:
			return '+';
		case NUMPAD0:
			return '0';
		case NUMPAD1:
			return '1';
		case NUMPAD2:
			return '2';
		case NUMPAD3:
			return '3';
		case NUMPAD4:
			return '4';
		case NUMPAD5:
			return '5';
		case NUMPAD6:
			return '6';
		case NUMPAD7:
			return '7';
		case NUMPAD8:
			return '8';
		case NUMPAD9:
			return '9';
		case DECIMAL:
			// KEY_TYPED does not go through here and will produce
			// dot or comma based on the keyboard layout.
			// For KEY_PRESSED and KEY_RELEASED, let's just go with
			// the dot. Users can detect the key by its keyCode.
			return '.';
		case UNDEFINED:
			// KEY_TYPED has KeyCode: UNDEFINED
			// and falls through here
			break;
		default:
			break;
		}

		// Just go with what FX gives us for the rest of
		// KEY_PRESSED and KEY_RELEASED and all of KEY_TYPED
		String ch;
		if (fxEvent.getEventType() == KeyEvent.KEY_TYPED) {
			ch = fxEvent.getCharacter();
		} else {
			ch = kc.getChar();
		}

		if (ch.length() < 1) {
			return PConstants.CODED;
		}
		if (ch.startsWith("\r")) {
			return '\n'; // normalize enter key
		}
		return ch.charAt(0);
	}
}
