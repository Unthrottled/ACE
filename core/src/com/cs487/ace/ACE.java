package com.cs487.ace;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader;
import com.badlogic.gdx.graphics.g3d.particles.ParticleSystem;
import com.badlogic.gdx.graphics.g3d.particles.batches.PointSpriteParticleBatch;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;



/**
 * Things to do:
 *  -Create 2 separate cockpits, one with a green light and the other with a red light to indicated missile status
 *      (or just do something clever with 2d graphics) DONE
 *  -Create a object re-spawn count down, put it in game object or something. DONE
 *  -Move objects away from there original x-axis coordinate after being hit by ordinance. DONE
 *  -Work on moving objects toward the user when the moveObjectAhead.
 *  -Create a developer/debug flag or something for all of the information DONE
 *  -Move missle to launch behind user. DONE
 *  -Create a game model with a GUI method to return a string for the hud information or something DONE
 *  -Find better models for user to shoot at.
 *
 *  -Fix sound lagging up tablet.
 *  -Mess around with particle trails if you have the time later.
 *
 */


public class ACE extends ApplicationAdapter {
	private AceGameModel aceGameModel;

    //drawables
    private SpriteBatch spriteBatch;
	private Texture img, ckpRdy, ckpLchd, ldScrn, chrHair, inst;
    private Sprite cockPit, cockpitReady, cockpitLaunched, loadScreen, crosshair, instructions;

    //sound effects
    private Music theme;
    private Sound ordinanceLaunch, ordinanceContact;
    private long ordinanceLaunchId, ordinanceContactId;

    //hud stuff
    private BitmapFont font;
    private StringBuilder devInfo;
    private String LOADING = "LOADING!!";
    private int deviceWidth, deviceHeight;
    private BitmapFont scoreFont, killCountFont, timeElapsedFont, ordinanceStatusFont;

    //Tenative HUD options
    //private BitmapFont health, ordinanceType, speed;

    private Color scoreColor = Color.valueOf("cf911a");
    private Color killCountColor = Color.valueOf("c40707");
    private Color timeElapsedColor = Color.valueOf("c6deaa");

    //3d stuff
    public PerspectiveCamera cam;
    public PerspectiveCamController camController;
    public ModelBatch modelBatch;
    public AssetManager assets;
    public Array<GameObject> modelInstances = new Array<GameObject>();
    public Environment environment;
    public boolean loading;
    ParticleSystem particleSystem;
    PointSpriteParticleBatch pointSpriteBatch;
    ParticleEffect effect;

    //Game Logic Stuff
    //private Vector3 modelPosition = new Vector3();
    private float modelDistance = -150f;
    private float jetOffsetZ = 10f;
    private static final float ordinanceLife = 150f;
    private float ordinanceTimeout;
    private boolean instAcknowledged = false;

    private float pitch = 0, lastPitch = 0;
    private float roll = 90f, lastRoll = 90f;
    private float posX = 0f;
    private float posZ = 0f;
    private float posY = 0f;
    private float defaultRollOrientation = 90f;//Reverse Landscape At the moment.

    private float shipStrafeAcceleration = 0.015f;
    private float shipClimbAcceleration = 0.010f;
    private static final float shipMovementSpeed = -0.225f;
    private static final float shipXOrientation = 0f;

    private static final float mapBounds = 250f;

    //ordinance stuff
    private static final float ordinanceY = -0.2f;
    //private boolean ordinanceLaunched = false;
    private boolean ordinanceRegistered = false;
    private boolean ordinanceCollision = false;
    private float lastOrdX = 0f;
    private float lastOrdZ = 0f;
    private float lastOrdY = 0f;
    private float lastOrdRoll = 90f;
    private float ordinanceZTravel = 0f;
    private btCollisionConfiguration collisionConfig;
    private btDispatcher dispatcher;

    //Frustum Culling Stuff
    private static Vector3 position = new Vector3();
    private int modelsVisible;
    private Vector3 temp = new Vector3();

    //Developer flag to game info or not.
    private static final boolean isDev = true;

    //Libgdx's Tutorial Game Object
    //With a mix of my game model stuff in there, also minus scene implementation of models.
    //Consider making this a separate class so you can make abstractions of it. If time applies
    public static class GameObject extends ModelInstance implements Disposable {
        public final Vector3 center = new Vector3();
        public final Vector3 dimensions = new Vector3();
        public final float radius;
        public final btCollisionObject body;

        //gamelogic within the model
        public boolean ordinanceHit = false;
        public  String name = null;
        public static final float RESPAWN_TIME = 200f;
        private float respawnTimout = 0;

        private final static BoundingBox bounds = new BoundingBox();

        public GameObject(Model model, btCollisionShape shape, String modelName) {
            super(model);
            model.calculateBoundingBox(bounds);
            bounds.getCenter(center);
            bounds.getDimensions(dimensions);
            radius = dimensions.len() / 2f;
            body = new btCollisionObject();
            body.setCollisionShape(shape);
            this.name = modelName;
            respawnTimout = RESPAWN_TIME;
        }

        public void dispose () {
            body.dispose();
        }

        public boolean isVisible(Camera cam) {
            return cam.frustum.sphereInFrustum(transform.getTranslation(position).add(center), radius);
        }

        public boolean isGhost(){
            return --respawnTimout > 0;
        }
        public void setRespawnTimout(){
            respawnTimout = RESPAWN_TIME;
        }

        static class Constructor implements Disposable {
            public final Model model;
            public final String node;
            public final btCollisionShape shape;
            public Constructor(Model model, String node, btCollisionShape shape) {
                this.model = model;
                this.node = node;
                this.shape = shape;
            }

            public GameObject construct() {
                return new GameObject(model, shape, null);
            }

            @Override
            public void dispose () {
                shape.dispose();
            }
        }
    }


    @Override
	public void create () {
        deviceWidth = Gdx.graphics.getWidth();
        deviceHeight = Gdx.graphics.getHeight();

        aceGameModel = new AceGameModel();

		spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.LIGHT_GRAY);
//        font.setScale(3f);

        //sound initialization
        //theme = Gdx.audio.newMusic(Gdx.files.internal("murica.mp3"));
        //More Appropriate Theme For Submission :)
        theme = Gdx.audio.newMusic(Gdx.files.internal("danger_zone.mp3"));
        theme.setLooping(true);
        theme.setVolume(0.5f);
        ordinanceLaunch = Gdx.audio.newSound(Gdx.files.internal("launch.wav"));
        ordinanceContact = Gdx.audio.newSound(Gdx.files.internal("explode.wav"));


        //hud initialization
        scoreFont = new BitmapFont();
        killCountFont = new BitmapFont();
        timeElapsedFont = new BitmapFont();
        //ordinanceStatusFont = new BitmapFont();

        scoreFont.setColor(scoreColor);
        killCountFont.setColor(killCountColor);
        timeElapsedFont.setColor(timeElapsedColor);
        //ordinanceStatusFont.setColor(Color.GREEN);

//        scoreFont.setScale(2f,2.5f);
//        killCountFont.setScale(2f,2.5f);
//        timeElapsedFont.setScale(2f,2.5f);
        //ordinanceStatusFont.setScale(1f,1.5f);

        //Partial Credit to: http://homepage1.nifty.com/avionics/f22adf/cockpit.jpg
        img = new Texture("cockpit.png");

        ckpLchd = new Texture("cockpit_ordinance_launched.png");
        ckpRdy = new Texture("cockpit_ordinance_ready.png");
        ldScrn = new Texture("load_screen_text.png");
        chrHair = new Texture("cross-hair.png");
        inst = new Texture("load_screen_instructions.png");

        cockPit = new Sprite(img);
        cockpitReady = new Sprite(ckpRdy);
        cockpitLaunched = new Sprite(ckpLchd);
        loadScreen = new Sprite(ldScrn);
        crosshair = new Sprite(chrHair);
        instructions = new Sprite(inst);

        loadScreen.setSize(deviceWidth,deviceHeight);

        //ordinance stuff
        ordinanceTimeout = ordinanceLife;
        Bullet.init();
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);

        //current dimensions are 700x400
        //fitting to current display
        float sizeY = deviceHeight * 0.8f;
        float sizeX = sizeY * 1.75f;
        float offsetX = deviceWidth * 0.1f;
        //cockPit.setSize(sizeX,sizeY);
        //cockPit.setPosition(offsetX, 0);
        cockpitReady.setSize(sizeX,sizeY);
        cockpitReady.setPosition(offsetX, 0);
        cockpitLaunched.setSize(sizeX,sizeY);
        cockpitLaunched.setPosition(offsetX, 0);

        sizeY *= 0.375;
        sizeX *= 0.214;
        crosshair.setSize(sizeX,sizeY);
        crosshair.setPosition((deviceWidth/2) - crosshair.getWidth()/2, (deviceHeight/2));


        devInfo = new StringBuilder();
        //Gdx.graphics.setContinuousRendering(true);
        Gdx.graphics.requestRendering();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(new MyInput());
        //multiplexer.addProcessor(new GameInput());
        Gdx.input.setInputProcessor(multiplexer);

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
        cam = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0f, 0f, jetOffsetZ);
        //cam.rotateAround(Vector3.Zero,Vector3.X, shipXOrientation);
        cam.lookAt(0,-3f,0);
        cam.near = 1f;
        cam.far = 1000f;
        cam.update();

        //partical effects
        particleSystem = ParticleSystem.get();
        pointSpriteBatch = new PointSpriteParticleBatch();
        pointSpriteBatch.setCamera(cam);
        particleSystem.add(pointSpriteBatch);


        //camController = new PerspectiveCamController(cam);
        //Gdx.input.setInputProcessor(camController);

        assets = new AssetManager();

        //Game Models
        assets.load("powerup.obj", Model.class);
        assets.load("rock.obj", Model.class);
        assets.load("sky.obj", Model.class);
        assets.load("shipBounds.obj", Model.class);
        //assets.load("commie_assets/Su-35/Su-35_SuperFlanker.obj", Model.class);
        assets.load("kinetic_barrier.png", Texture.class);

        //Free model provided by: http://www.gameartmarket.com/details?id=ag9zfmRhZGEtM2RtYXJrZXRyMgsSBFVzZXIiE3BpZXJ6YWtAcGllcnphay5jb20MCxIKVXNlclVwbG9hZBjfjJmo6ycM
        assets.load("missile_1.obj", Model.class);
        assets.load("missile_diffuse.jpg", Texture.class);

        //courtesy of: http://miriadna.com/desctopwalls/images/max/Blue-sea-horizon.jpg
        assets.load("sky.jpg", Texture.class);

        //partical effects
        ParticleEffectLoader.ParticleEffectLoadParameter loadParam = new ParticleEffectLoader.ParticleEffectLoadParameter(particleSystem.getBatches());
        ParticleEffectLoader loader = new ParticleEffectLoader(new InternalFileHandleResolver());
        assets.setLoader(ParticleEffect.class, loader);
        assets.load("trail.pfx", ParticleEffect.class, loadParam);

        loading = true;
	}

    private void doneLoading() {
        Model sky = assets.get("sky.obj", Model.class);
        GameObject skyInstance = new GameObject(sky,new btSphereShape(0.5f),"sky");
        skyInstance.body.setWorldTransform(skyInstance.transform);
    
        skyInstance.transform.scale(6.0f,6.0f,6.0f);
        //skyInstance.transform.rotate(1,0,0,shipXOrientation);
        //skyInstance.transform.setFromEulerAngles(0,90,0);
        Material skyMaterial = skyInstance.materials.get(0);
        TextureAttribute skyTextureAttribute = new TextureAttribute(TextureAttribute.Diffuse, assets.get("sky.jpg", Texture.class));
        skyMaterial.set(skyTextureAttribute);
        skyInstance.materials.add(skyMaterial);
        modelInstances.add(skyInstance);

        Model ordinance = assets.get("missile_1.obj", Model.class);
        GameObject ordinanceInstance = new GameObject(ordinance,new btCapsuleShape(0.8f, 2f),"ordinance");
        ordinanceInstance.transform.translate(posX, ordinanceY, jetOffsetZ + 2f );
        ordinanceInstance.body.setWorldTransform(ordinanceInstance.transform);
        Material ordinanceMaterial = ordinance.materials.get(0);
        TextureAttribute ordinanceTextureAttribute = new TextureAttribute(TextureAttribute.Diffuse, assets.get("missile_diffuse.jpg", Texture.class));
        ordinanceMaterial.set(ordinanceTextureAttribute);
        ordinanceInstance.materials.add(ordinanceMaterial);
        modelInstances.add(ordinanceInstance);

        lastOrdX = posX;
        lastOrdZ = jetOffsetZ + 2f;
        lastOrdY = posY;

        TextureAttribute enemyTextureAttribute = new TextureAttribute(TextureAttribute.Diffuse, assets.get("kinetic_barrier.png", Texture.class));
        Model enemy = assets.get("rock.obj", Model.class);
        GameObject enemyInstance = new GameObject(enemy, new btSphereShape(1f),"enemy");
        Material enemyMaterial = enemyInstance.materials.get(0);
        enemyMaterial.set(enemyTextureAttribute);
        enemyInstance.materials.add(enemyMaterial);
        enemyInstance.transform.translate(0.2f, ordinanceY, -10.3f);
        enemyInstance.body.setWorldTransform(enemyInstance.transform);
        modelInstances.add(enemyInstance);

        Model rock = assets.get("rock.obj", Model.class);
        GameObject rockInstance = new GameObject(rock, new btSphereShape(0.75f),"enemy");
        Material rockMaterial = rockInstance.materials.get(0);
        rockMaterial.set(enemyTextureAttribute);
        rockInstance.materials.add(rockMaterial);
        rockInstance.transform.translate(8f, ordinanceY,2.5f);
        rockInstance.body.setWorldTransform(rockInstance.transform);
        modelInstances.add(rockInstance);

        Model rock1 = assets.get("rock.obj", Model.class);
        GameObject rockInstance1 = new GameObject(rock1, new btSphereShape(0.75f),"enemy");
        Material rockMaterial1 = rockInstance1.materials.get(0);
        rockMaterial1.set(enemyTextureAttribute);
        rockInstance1.materials.add(rockMaterial1);
        rockInstance1.transform.translate(-4f, ordinanceY,-10.5f);
        rockInstance1.body.setWorldTransform(rockInstance1.transform);
        modelInstances.add(rockInstance1);

        Model rock2 = assets.get("rock.obj", Model.class);
        GameObject rockInstance2 = new GameObject(rock2, new btSphereShape(0.75f),"enemy");
        Material rockMaterial2 = rockInstance2.materials.get(0);
        rockMaterial2.set(enemyTextureAttribute);
        rockInstance.materials.add(rockMaterial2);
        rockInstance2.transform.translate(-8f, ordinanceY,-20.5f);
        rockInstance2.body.setWorldTransform(rockInstance2.transform);
        modelInstances.add(rockInstance2);

        Model rock3 = assets.get("rock.obj", Model.class);
        GameObject rockInstance3 = new GameObject(rock3, new btSphereShape(0.75f),"enemy");
        Material rockMaterial3 = rockInstance3.materials.get(0);
        rockMaterial3.set(enemyTextureAttribute);
        rockInstance3.materials.add(rockMaterial3);
        rockInstance3.transform.translate(-12f, ordinanceY,-30.5f);
        rockInstance3.body.setWorldTransform(rockInstance3.transform);
        modelInstances.add(rockInstance3);

        Model rock4 = assets.get("rock.obj", Model.class);
        GameObject rockInstance4 = new GameObject(rock4, new btSphereShape(0.75f),"enemy");
        Material rockMaterial4 = rockInstance4.materials.get(0);
        rockMaterial4.set(enemyTextureAttribute);
        rockInstance4.materials.add(rockMaterial4);
        rockInstance4.transform.translate(12f, ordinanceY,-40.5f);
        rockInstance4.body.setWorldTransform(rockInstance4.transform);
        modelInstances.add(rockInstance4);

        Model rock5 = assets.get("rock.obj", Model.class);
        GameObject rockInstance5 = new GameObject(rock5, new btSphereShape(0.75f),"enemy");
        Material rockMaterial5 = rockInstance5.materials.get(0);
        rockMaterial5.set(enemyTextureAttribute);
        rockInstance5.materials.add(rockMaterial5);
        rockInstance5.transform.translate(4f, ordinanceY,-50.5f);
        rockInstance5.body.setWorldTransform(rockInstance5.transform);
        modelInstances.add(rockInstance5);

        //Libgdx's bullet wrapper model demo sphere
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.node().id = "sphere";
        mb.part("sphere", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.RED)))
                .sphere(1f, 1f, 1f, 10, 10);
        Model sphereModel = mb.end();
        GameObject sphereInstance = new GameObject(sphereModel,new btSphereShape(1f),"enemy 1");
        sphereInstance.transform.translate(-6f, ordinanceY, -20f);
        sphereInstance.body.setWorldTransform(sphereInstance.transform);
        modelInstances.add(sphereInstance);

        assets.finishLoading();

        ParticleEffect originalEffect = assets.get("trail.pfx");
        // we cannot use the originalEffect, we must make a copy each time we create new particle effect
        effect = originalEffect.copy();
        effect.init();
        effect.start();
        particleSystem.add(effect);

        loading = false;
    }

	@Override
	public void render () {
        final float delta = Math.min(1f/30f, Gdx.graphics.getDeltaTime());

        aceGameModel.setFreedomSeconds(delta);

        //Altering game logic based on user inputs
        handleInput();

        //Setting the OpenGL viewport
        //Clearing out the buffers for fresh rendering
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glClearColor(0.64f, 0.28f, 0.28f, 1);

        //Getting device orientation input from the user
        if (Gdx.input.isPeripheralAvailable(Input.Peripheral.Compass)) {
            pitch = Gdx.input.getPitch();
            roll = Gdx.input.getRoll();
            if(isDev) {
                devInfo.append("Azimuth:").append(Float.toString(Gdx.input.getAzimuth())).append("\n");
                devInfo.append("Pitch:").append(Float.toString(pitch)).append("\n");
                devInfo.append("Roll:").append(Float.toString(Gdx.input.getRoll())).append("\n");
            }
        }


        //If the the pitch threshold is breached
        //Do some voodo magic to rotate screen
        //if (Math.abs(lastRoll - roll) > 0.01)
        //    pitchCam();

        //If the the rotation threshold is breached
        //Do some voodo magic to rotate screen
        if (Math.abs(lastPitch - pitch) > 0.01)
            rotateCam();

       // rotate();

        //Allows for models to load onto the screen while the user is waiting.
        if (loading && assets.update())
            doneLoading();

        //camController.update();

        //Applying all changes made to the camera
        cam.update();

        //Developer Info
        if(isDev) {
            devInfo.append("Ship Position X: ").append(posX).append('\n');
            devInfo.append("Ship Position Z: ").append(posZ).append('\n');
            devInfo.append("Ship Position Y: ").append(posY).append('\n');
            devInfo.append("Ordinance Timeout: ").append(ordinanceTimeout).append('\n');
        }

        modelsVisible = 0;

        //All of the 3D models set to be rendered
        //here is a good place to have already checked for vida game logic
        modelBatch.begin(cam);
        //modelBatch.render(modelInstances, environment);
        for (final GameObject instance : modelInstances) {
            //ordinance object detection
            if (instance.name.equals("ordinance") && ordinanceRegistered && ordinanceTimeout > 0) {
                ordinanceTimeout--;
                devInfo.append("launched\n");
                if(!ordinanceCollision){
                    modelBatch.render(instance, environment);
                    particleSystem.update();
                    particleSystem.begin();
                    particleSystem.draw();
                    particleSystem.end();
                    modelBatch.render(particleSystem);
                }
            } else if (instance.isVisible(cam) && !instance.name.equals("ordinance")) {
                if(!instance.ordinanceHit) {
                    modelBatch.render(instance, environment);
                    modelsVisible++;
                    if (ordinanceRegistered && !instance.name.equals("sky")) {
                        if (checkCollision(instance.body, modelInstances.get(1).body)) {
                            instance.ordinanceHit = true;
                            ordinanceCollision = true;
                            instance.setRespawnTimout();
                            ordinanceLaunch.stop(ordinanceLaunchId);
                            ordinanceContact.play(2f);
                            aceGameModel.commieLiberated();
                            aceGameModel.setFreedomPoints();
                        }
                    }
                }else{
                    if(!instance.isGhost()){
                        instance.ordinanceHit = false;
                        instance.setRespawnTimout();
                        double xPos = (Math.random() * 20) + 1;
                        double zPos = (Math.random() * 50) + 1;
                        if(Math.ceil(Math.random()) == 1)
                            xPos = -xPos;
                        instance.transform.translate((float)xPos,0f,(float)-zPos);
                        instance.body.setWorldTransform(instance.transform);
                    }
                }
            } else {
                //ordinance object
                if (instance.name.equals("ordinance")) {
                    //ordinance has been fired and is out of time.
                    if (ordinanceRegistered && ordinanceTimeout <= 0) {
                        ordinanceRegistered = false;

                        //Reset Hit counters
                        //for(int i = 0; i <modelInstances.size;i++){
                        //    modelInstances.get(i).ordinanceHit = false;
                        //}

                        aceGameModel.setOrdinanceLaunched(false);
                        if(!ordinanceCollision)
                            aceGameModel.freedomIsNotFree();
                        ordinanceCollision = false;
                    }
                } else {
                    moveObjectAhead(instance);
                }
            }
        }
        modelBatch.end();

        //Developer Info
        if(isDev) {
            devInfo.append("Visible: ").append(modelsVisible).append("\n");
        }

        //Drawing the the 2d String builder
        spriteBatch.begin();

        //Developer Information
        if(isDev) {
            font.draw(spriteBatch, devInfo, 0, deviceHeight);
        }

        //Indication of loading status or actual game HUD info
        //might change it to start screen
        if (!assets.update()) {

            loadScreen.draw(spriteBatch);

            //replaced by loadScreen
            //font.scale(2.0f);
            //font.setColor(Color.valueOf("c40707"));
            //font.drawMultiLine(spriteBatch, LOADING, (deviceWidth / 2)-(font.getBounds(LOADING).width/2), deviceHeight / 2);
            //font.scale(-2f);
            //font.setColor(Color.WHITE);
        }
        else {
            if (instAcknowledged) {
                crosshair.draw(spriteBatch);

                //Good place to put cockpit :)
                //cockPit.draw(spriteBatch);
                if (ordinanceRegistered)
                    cockpitLaunched.draw(spriteBatch);
                else
                    cockpitReady.draw(spriteBatch);

                //Game Hud Info
                if (!isDev) {
                    timeElapsedFont.draw(spriteBatch,
                            aceGameModel.getTimeElapsedText(), 0, deviceHeight);
//                    killCountFont.draw(spriteBatch,
//                            aceGameModel.getKillCountText(), deviceWidth - killCountFont.getBounds(aceGameModel.getKillCountText()).width,
//                            deviceHeight - scoreFont.getBounds(aceGameModel.getKillCountText()).height);
//                    scoreFont.draw(spriteBatch,
//                            aceGameModel.getScoreText(), deviceWidth - scoreFont.getBounds(aceGameModel.getScoreText()).width,
//                            deviceHeight);

                    //replaced by cockpitReady and cockpitLaunched
                    //ordinanceStatusFont.drawMultiLine(spriteBatch,
                    //        aceGameModel.getOrdinanceStatusText(),
                    //        (deviceWidth/2) - (ordinanceStatusFont.getBounds(aceGameModel.getOrdinanceStatusText()).width/2),
                    //        deviceHeight-ordinanceStatusFont.getBounds(aceGameModel.getOrdinanceStatusText()).height);
                }
            }else
                instructions.draw(spriteBatch);
        }
        spriteBatch.end();

        //Clearing out the font, consider making a gui class :)
        if(isDev) {
            devInfo.setLength(0);
        }


    }

    //Libgdx's tutorial on the bullet wrapper collision tests
    //Most handy when checking for collisions between ordinance and
    //opposition objects.
    boolean checkCollision(btCollisionObject obj0, btCollisionObject obj1) {
        CollisionObjectWrapper co0 = new CollisionObjectWrapper(obj0);
        CollisionObjectWrapper co1 = new CollisionObjectWrapper(obj1);

//        btCollisionAlgorithm algorithm = dispatcher.freeCollisionAlgorithm(Collision.bt_line_plane_collision());

        btDispatcherInfo info = new btDispatcherInfo();
        btManifoldResult result = new btManifoldResult(co0.wrapper, co1.wrapper);

//        algorithm.processCollision(co0.wrapper, co1.wrapper, info, result);

        boolean r = result.getPersistentManifold().getNumContacts() > 0;

//        dispatcher.freeCollisionAlgorithm(algorithm.getCPointer());
        result.dispose();
        info.dispose();
        co1.dispose();
        co0.dispose();

        return r;
    }

    //When object is out of the frustum volume move it back in the viewers perspective
    private void moveObjectAhead(GameObject instance){
        if(assets.update() && !loading) {
            //instance.transform.getTranslation(modelPosition).;
            //modelDistance = modelPosition.dst2(cam.position);
            //modelDistance -= 2f;
            //temp = cam.position;
            //temp.nor();
            double xPos = (Math.random() * 5) + 1;
            //double zPos = (Math.random() * 10) + 1;
            //double yPos = (Math.random() * 5) + 1;
            if(Math.ceil(Math.random()) == 1)
                xPos = -xPos;
            //if(Math.random() < 0.5)
            //    yPos = -yPos;
            //temp.set((float)(temp.x - xPos), (float)(temp.y + yPos),temp.z);
            instance.transform.translate((float)xPos,0f,-55f);
            instance.body.setWorldTransform(instance.transform);
        }
    }


    private void handleInput(){
        //Don't want the user flying around until they can see the sky :)
        if(assets.update() && !loading) {
            //User has the screen touched
            if (Gdx.input.isTouched() && instAcknowledged) {
                if (!ordinanceRegistered) {
                    //bring the ordinance back the distance it traveled
                    modelInstances.get(1).transform.translate(0, 0, -(ordinanceZTravel));

                    //place it where the jet's current position is
                    modelInstances.get(1).transform.translate(posX - lastOrdX, 0/*posY - lastOrdY*/, (posZ - lastOrdZ) + 2f);
                    // modelInstances.get(1).transform.rotate(Vector3.X,roll - lastOrdRoll);
                    modelInstances.get(1).body.setWorldTransform(modelInstances.get(1).transform);

                    effect.setTransform(modelInstances.get(1).transform);


                    ordinanceRegistered = true;
                    lastOrdX = posX;
                    lastOrdZ = posZ;
                    lastOrdY = posY;
                    lastOrdRoll = roll;
                    ordinanceZTravel = 0;
                    ordinanceTimeout = ordinanceLife;

                    ordinanceLaunchId = ordinanceLaunch.play(1f);

                    aceGameModel.setOrdinanceLaunched(true);
                    effect.start();  // optional: particle will begin playing immediately
                }
            } else if (Gdx.input.isTouched()) {
                instAcknowledged = true;
                theme.play();
                return;
            }
            if (instAcknowledged) {
                //Determines how far the ship moves during each
                //delta/render, more pitch more to the left or right
                float shipStrafe = pitch * shipStrafeAcceleration;

                //Determines how far the ship moves during each
                //delta/render, more roll more to the up or down
                //float shipClimb = (roll - defaultRollOrientation) * shipClimbAcceleration;


                //float nextPosX = posX + shipStrafe;

                //If position to be moved to is greater than map bounds
                //set translation to the difference so player can sit at
                //mapBounds
                //if(nextPosX > mapBounds || nextPosX < -mapBounds){
                //    shipStrafe = nextPosX - mapBounds;
                //}

                //keeping track of where the player is
                posX += shipStrafe;
                posZ += shipMovementSpeed;
                //posY += shipClimb;

                //move Dat sky
                modelInstances.get(0).transform.translate(shipStrafe * 0.166f, 0/*shipClimb*0.166f*/, shipMovementSpeed * 0.166f);

                //ordinance logic
                if (ordinanceRegistered && !ordinanceCollision) {
                    modelInstances.get(1).transform.translate(0f, 0f, shipMovementSpeed * 10f);
                    modelInstances.get(1).body.setWorldTransform(modelInstances.get(1).transform);
                    effect.setTransform(modelInstances.get(1).transform);
                    effect.translate(new Vector3(0, 0, 0.75f));
                    ordinanceZTravel += shipMovementSpeed * 10f;
                }

                //To avoid complicated maths I'm just reoriented the camera
                //normally and then translating it, then rotating it back.
                //The user will not see this as the camera hasn't been updated
                //yet so it will move and look rotated. And I don't have to maths :)
                //cam.rotateAround(cam.position, Vector3.Z, pitch);
                cam.translate(shipStrafe, 0, shipMovementSpeed);

                //cam.rotateAround(cam.position, Vector3.Z, -pitch);
            }
        }

    }

    private void rotateCam() {
        //The change of the roll angle since the threshold was last broken
        float angleOfRotation = -(pitch - lastPitch);

        //Rotating the camera around its Z-Axis
        cam.rotate(Vector3.Z, angleOfRotation);

        //Rotating the crosshair
        crosshair.setRotation(angleOfRotation);

        //Recording current roll for next threshold
        lastPitch = pitch;

    }

    private void pitchCam(){
        //The change of the pitch angle since the threshold was last broken
        float angleOfPitch = (roll - lastRoll);

        //Rotating the camera around its X-Axis
        cam.rotate(Vector3.X, angleOfPitch);

        //Recording current pitch for next threshold
        lastRoll = roll;
    }

   /* private void rotate(){
        cam.direction.x = 0;
        cam.direction.y = 0;
        cam.direction.z = 1;
        cam.up.x = 0;
        cam.up.y = 1;
        cam.up.z = 0;
        cam.position.x = 0;
        cam.position.y = 0;
        cam.position.z = 0;
        cam.update();

        cam.rotate(pitch,0,1,0);
        Vector3 pivot = cam.direction.cpy().crs(cam.up);
        cam.rotate(roll,pivot.x,pivot.y,pivot.z);
        cam.rotate(pitch,cam.direction.x, cam.direction.y,cam.direction.z);
        cam.update();

    }*/


    public void dispose(){
        spriteBatch.dispose();
        font.dispose();
        killCountFont.dispose();
        timeElapsedFont.dispose();
        scoreFont.dispose();
        //ordinanceStatusFont.dispose();
        modelBatch.dispose();
        for (GameObject obj : modelInstances)
            obj.dispose();
        modelInstances.clear();
        assets.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
        theme.dispose();
        ordinanceContact.dispose();
        ordinanceLaunch.dispose();
        effect.dispose();
    }

    public void pause(){

    }

    public void resume(){

    }
}
