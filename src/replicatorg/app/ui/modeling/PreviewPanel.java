/**
 * 
 */
package replicatorg.app.ui.modeling;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Enumeration;
import java.util.logging.Level;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingBox;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.Geometry;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.LineArray;
import javax.media.j3d.LineAttributes;
import javax.media.j3d.Material;
import javax.media.j3d.Node;
import javax.media.j3d.PolygonAttributes;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

import net.miginfocom.swing.MigLayout;
import replicatorg.app.Base;
import replicatorg.app.ui.MainWindow;
import replicatorg.model.BuildModel;

import com.sun.j3d.utils.universe.SimpleUniverse;

/**
 * @author phooky
 *
 */
public class PreviewPanel extends JPanel {

	BoundingSphere bounds =
		new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0);

	BuildModel model = null;
	
	public void setModel(BuildModel model) {
		if (model != this.model) {
			this.model = model;
			if (model != null) {
				setScene(model);
			}
		}
	}

	private void setScene(BuildModel model) {
		Base.logger.info(model.getPath());
		if (objectBranch != null) {
			sceneGroup.removeChild(objectBranch);
		}
		objectBranch = makeShape(model);
		sceneGroup.addChild(objectBranch);
	}
	
	MainWindow mainWindow;

	public JButton createToolButton(String text, String iconPath) {
		ImageIcon icon = new ImageIcon(Base.getImage(iconPath, this));
		JButton button = new JButton(text,icon);
		button.setVerticalTextPosition(SwingConstants.BOTTOM);
		button.setHorizontalTextPosition(SwingConstants.CENTER);
		return button;
	}

	public JPanel createToolPanel() {
		JPanel panel = new JPanel(new MigLayout());

		JButton resetViewButton = createToolButton("Reset view","images/look-at-object.png");
		resetViewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				resetView();
			}
		});
		panel.add(resetViewButton,"growx,spanx,wrap");

		JButton sliceButton = createToolButton("Generate GCode","images/model-to-gcode.png");
		sliceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				mainWindow.runToolpathGenerator();
			}
		});
		panel.add(sliceButton,"growx,spanx,wrap");

		JButton alignButton = createToolButton("Align","images/align-with-floor.png");
		alignButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				align();
			}
		});
		panel.add(alignButton,"growx,growy");

		JButton flipButton = createToolButton("Flip","images/flip-object.png");
		flipButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				flipZ();
			}
		});
		panel.add(flipButton,"growx,growy,wrap");

		String instrStr = Base.isMacOS()?
				"<html><body>Drag to rotate<br>Shift-drag to pan<br>Mouse wheel to zoom</body></html>":
				"<html><body>Left button drag to rotate<br>Right button drag to pan<br>Mouse wheel to zoom</body></html>";
		JLabel instructions = new JLabel(instrStr);
		Font f = instructions.getFont();
		instructions.setFont(f.deriveFont((float)f.getSize()*0.8f));
		panel.add(instructions,"growx,gaptop 20,spanx,wrap");
		return panel;
	}

	enum DragMode {
		NONE,
		ROTATE_VIEW,
		TRANSLATE_VIEW,
		ROTATE_OBJECT,
		TRANSLATE_OBJECT
	};

	public PreviewPanel(final MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		//setLayout(new MigLayout()); 
		setLayout(new MigLayout("fill,ins 0,gap 0"));
		// Create Canvas3D and SimpleUniverse; add canvas to drawing panel
		Canvas3D c = createUniverse();
		add(c, "growx,growy");
		add(createToolPanel(),"dock east");
		// Create the content branch and add it to the universe
		BranchGroup scene = createSTLScene();
		univ.addBranchGraph(scene);

		class MouseActivityListener implements MouseMotionListener, MouseListener, MouseWheelListener {
			Point startPoint = null;
			int button = 0;
						
			public void mouseDragged(MouseEvent e) {
				if (startPoint == null) return;
				Point p = e.getPoint();
				DragMode mode = DragMode.ROTATE_VIEW; 
				if (Base.isMacOS()) {
					if (button == MouseEvent.BUTTON1 && !e.isShiftDown()) { mode = DragMode.ROTATE_VIEW; }
					else if (button == MouseEvent.BUTTON1 && e.isShiftDown()) { mode = DragMode.TRANSLATE_VIEW; }
				} else {
					if (e.isAltDown()) {
						if (button == MouseEvent.BUTTON1) { mode = DragMode.ROTATE_OBJECT; }
						else if (button == MouseEvent.BUTTON3) { mode = DragMode.TRANSLATE_OBJECT; }
					} else {
						if (button == MouseEvent.BUTTON1) { mode = DragMode.ROTATE_VIEW; }
						else if (button == MouseEvent.BUTTON3) { mode = DragMode.TRANSLATE_VIEW; }
					}
				}
				double xd = (double)(p.x - startPoint.x);
				double yd = (double)(p.y - startPoint.y);
				switch (mode) {
				case ROTATE_VIEW:
					// Rotate view
					turntableAngle += 0.05 * xd;
					elevationAngle -= 0.05 * yd;
					updateVP();
					break;
				case TRANSLATE_VIEW:
					// Pan view
					cameraTranslation.x += -0.05 * xd;
					cameraTranslation.y += 0.05 * yd;
					updateVP();
					break;
				case TRANSLATE_OBJECT:
					translateObject(0.05*xd,0d,-0.05*yd);
					break;
				}
				startPoint = p;
			}
			public void mouseMoved(MouseEvent e) {
			}
			public void mouseClicked(MouseEvent e) {
			}
			public void mouseEntered(MouseEvent e) {
			}
			public void mouseExited(MouseEvent e) {
			}
			public void mousePressed(MouseEvent e) {
				startPoint = e.getPoint();
				button = e.getButton();
			}
			public void mouseReleased(MouseEvent e) {
				startPoint = null;
			}
			public void mouseWheelMoved(MouseWheelEvent e) {
				int notches = e.getWheelRotation();				
				cameraTranslation.z += 0.10 * notches;
				updateVP();
			}
			
		};

		MouseActivityListener activityListener = new MouseActivityListener();
		c.addMouseMotionListener(activityListener);
		c.addMouseWheelListener(activityListener);
		c.addMouseListener(activityListener);
		
		c.addKeyListener( new KeyListener() {
			public void keyPressed(KeyEvent e) {
				if (e.getKeyChar() == 'a') {
					cameraTranslation.x += 0.05;
				} else if (e.getKeyChar() == 'z') {
					cameraTranslation.x -= 0.05;
				} else if (e.getKeyChar() == 's') {
					cameraTranslation.y += 0.05;
				} else if (e.getKeyChar() == 'x') {
					cameraTranslation.y -= 0.05;
				} else if (e.getKeyChar() == 'd') {
					cameraTranslation.z += 0.05;
				} else if (e.getKeyChar() == 'c') {
					cameraTranslation.z -= 0.05;
				} else if (e.getKeyChar() == '[') {
					elevationAngle += 0.05;
				} else if (e.getKeyChar() == ']') {
					elevationAngle -= 0.05;
				} else if (e.getKeyChar() == '{') {
					turntableAngle += 0.05;
				} else if (e.getKeyChar() == '}') {
					turntableAngle -= 0.05;
				} else if (e.getKeyChar() == 'e') {
					if (showEdges) {
						objectSwitch.setWhichChild(0);
						showEdges = false;
					} else {
						objectSwitch.setWhichChild(1);
						showEdges = true;
					}
				} else {
					return;
				}
				updateVP();
			}

			public void keyReleased(KeyEvent e) {
			}

			public void keyTyped(KeyEvent e) {
			}
		});
	}		


	private SimpleUniverse univ = null;

	/**
	 * The switch object that allows us to toggle between wireframe and solid modes.
	 */
	private Switch objectSwitch = null;
	/**
	 * Indicates whether we're in edge (wireframe) mode.  False indicates a solid view. 
	 */
	private boolean showEdges = false;
	/**
	 * The transform group for the shape.  The enclosed transform should be applied to the shape before:
	 * * bounding box calculation
	 * * saving out the STL for skeining
	 */
	private TransformGroup shapeTransform = new TransformGroup();

	public Node makeAmbientLight() {
		AmbientLight ambient = new AmbientLight();
		ambient.setColor(new Color3f(0.3f,0.3f,0.9f));
		ambient.setInfluencingBounds(bounds);
		return ambient;
	}

	public Node makeDirectedLight1() {
		Color3f color = new Color3f(0.7f,0.7f,0.7f);
		Vector3f direction = new Vector3f(1f,0.7f,-0.2f);
		DirectionalLight light = new DirectionalLight(color,direction);
		light.setInfluencingBounds(bounds);
		return light;
	}

	public Node makeDirectedLight2() {
		Color3f color = new Color3f(0.5f,0.5f,0.5f);
		Vector3f direction = new Vector3f(-1f,-0.7f,0.2f);
		DirectionalLight light = new DirectionalLight(color,direction);
		light.setInfluencingBounds(bounds);
		return light;
	}

	final double wireBoxCoordinates[] = {
			0,  0,  0,    0,  0,  1,
			0,  1,  0,    0,  1,  1,
			1,  1,  0,    1,  1,  1,
			1,  0,  0,    1,  0,  1,

			0,  0,  0,    0,  1,  0,
			0,  0,  1,    0,  1,  1,
			1,  0,  1,    1,  1,  1,
			1,  0,  0,    1,  1,  0,

			0,  0,  0,    1,  0,  0,
			0,  0,  1,    1,  0,  1,
			0,  1,  1,    1,  1,  1,
			0,  1,  0,    1,  1,  0,
	};

	public Shape3D makeBoxFrame(Point3d ll, Vector3d dim) {
		Appearance edges = new Appearance();
		edges.setLineAttributes(new LineAttributes(1,LineAttributes.PATTERN_DOT,true));
		edges.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_LINE,
				PolygonAttributes.CULL_NONE,0));
		double[] coords = new double[wireBoxCoordinates.length];
		for (int i = 0; i < wireBoxCoordinates.length;) {
			coords[i] = (wireBoxCoordinates[i] * dim.x) + ll.x; i++;
			coords[i] = (wireBoxCoordinates[i] * dim.y) + ll.y; i++;
			coords[i] = (wireBoxCoordinates[i] * dim.z) + ll.z; i++;
		}
		LineArray wires = new LineArray(wireBoxCoordinates.length/3,GeometryArray.COORDINATES);
		wires.setCoordinates(0, coords);

		return new Shape3D(wires,edges); 
	}

	public Node makeBoundingBox() {

		Shape3D boxframe = makeBoxFrame(new Point3d(-50,-50,0), new Vector3d(100,100,100));	

		/*
		Appearance sides = new Appearance();
		sides.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NICEST,0.9f));
		Color3f color = new Color3f(0.05f,0.05f,1.0f); 
		Material m = new Material(color,color,color,color,64.0f);
		sides.setMaterial(m);

		Box box = new Box(50,50,50,sides);
		Transform3D tf = new Transform3D();
		tf.setTranslation(new Vector3d(0,0,50));
		TransformGroup tg = new TransformGroup(tf);
		tg.addChild(box);
		tg.addChild(boxframe);
		*/
		return boxframe;
	}

	public Node makeBackground() {
		Background bg = new Background(0.5f,0.5f,0.6f);
		bg.setApplicationBounds(bounds);
		return bg;
	}

	public Node makeBaseGrid() {
		Appearance edges = new Appearance();
		edges.setLineAttributes(new LineAttributes(1,LineAttributes.PATTERN_DOT,true));
		edges.setColoringAttributes(new ColoringAttributes(0.7f,0.7f,1f,ColoringAttributes.FASTEST));
		final int LINES = 11;
		LineArray grid = new LineArray(4*LINES,GeometryArray.COORDINATES);
		for (int i = 0; i < LINES; i++) {
			double offset = -50 + (100/(LINES-1))*i;
			int idx = i*4;
			// Along x axis
			grid.setCoordinate(idx++, new Point3d(offset,-50,0));
			grid.setCoordinate(idx++, new Point3d(offset,50,0));
			// Along y axis
			grid.setCoordinate(idx++, new Point3d(-50,offset,0));
			grid.setCoordinate(idx++, new Point3d(50,offset,0));
		}
		return new Shape3D(grid,edges); 
	}

	private BoundingBox getBoundingBox(Group group, Transform3D transformation) {
		BoundingBox bb = new BoundingBox(new Point3d(Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE),
				new Point3d(Double.MIN_VALUE,Double.MIN_VALUE,Double.MIN_VALUE));
		transformation = new Transform3D(transformation);
		if (group instanceof TransformGroup) {
			Transform3D nextTransform = new Transform3D();
			((TransformGroup)group).getTransform(nextTransform);
			transformation.mul(nextTransform);
		}
		for (int i = 0; i < group.numChildren(); i++) {
			Node n = group.getChild(i);
			if (n instanceof Shape3D) {
				bb.combine(getBoundingBox((Shape3D)n, transformation));
			} else if (n instanceof Group) {
				bb.combine(getBoundingBox((Group)n,transformation));
			}
		}
		return bb;
	}
	
	private BoundingBox getBoundingBox(Group group) {
		return getBoundingBox(group, new Transform3D());
	}
	
	private BoundingBox getBoundingBox(Shape3D shape, Transform3D transformation) {
		BoundingBox bb = null;
		Enumeration<?> geometries = shape.getAllGeometries();
		while (geometries.hasMoreElements()) {
			Geometry g = (Geometry)geometries.nextElement();
			if (g instanceof GeometryArray) {
				GeometryArray ga = (GeometryArray)g;
				Point3d p = new Point3d();
				for (int i = 0; i < ga.getVertexCount(); i++) {
					ga.getCoordinate(i,p);
					transformation.transform(p);
					if (bb == null) { bb = new BoundingBox(p,p); }
					bb.combine(p);
				}
			}
		}
		return bb;
	}

	private BranchGroup makeShape(BuildModel model) {
		objectSwitch = new Switch();
		Shape3D originalShape = model.getShape();

		Shape3D shape = (Shape3D)originalShape.cloneTree();
		Shape3D edgeClone = (Shape3D)originalShape.cloneTree();
		objectSwitch.addChild(shape);
		objectSwitch.addChild(edgeClone);
		objectSwitch.setWhichChild(0);
		objectSwitch.setCapability(Switch.ALLOW_SWITCH_WRITE);

		//Color3f color = new Color3f(0.05f,1.0f,0.04f); 
		Color3f color = new Color3f(1.0f,1.0f,1.0f); 
		Material m = new Material();
		m.setAmbientColor(color);
		m.setDiffuseColor(color);
		//m.setSpecularColor(new Color3f(1f,1f,1f));
		Appearance solid = new Appearance();
		solid.setMaterial(m);
		//solid.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NICEST, 0.2f));
		shape.setAppearance(solid);

		Appearance edges = new Appearance();
		edges.setLineAttributes(new LineAttributes(1,LineAttributes.PATTERN_SOLID,true));
		edges.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_LINE,
				PolygonAttributes.CULL_NONE,0));
		edgeClone.setAppearance(edges);

		BranchGroup wrapper = new BranchGroup();

		shapeTransform = new TransformGroup();
		shapeTransform.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		wrapper.addChild(shapeTransform);

		shapeTransform.addChild(objectSwitch);
		wrapper.setCapability(BranchGroup.ALLOW_DETACH);
		wrapper.compile();
		return wrapper;
	}

	BranchGroup sceneGroup;
	BranchGroup objectBranch;
	
	public void translateObject(double x, double y, double z) {
		Transform3D translate = new Transform3D();
		translate.setZero();
		translate.setTranslation(new Vector3d(x,y,z));
		Transform3D old = new Transform3D();
		shapeTransform.getTransform(old);
		old.add(translate);
		shapeTransform.setTransform(old);
	}
	
	/**
	 * Center the object tree and raise its lowest point to Z=0.
	 */
	public void center() {
		BoundingBox bb = getBoundingBox(shapeTransform);
		Point3d lower = new Point3d();
		Point3d upper = new Point3d();
		bb.getLower(lower);
		bb.getUpper(upper);
		double zoff = -lower.z;
		double xoff = -(upper.x + lower.x)/2.0d;
		double yoff = -(upper.y + lower.y)/2.0d;
		translateObject(xoff, yoff, zoff);
	}
	
	/**
	 * Center the object and flatten the bottommost poly.  (A more thorough version would
	 * be able to correctly center a tripod or other spiky object.)
	 */
	public void align() {
		center();
	}
	
	/**
	 * Flip the object tree around the Z axis.  This is particularly useful when
	 * breaking a print into two parts.
	 */
	public void flipZ() {
		Transform3D flipZ = new Transform3D();
		Transform3D old = new Transform3D();
		shapeTransform.getTransform(old);
		flipZ.rotY(Math.PI);
		flipZ.mul(old);
		shapeTransform.setTransform(flipZ);
	}
	
	public BranchGroup createSTLScene() {
		// Create the root of the branch graph
		BranchGroup objRoot = new BranchGroup();

		// Create the TransformGroup node and initialize it to the
		// identity. Enable the TRANSFORM_WRITE capability so that
		// our behavior code can modify it at run time. Add it to
		// the root of the subgraph.
		TransformGroup scaleTransform = new TransformGroup();
		// All sizes are represented in mm.  We scale this down so that 1mm == 0.01 units.
		Transform3D scaleTf = new Transform3D();
		scaleTf.setScale(0.01d);
		scaleTransform.setTransform(scaleTf);
		objRoot.addChild(scaleTransform);

		sceneGroup = new BranchGroup();
		sceneGroup.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
		sceneGroup.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
		sceneGroup.addChild(makeAmbientLight());
		sceneGroup.addChild(makeDirectedLight1());
		sceneGroup.addChild(makeDirectedLight2());
		sceneGroup.addChild(makeBoundingBox());
		sceneGroup.addChild(makeBackground());
		sceneGroup.addChild(makeBaseGrid());

		scaleTransform.addChild(sceneGroup);

		// Create a new Behavior object that will perform the
		// desired operation on the specified transform and add
		// it into the scene graph.
		//	Transform3D yAxis = new Transform3D();
		//	Alpha rotationAlpha = new Alpha(-1, 4000);

		//	RotationInterpolator rotator =
		//	    new RotationInterpolator(rotationAlpha, objTrans, yAxis,
		//				     0.0f, (float) Math.PI*2.0f);
		//	BoundingSphere bounds =
		//	    new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0);
		//	rotator.setSchedulingBounds(bounds);
		objRoot.compile();

		return objRoot;
	}

	// These values were determined experimentally to look pretty dang good.
	final static Vector3d CAMERA_TRANSLATION_DEFAULT = new Vector3d(0,0.4,2.9);
	final static double ELEVATION_ANGLE_DEFAULT = 1.278;
	final static double TURNTABLE_ANGLE_DEFAULT = 0.214;
	
	Vector3d cameraTranslation = new Vector3d(CAMERA_TRANSLATION_DEFAULT);
	double elevationAngle = ELEVATION_ANGLE_DEFAULT;
	double turntableAngle = TURNTABLE_ANGLE_DEFAULT;

	private void resetView() {
		cameraTranslation = new Vector3d(CAMERA_TRANSLATION_DEFAULT);
		elevationAngle = ELEVATION_ANGLE_DEFAULT;
		turntableAngle = TURNTABLE_ANGLE_DEFAULT;
		updateVP();
	}
	
	private void updateVP() {
		TransformGroup viewTG = univ.getViewingPlatform().getViewPlatformTransform();
		Transform3D t3d = new Transform3D();
		Transform3D trans = new Transform3D();
		Transform3D rotZ = new Transform3D();
		Transform3D rotX = new Transform3D();
		trans.setTranslation(cameraTranslation);
		rotX.rotX(elevationAngle);
		rotZ.rotZ(turntableAngle);
		t3d.mul(rotZ);
		t3d.mul(rotX);
		t3d.mul(trans);
		viewTG.setTransform(t3d);

		if (Base.logger.isLoggable(Level.FINE)) {
			Base.logger.fine("Camera Translation: "+cameraTranslation.toString());
			Base.logger.fine("Elevation "+Double.toString(elevationAngle)+", turntable "+Double.toString(turntableAngle));
		}
	}

	private Canvas3D createUniverse() {
		// Get the preferred graphics configuration for the default screen
		GraphicsConfiguration config =
			SimpleUniverse.getPreferredConfiguration();

		// Create a Canvas3D using the preferred configuration
		Canvas3D c = new Canvas3D(config) {
			public Dimension getMinimumSize()
		    {
		        return new Dimension(0, 0);
		    }
		};

		// Create simple universe with view branch
		univ = new SimpleUniverse(c);
		univ.getViewer().getView().setSceneAntialiasingEnable(true);
		updateVP();

		// Ensure at least 5 msec per frame (i.e., < 200Hz)
		univ.getViewer().getView().setMinimumFrameCycleTime(5);

		return c;
	}


}
