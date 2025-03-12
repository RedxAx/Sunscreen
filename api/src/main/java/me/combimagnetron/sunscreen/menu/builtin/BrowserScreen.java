package me.combimagnetron.sunscreen.menu.builtin;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.misc.BoolRef;
import me.combimagnetron.passport.internal.entity.impl.display.TextDisplay;
import me.combimagnetron.passport.internal.entity.impl.display.Display;
import me.combimagnetron.passport.internal.entity.metadata.type.Vector3d;
import me.combimagnetron.sunscreen.SunscreenLibrary;
import me.combimagnetron.sunscreen.image.Canvas;
import me.combimagnetron.sunscreen.image.CanvasRenderer;
import me.combimagnetron.sunscreen.menu.Menu;
import me.combimagnetron.sunscreen.menu.timing.MenuTicker;
import me.combimagnetron.sunscreen.menu.timing.Tick;
import me.combimagnetron.sunscreen.user.SunscreenUser;
import me.combimagnetron.sunscreen.util.Scheduler;
import me.combimagnetron.sunscreen.util.Vec2d;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserScreen extends Menu.Float {
    private static final Logger log = LoggerFactory.getLogger(BrowserScreen.class);
    private final SunscreenUser<?> viewer;
    private CefApp cefApp;
    private CefClient cefClient;
    private CefBrowser cefBrowser;
    private JFrame browserFrame;
    private volatile boolean running = true;
    private TextDisplay browserDisplay;
    private final TextDisplay cursorDisplay = TextDisplay.textDisplay(Vector3d.vec3(0));
    private Vec2d lastInput = Vec2d.of(0, 0);
    private PacketListenerCommon listener;
    private volatile boolean cefReady = false;
    private long lastOutputTime = 0;
    public BrowserScreen(SunscreenUser<?> viewer) {
        super(viewer);
        this.viewer = viewer;
        initializeCef();
        hideCursor();
        open(viewer);
    }
    private void initializeCef() {
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = false;
        SwingUtilities.invokeLater(() -> {
            cefApp = CefApp.getInstance(settings);
            cefClient = cefApp.createClient();
            cefClient.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
                @Override
                public void onAfterCreated(CefBrowser browser) {
                    SwingUtilities.invokeLater(() -> {
                        if (browserFrame == null) {
                            browserFrame = new JFrame();
                            browserFrame.getContentPane().add(cefBrowser.getUIComponent());
                            browserFrame.pack();
                            browserFrame.setSize(800, 600);
                            browserFrame.setUndecorated(true);
                            browserFrame.setLocation(-2000, -2000);
                            browserFrame.setVisible(true);
                        }
                        cefReady = true;
                    });
                }
            });
            cefClient.addKeyboardHandler(new CefKeyboardHandler() {
                @Override
                public boolean onPreKeyEvent(CefBrowser browser, CefKeyboardHandler.CefKeyEvent event, BoolRef isKeyboardShortcut) {
                    return false;
                }
                @Override
                public boolean onKeyEvent(CefBrowser browser, CefKeyboardHandler.CefKeyEvent event) {
                    return false;
                }
            });
            cefBrowser = cefClient.createBrowser("https://www.google.com", false, false);
            cefBrowser.getUIComponent().setSize(800, 600);
            cefBrowser.getUIComponent().setVisible(true);
        });
    }
    protected void hideCursor() {
        cursorDisplay.text(Component.empty());
        java.util.List<EntityData> entityData = cursorDisplay.type().metadata().entityData();
        viewer.connection().send(new WrapperPlayServerEntityMetadata(cursorDisplay.id().intValue(), entityData));
    }
    protected void showCursor() {
        cursorDisplay.text(Component.text("e").font(Key.key("comet:arrow")));
        cursorDisplay.backgroundColor(0);
        cursorDisplay.billboard(Display.Billboard.CENTER);
        Display.Transformation trans = Display.Transformation.transformation().translation(Vector3d.vec3(0, 0, -0.24999))
                .scale(Vector3d.vec3(1/24.0, 1/24.0, 1/24.0));
        cursorDisplay.transformation(trans);
        viewer.show(cursorDisplay);
    }
    private void initListener() {
        listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_ROTATION) {
                    WrapperPlayClientPlayerRotation packet = new WrapperPlayClientPlayerRotation(event);
                    float yaw = packet.getYaw();
                    float pitch = -packet.getPitch();
                    lastInput = Vec2d.of(yaw, pitch).sub(lastInput).div(500);
                    move();
                } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.ENTITY_ACTION) {
                    WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event);
                    if (packet.getAction() == WrapperPlayClientEntityAction.Action.STOP_SNEAKING) {
                        leave();
                    }
                }
            }
        }, PacketListenerPriority.LOWEST);
    }
    private void move() {
        Vec2d translation = lastInput.mul(1);
        Display.Transformation trans = Display.Transformation.transformation().translation(Vector3d.vec3(translation.x(), translation.y(), -0.24999))
                .scale(Vector3d.vec3(1/24.0, 1/24.0, 1/24.0));
        cursorDisplay.transformation(trans);
        Menu.MenuHelper.send(viewer, cursorDisplay);
        forwardMouseMove((int) translation.x(), (int) translation.y());
    }
    @Override
    public void open(SunscreenUser<?> user) {
        user.connection().send(new WrapperPlayServerPlayerRotation(0, -180));
        initListener();
        TextDisplay camera = TextDisplay.textDisplay(Vector3d.vec3(user.position().x(), user.position().y() + 1.6, user.position().z()));
        camera.rotation(user.rotation());
        user.show(camera);
        if (SunscreenLibrary.library().config().forceShaderFov()) {
            user.fov(70);
        }
        user.connection().send(new WrapperPlayServerCamera(camera.id().intValue()));
        user.connection().send(new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(new UserProfile(user.uniqueIdentifier(), user.name()), true, 0,
                        GameMode.SPECTATOR, Component.empty(), null)));
        user.connection().send(new WrapperPlayServerChangeGameState(WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE, 3));
        browserDisplay = TextDisplay.textDisplay(user.position());
        browserDisplay.transformation(Display.Transformation.transformation().translation(Vector3d.vec3(0, 0, -0.25))
                .scale(Vector3d.vec3(1, 1, 1)));
        browserDisplay.lineWidth(200000);
        user.show(browserDisplay);
        showCursor();
        java.util.List<Integer> entityIds = new java.util.ArrayList<>();
        entityIds.add(user.entityId());
        entityIds.add(cursorDisplay.id().intValue());
        entityIds.add(browserDisplay.id().intValue());
        Scheduler.async(() -> {
            user.connection().send(new WrapperPlayServerSetPassengers(camera.id().intValue(), entityIds.stream().mapToInt(Integer::intValue).toArray()));
            return null;
        });
        MenuTicker menuTicker = new MenuTicker();
        menuTicker.start(this);
    }
    @Override
    public void tick(Tick tick) {
        if (!running || !cefReady || browserFrame == null || !browserFrame.isShowing()) {
            return;
        }
        BufferedImage img = captureBrowserFrame();
        Canvas canvas = Canvas.image(img);
        browserDisplay.text(CanvasRenderer.optimized().render(canvas).component());
        Menu.MenuHelper.send(viewer, browserDisplay);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastOutputTime > 1000) {
            lastOutputTime = currentTime;
            try {
                File outFile = new File("plugins/Sunscreen/browser-capture.png");
                ImageIO.write(img, "png", outFile);
                log.info("Browser capture saved to " + outFile.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private BufferedImage captureBrowserFrame() {
        final BufferedImage[] imageHolder = new BufferedImage[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                cefBrowser.getUIComponent().validate();
                int width = cefBrowser.getUIComponent().getWidth();
                int height = cefBrowser.getUIComponent().getHeight();
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                cefBrowser.getUIComponent().paint(g);
                g.dispose();
                imageHolder[0] = image;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imageHolder[0];
    }
    @Override
    public void handleRot(float yaw, float pitch) {
        Vec2d delta = Vec2d.of(yaw, -pitch).div(10);
        Vec2d current = Vec2d.of(cursorDisplay.transformation().translation().x(), cursorDisplay.transformation().translation().y());
        Vec2d newPos = current.add(delta);
        newPos = Vec2d.of(clamp(newPos.x(), 0, 800), clamp(newPos.y(), 0, 600));
        Display.Transformation trans = Display.Transformation.transformation().translation(Vector3d.vec3(newPos.x(), newPos.y(), -0.24999))
                .scale(Vector3d.vec3(1/24.0, 1/24.0, 1/24.0));
        cursorDisplay.transformation(trans);
        Menu.MenuHelper.send(viewer, cursorDisplay);
        forwardMouseMove((int)newPos.x(), (int)newPos.y());
    }
    @Override
    public void handleSneak() {
        leave();
    }
    @Override
    public void handleScroll(int slot) {}
    @Override
    public void handleDamage() {}
    private void forwardMouseMove(int x, int y) {
        if (!cefReady || browserFrame == null || !browserFrame.isShowing()) return;
        java.awt.Component comp = cefBrowser.getUIComponent();
        MouseEvent me = new MouseEvent(comp, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false);
        comp.dispatchEvent(me);
    }
    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
    private void leave() {
        running = false;
        cefBrowser.close(true);
        cefClient.dispose();
        cefApp.dispose();
        if (browserFrame != null) {
            browserFrame.dispose();
        }
        viewer.connection().send(new WrapperPlayServerChangeGameState(WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE, viewer.gameMode()));
        viewer.connection().send(new WrapperPlayServerSetPassengers(cursorDisplay.id().intValue(), new int[]{}));
        viewer.connection().send(new WrapperPlayServerCamera(viewer.entityId()));
        viewer.connection().send(new WrapperPlayServerDestroyEntities(cursorDisplay.id().intValue(), browserDisplay.id().intValue()));
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
    @Override
    public boolean close() {
        leave();
        return true;
    }
}
