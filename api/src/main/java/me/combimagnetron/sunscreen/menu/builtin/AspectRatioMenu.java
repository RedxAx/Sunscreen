package me.combimagnetron.sunscreen.menu.builtin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import me.combimagnetron.passport.internal.entity.impl.display.Display;
import me.combimagnetron.passport.internal.entity.impl.display.TextDisplay;
import me.combimagnetron.passport.internal.entity.metadata.type.Vector3d;
import me.combimagnetron.passport.user.User;
import me.combimagnetron.sunscreen.SunscreenLibrary;
import me.combimagnetron.sunscreen.image.Canvas;
import me.combimagnetron.sunscreen.image.CanvasRenderer;
import me.combimagnetron.sunscreen.image.Color;
import me.combimagnetron.sunscreen.image.Pixel;
import me.combimagnetron.sunscreen.menu.Menu;
import me.combimagnetron.sunscreen.menu.ScreenSize;
import me.combimagnetron.sunscreen.menu.builtin.editor.EditorMenu;
import me.combimagnetron.sunscreen.menu.draft.Draft;
import me.combimagnetron.sunscreen.menu.element.Element;
import me.combimagnetron.sunscreen.menu.element.Interactable;
import me.combimagnetron.sunscreen.menu.element.Position;
import me.combimagnetron.sunscreen.menu.element.div.Div;
import me.combimagnetron.sunscreen.menu.element.div.Edit;
import me.combimagnetron.sunscreen.menu.element.impl.ButtonElement;
import me.combimagnetron.sunscreen.menu.element.impl.ImageElement;
import me.combimagnetron.sunscreen.menu.element.impl.SelectorElement;
import me.combimagnetron.sunscreen.menu.element.impl.TextElement;
import me.combimagnetron.sunscreen.style.Style;
import me.combimagnetron.sunscreen.style.Text;
import me.combimagnetron.sunscreen.user.SunscreenUser;
import me.combimagnetron.sunscreen.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class AspectRatioMenu implements Menu {
    private final static double PixelFactor = 0.0010;
    private final Canvas spriteSheet = Canvas.image(Canvas.ImageProvider.url("https://i.imgur.com/6tJNlmq.png"));
    private final SunscreenUser<?> viewer;
    private final HashMap<Identifier, Div> divHashMap = new HashMap<>();
    private final HashMap<Identifier, TextDisplay> divEntityIdHashMap = new HashMap<>();
    private final TextDisplay cursorDisplay = TextDisplay.textDisplay(Vector3d.vec3(0));
    private final TextDisplay instructionDisplay = TextDisplay.textDisplay(Vector3d.vec3(0));
    private final TextDisplay temp = TextDisplay.textDisplay(Vector3d.vec3(0));
    private boolean cursorLocked = false;
    private boolean hover = false;
    private int stage = 0;
    private int gameMode = 0;
    private Vec2d lastInput = Vec2d.of(0, 0);
    private PacketListenerCommon listener;

    public AspectRatioMenu(SunscreenUser<?> viewer) {
        this.viewer = viewer;
        this.gameMode = viewer.gameMode();
        build();
        hideCursor();
        open(viewer);
    }

    private void build() {
        Div upperLeft = Div.nonRender(Identifier.of("upper_left"))
                .add(ImageElement.imageElement(Canvas.image(Canvas.ImageProvider.url("https://i.imgur.com/79sGWjB.png")), Identifier.of("arrow"), Position.pixel(0,0)));
        div(upperLeft);
        Div upperRight = Div.nonRender(Identifier.of("upper_right"))
                .add(ImageElement.imageElement(Canvas.image(Canvas.ImageProvider.url("https://i.imgur.com/79sGWjB.png")), Identifier.of("arrow"), Position.pixel(0,0)));
        div(upperRight);
        Div lowerLeft = Div.nonRender(Identifier.of("lower_left"))
                .add(ImageElement.imageElement(Canvas.image(Canvas.ImageProvider.url("https://i.imgur.com/79sGWjB.png")), Identifier.of("arrow"), Position.pixel(0,0)));
        div(lowerLeft);
        Div lowerRight = Div.nonRender(Identifier.of("lower_right"))
                .add(ImageElement.imageElement(Canvas.image(Canvas.ImageProvider.url("https://i.imgur.com/79sGWjB.png")), Identifier.of("arrow"), Position.pixel(0,0)));
        div(lowerRight);
        Div center = Div.div(Identifier.of("center")).size(Vec2d.of(114, 140))
                .add(ImageElement.imageElement(spriteSheet.sub(Vec2d.of(114, 140), Vec2d.of(594, 0)), Identifier.of("center", "bg"), Position.pixel(0, 0)))
                .add(ImageElement.imageElement(spriteSheet.sub(Vec2d.of(101, 12), Vec2d.of(115, 194)), Identifier.of("center", "button"), Position.pixel(3, 125)))
                .add(TextElement.textElement(Identifier.of("center", "button_label"), Position.pixel(13, 127), Text.text("Sneak to continue", Text.Font.vanilla()))
                        .style(Style.color(), Color.of(155, 171, 178)))
                .add(TextElement.textElement(Identifier.of("center", "label"), Position.pixel(3, 3), Text.text("Move the arrows to\nthe corner of your\nscreen by moving\nyour mouse.", Text.Font.vanilla()))
                        .style(Style.color(), Color.of(155, 171, 178)));
        div(center);
    }

    private void hideCursor() {
        cursorDisplay.text(Component.empty());
        List<EntityData> entityData = cursorDisplay.type().metadata().entityData();
        WrapperPlayServerEntityMetadata clientEntityMetadata = new WrapperPlayServerEntityMetadata(cursorDisplay.id().intValue(), entityData);
        viewer.connection().send(clientEntityMetadata);
    }

    private void showCursor() {
        cursorDisplay.text(Component.text("e").font(Key.key("comet:arrow")));
        cursorDisplay.backgroundColor(0);
        cursorDisplay.billboard(Display.Billboard.CENTER);
        Display.Transformation transformation = Display.Transformation.transformation().translation(Vector3d.vec3(0, 0, -0.24999)).scale(Vector3d.vec3((double) 1/24, (double) 1/24, (double) 1/24));
        cursorDisplay.transformation(transformation);
        viewer.show(cursorDisplay);
    }

    private void leave() {
        viewer.connection().send(new WrapperPlayServerChangeGameState(WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE, gameMode));
        viewer.connection().send(new WrapperPlayServerCamera(viewer.entityId()));
        viewer.connection().send(new WrapperPlayServerSetPassengers(cursorDisplay.id().intValue(), new int[]{}));
        viewer.connection().send(new WrapperPlayServerDestroyEntities(cursorDisplay.id().intValue(), temp.id().intValue()));
        divEntityIdHashMap.forEach((div, display) -> viewer.connection().send(new WrapperPlayServerDestroyEntities(display.id().intValue())));
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }

    private void initListener() {
        this.listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
                    WrapperPlayClientPlayerRotation packet = new WrapperPlayClientPlayerRotation(event);
                    float yaw = packet.getYaw();
                    float pitch = -packet.getPitch();
                    lastInput = Vec2d.of(yaw, pitch).sub(lastInput).div(500);
                    move();
                } else if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
                    WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event);
                    if (packet.getAction() == WrapperPlayClientEntityAction.Action.STOP_SNEAKING) {
                        Vector3d begin = divEntityIdHashMap.get(Identifier.of("lower_left")).transformation().translation();
                        Vector3d end = divEntityIdHashMap.get(Identifier.of("upper_right")).transformation().translation();
                        begin = begin.add(Vector3d.vec3(-9 * PixelFactor, 12.75 * PixelFactor, 0));
                        end = end.add(Vector3d.vec3(9 * PixelFactor, -12.75 * PixelFactor, 0));
                        Vec2d screenSize = Vec2d.of((end.x() - begin.x())/PixelFactor, (end.y() - begin.y())/PixelFactor);
                        viewer.screenSize(ScreenSize.of(screenSize, Pair.of(Vec2d.of(begin.x(), begin.y()), Vec2d.of(end.x(), end.y()))));
                        leave();
                        //new SetupMenu(viewer);
                        //new EditorMenu(viewer);
                        new BrowserScreen(viewer);
                    }

                }
            }
        }, PacketListenerPriority.LOWEST);
    }

    private static void send(User<?> viewer, TextDisplay textDisplay) {
        List<EntityData> entityData = textDisplay.type().metadata().entityData();
        WrapperPlayServerEntityMetadata clientEntityMetadata = new WrapperPlayServerEntityMetadata(textDisplay.id().intValue(), entityData);
        viewer.connection().send(clientEntityMetadata);
    }

    private boolean isHovered(Vec2d position, Vec2d size) {
        Vector3d translation = cursorDisplay.transformation().translation();
        Vec2d cursor = Vec2d.of(translation.x(), translation.y());
        return cursor.x() > position.x() && cursor.x() < position.x() + size.x() && cursor.y() > position.y() && cursor.y() < position.y() + size.y();
    }

    private void move() {
        Vec2d translation = lastInput.mul(1);
        if (!cursorLocked) {
            Display.Transformation transformation = Display.Transformation.transformation().translation(Vector3d.vec3(translation.x(), translation.y(), -0.24999)).scale(Vector3d.vec3((double) 1/24, (double) 1/24, (double) 1/24));
            cursorDisplay.transformation(transformation);
            cursorDisplay.text(Component.text("e").font(Key.key("comet:arrow")));
            cursorDisplay.backgroundColor(0);
            send(viewer, cursorDisplay);
        }
        divEntityIdHashMap.forEach((identifier, display) -> {
            Map<Identifier, String> charMap = Map.of(Identifier.of("upper_left"), "1a",
                    Identifier.of("upper_right"), "2b",
                    Identifier.of("lower_left"), "1c",
                    Identifier.of("lower_right"), "2d");
            //display.text(CanvasRenderer.optimized().render(canvas).component().append(Component.text("a")));
            if (!charMap.containsKey(identifier)) return;
            display.brightness(15, 15);
            display.onFire(true);
            display.text(Component.text(charMap.get(identifier)).font(Key.key("comet:arrow")));
            double xMov = translation.x();
            double yMov = translation.y();
            if (identifier.string().contains("right")) {
                xMov = -xMov;
            }
            if (identifier.string().contains("lower")) {
                yMov = -yMov;
            }
            Display.Transformation transformation = Display.Transformation.transformation().translation(Vector3d.vec3(xMov, yMov, -0.24999)).scale(Vector3d.vec3((double) 1/24, (double) 1/24, (double) 1/24));
            display.transformation(transformation);
            display.backgroundColor(0);
            send(viewer, display);
        });
    }

    @Override
    public void open(SunscreenUser<?> user) {
        user.connection().send(new WrapperPlayServerPlayerRotation(0, -180));
        initListener();
        TextDisplay camera = TextDisplay.textDisplay(Vector3d.vec3(user.position().x(), user.position().y() + 1.6, user.position().z()));
        Vector3d rotation = Vector3d.vec3(user.rotation().y(), user.rotation().x(), user.rotation().z());
        camera.rotation(rotation);
        user.show(camera);
        if (SunscreenLibrary.library().config().forceShaderFov()) {
            user.fov(70);
        }
        user.connection().send(new WrapperPlayServerCamera(camera.id().intValue()));
        user.connection().send(new WrapperPlayServerChangeGameState(WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE, 3));
        for (Div div : divHashMap.values()) {
            divEntityIdHashMap.put(div.identifier(), spawn(div, user));
        }
        temp.backgroundColor(-16184812);
        temp.text(Component.text(" "));
        temp.brightness(15, 15);
        temp.billboard(Display.Billboard.CENTER);
        Display.Transformation tempTransformation = Display.Transformation.transformation().translation(Vector3d.vec3(0, -60, -0.27)).scale(Vector3d.vec3(500, 500, (double) 1/24));
        temp.transformation(tempTransformation);
        viewer.show(temp);
        List<Integer> entityIds = new ArrayList<>();
        entityIds.add(user.entityId());
        //entityIds.add(instructionDisplay.id().intValue());
        entityIds.add(temp.id().intValue());
        //showCursor();
        entityIds.add(cursorDisplay.id().intValue());
        Scheduler.async(() -> {
            entityIds.addAll(divEntityIdHashMap.values().stream().map(entity -> entity.id().intValue()).toList());
            user.connection().send(new WrapperPlayServerSetPassengers(camera.id().intValue(), ArrayUtils.toPrimitive(entityIds.toArray(new Integer[0]))));
            return null;
        });
    }

    private TextDisplay spawn(Div div, User<?> user) {
        System.out.println(div.identifier().string());
        TextDisplay display = TextDisplay.textDisplay(user.position());
        display.billboard(Display.Billboard.CENTER);
        double yOffset = div.canvas().size().mul(PixelFactor).mul(0.5).y();
        Display.Transformation transformation = Display.Transformation.transformation().translation(Vector3d.vec3(0, -yOffset, -0.25)).scale(Vector3d.vec3((double) 1/24, (double) 1/24, (double) 1/24));
        display.transformation(transformation);
        Component component = Component.text(" ");
        System.out.println(div.getClass().getName());
        if (!(div instanceof Div.NonRenderDiv)) {
            component = CanvasRenderer.optimized().render(div.render()).component();
        }
        display.onFire(true);
        display.backgroundColor(0);
        display.brightness(15, 15);
        display.text(component);
        display.lineWidth(200000);
        user.show(display);
        return display;
    }

    @Override
    public Menu apply(Draft draft) {
        Draft.Impl draftImpl = (Draft.Impl) draft;
        for (Edit<?> edit : draftImpl.edits()) {
            if (edit.type() == Div.class) {
                Edit<Div> divEdit = (Edit<Div>) edit;
                Div div = divHashMap.get(edit.identifier());
                for (Function<Div, Div> draftEdit : divEdit.edits()) {
                    div = draftEdit.apply(div);
                }
                divHashMap.put(edit.identifier(), div);
            } else if (edit.type() == Element.class) {
                Edit<Element> elementEdit = (Edit<Element>) edit;
                Element element = divHashMap.get(edit.identifier()).elements().stream().filter(e -> e.identifier().equals(edit.identifier())).findFirst().orElse(null);
                for (Function<Element, Element> draftEdit : elementEdit.edits()) {
                    element = draftEdit.apply(element);
                }
                Div div = divHashMap.get(edit.identifier());
                div.remove(edit.identifier());
                div.add(element);
                divHashMap.put(edit.identifier(), div);
            }
        }
        return this;
    }

    @Override
    public Menu div(Div div) {
        divHashMap.put(div.identifier(), div);
        return this;
    }

    @Override
    public boolean close() {
        return false;
    }

}
