package me.combimagnetron.sunscreen.menu;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import me.combimagnetron.passport.internal.entity.Entity;
import me.combimagnetron.passport.internal.entity.impl.display.Display;
import me.combimagnetron.passport.internal.entity.impl.display.TextDisplay;
import me.combimagnetron.passport.internal.entity.metadata.type.Vector3d;
import me.combimagnetron.sunscreen.SunscreenLibrary;
import me.combimagnetron.sunscreen.image.Canvas;
import me.combimagnetron.sunscreen.image.CanvasRenderer;
import me.combimagnetron.sunscreen.image.Color;
import me.combimagnetron.sunscreen.menu.draft.Draft;
import me.combimagnetron.sunscreen.menu.element.Element;
import me.combimagnetron.sunscreen.menu.element.div.Div;
import me.combimagnetron.sunscreen.menu.element.div.Edit;
import me.combimagnetron.sunscreen.menu.element.div.ScrollableDiv;
import me.combimagnetron.sunscreen.menu.timing.Tick;
import me.combimagnetron.sunscreen.menu.timing.Tickable;
import me.combimagnetron.sunscreen.session.Session;
import me.combimagnetron.sunscreen.user.SunscreenUser;
import me.combimagnetron.sunscreen.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface Menu {

    Menu apply(Draft draft);

    void open(SunscreenUser<?> user);

    Menu div(Div div);

    boolean close();

    abstract class Base implements Menu {
        protected final HashMap<Identifier, Div> divHashMap = new HashMap<>();

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

        public abstract void handleRot(float yaw, float pitch);

        public abstract void handleSneak();

        public abstract void handleScroll(int slot);

        public abstract void handleDamage();

        @Override
        public Menu div(Div div) {
            divHashMap.put(div.identifier(), div);
            return this;
        }

    }

    abstract class FloatImpl extends Base implements Tickable {
        private final SunscreenUser<?> viewer;
        private final HashMap<Identifier, TextDisplay> divEntityIdHashMap = new HashMap<>();
        protected final TextDisplay cursorDisplay = TextDisplay.textDisplay(Vector3d.vec3(0));
        private final TextDisplay background = TextDisplay.textDisplay(Vector3d.vec3(0));
        private Color backgroundColor = Color.of(0, 0, 0, 0);
        private Vec2d lastInput = Vec2d.of(0, 0);
        private int damageTick = 0;

        public FloatImpl(SunscreenUser<?> viewer) {
            this.viewer = viewer;
            SunscreenLibrary.library().sessionHandler().session(Session.session(this, viewer));
        }

        /**
         * Tick method for the menu, called every tick.
         * @param tick Tick to calculate generation time.
         */
        @Override
        public void tick(Tick tick) {
            if (damageTick > 0) {
                damageTick--;
                background.backgroundColor(Color.of(backgroundColor.red(), backgroundColor.green(), backgroundColor.blue(), 123).rgb());
                MenuHelper.send(viewer, background);
            } else {
                background(backgroundColor);
            }
        }

        protected void hideCursor() {
            cursorDisplay.text(Component.empty());
            List<EntityData> entityData = cursorDisplay.type().metadata().entityData();
            WrapperPlayServerEntityMetadata clientEntityMetadata = new WrapperPlayServerEntityMetadata(cursorDisplay.id().intValue(), entityData);
            viewer.connection().send(clientEntityMetadata);
        }

        protected void showCursor() {
            cursorDisplay.text(Component.text("e").font(Key.key("comet:arrow")));
            cursorDisplay.backgroundColor(0);
            cursorDisplay.billboard(Display.Billboard.CENTER);
            cursorDisplay.brightness(15, 15);
            Display.Transformation transformation = Display.Transformation.transformation().translation(Vector3d.vec3(0, 0, -0.24999)).scale(Vector3d.vec3((double) 1/24, (double) 1/24, (double) 1/24));
            cursorDisplay.transformation(transformation);
            viewer.show(cursorDisplay);
        }

        @Override
        public void handleRot(float yaw, float pitch) {
            lastInput = Vec2d.of(yaw, -pitch).sub(lastInput).div(500);
            move();
        }

        @Override
        public void handleSneak() {
            leave();
        }

        @Override
        public void handleScroll(int slot) {
            for (Map.Entry<Identifier, TextDisplay> entry : divEntityIdHashMap.entrySet()) {
                Div div = divHashMap.get(entry.getKey());
                if (div instanceof ScrollableDiv scrollableDiv) {
                    scrollableDiv.scroll(slot);
                    entry.getValue().text(CanvasRenderer.optimized().render(div.render()).component());
                    MenuHelper.send(viewer, entry.getValue());
                }
            }
        }

        @Override
        public void handleDamage() {
            damageTick += 20;
        }

        /**
         * Takes Color argument and sets the background accordingly, defaults to black.
         * @param color Color to set the background to, can be transparent.
         */
        public void background(Color color) {
            background.backgroundColor(color.rgb());
            background.text(Component.text(" "));
            this.backgroundColor = color;
            MenuHelper.send(viewer, background);
        }

        private void move() {
            Vec2d translation = lastInput.mul(1);
            translation = translation.add(0.003, -0.010);
            viewer.message(Component.text("a"));
            Display.Transformation transformation = Display.Transformation.transformation().translation(Vector3d.vec3(translation.x(), translation.y(), -0.24999)).scale(Vector3d.vec3((double) 1/24, (double) 1/24, (double) 1/24));
            cursorDisplay.transformation(transformation);
            MenuHelper.send(viewer, cursorDisplay);
            for (Map.Entry<Identifier, TextDisplay> entry: divEntityIdHashMap.entrySet()) {
                Div div = divHashMap.get(entry.getKey());
                if (div instanceof Div.NonRenderDiv) continue;
                Vec2d divPos = Vec2d.of(div.position().x().pixel(), div.position().y().pixel());
                Vector3d cursorTranslation = cursorDisplay.transformation().translation();
                Vec2d cursorPos = ViewportHelper.toScreen(cursorTranslation, viewer.screenSize());
                cursorPos = cursorPos.add(0, 10);
                if (HoverHelper.isHovered(cursorTranslation, viewer, divPos, div.size())) {
                    //System.out.println("Hovered " + cursorPos + " " + cursorTranslation + " " + viewer.screenSize());
                    ((Div.Impl)div).handleHover(cursorPos.sub(divPos));
                    entry.getValue().text(CanvasRenderer.optimized().render(div.render()).component());
                    MenuHelper.send(viewer, entry.getValue());
                }
            }
        }

        /**
         * Closes the menu and sends the necessary packets to the viewer.
         * @return True if the menu was closed successfully.
         */
        @Override
        public boolean close() {
            leave();
            return true;
        }

        private void leave() {
            viewer.connection().send(new WrapperPlayServerChangeGameState(WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE, viewer.gameMode()));
            viewer.connection().send(new WrapperPlayServerSetPassengers(cursorDisplay.id().intValue(), new int[]{}));
            viewer.connection().send(new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(new UserProfile(viewer.uniqueIdentifier(), viewer.name()), true, 0, GameMode.getById(viewer.gameMode()), Component.empty(), null)));
            viewer.connection().send(new WrapperPlayServerCamera(viewer.entityId()));
            viewer.connection().send(new WrapperPlayServerDestroyEntities(cursorDisplay.id().intValue(), background.id().intValue()));
            divEntityIdHashMap.forEach((div, display) -> viewer.connection().send(new WrapperPlayServerDestroyEntities(display.id().intValue())));
        }

        /**
         * Opens the menu and sends the necessary packets to the viewer.
         * @param user User to open the menu for.
         */
        @Override
        public void open(SunscreenUser<?> user) {
            user.connection().send(new WrapperPlayServerPlayerRotation(0, -180));
            TextDisplay camera = TextDisplay.textDisplay(Vector3d.vec3(user.position().x(), user.position().y() + 1.6, user.position().z()));
            Vector3d rotation = Vector3d.vec3(user.rotation().y(), user.rotation().x(), user.rotation().z());
            camera.rotation(rotation);
            user.show(camera);
            if (SunscreenLibrary.library().config().forceShaderFov()) {
                user.fov(70);
            }
            user.connection().send(new WrapperPlayServerCamera(camera.id().intValue()));
            user.connection().send(new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(new UserProfile(user.uniqueIdentifier(), user.name()), true, 0, GameMode.SPECTATOR, Component.empty(), null)));
            user.connection().send(new WrapperPlayServerChangeGameState(WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE, 3));
            for (Div div : divHashMap.values()) {
                divEntityIdHashMap.put(div.identifier(), spawn(div, user));
            }
            List<Integer> entityIds = new ArrayList<>();
            entityIds.add(user.entityId());
            showCursor();
            entityIds.add(cursorDisplay.id().intValue());
            background.billboard(Display.Billboard.CENTER);
            Display.Transformation tempTransformation = Display.Transformation.transformation().translation(Vector3d.vec3(0, -60, -0.27)).scale(Vector3d.vec3(500, 500, (double) 1/24));
            background.text(Component.text(" "));
            background.transformation(tempTransformation);
            background.brightness(15, 15);
            background.backgroundColor(backgroundColor.rgb());
            user.show(background);
            entityIds.add(background.id().intValue());
            user.show(cursorDisplay);
            Scheduler.async(() -> {
                entityIds.addAll(divEntityIdHashMap.values().stream().map(TextDisplay::id).map(Entity.EntityId::intValue).toList());
                user.connection().send(new WrapperPlayServerSetPassengers(camera.id().intValue(), ArrayUtils.toPrimitive(entityIds.toArray(new Integer[0]))));
                return null;
            });
        }

        private TextDisplay spawn(Div div, SunscreenUser<?> user) {
            TextDisplay display = TextDisplay.textDisplay(user.position());
            display.billboard(Display.Billboard.CENTER);
            Vector3d scale = div.scale();
            System.out.println(viewer.screenSize());
            Vector3d translation = ViewportHelper.toTranslation(Vec2d.of(div.position().x().pixel() + div.size().x() * 0.5, div.position().y().pixel() + div.size().y()), viewer.screenSize());
            Display.Transformation transformation = Display.Transformation.transformation().translation(Vector3d.vec3(translation.x(), translation.y(), -0.25)).scale(Vector3d.vec3((double) 1/(24/scale.x()), (double) 1/(24/scale.y()), (double) 1/(24/scale.z())));
            display.transformation(transformation);
            Component component = CanvasRenderer.optimized().render(div.render()).component();
            display.brightness(15, 15);
            display.backgroundColor(0x00000000);
            display.text(component);
            display.lineWidth(200000);
            user.show(display);
            return display;
        }

    }

    class Float extends FloatImpl {
        public Float(SunscreenUser<?> viewer) {
            super(viewer);
        }
    }

    class Single extends SingleImpl {
        public Single(SunscreenUser<?> viewer) {
            super(viewer);
        }

        @Override
        public void open(SunscreenUser<?> user) {

        }

        @Override
        public boolean close() {
            return false;
        }
    }

    abstract class SingleImpl extends Base implements Tickable {
        private final SunscreenUser<?> viewer;
        private final TextDisplay cursorDisplay = TextDisplay.textDisplay(Vector3d.vec3(0));
        private Vec2d lastInput = Vec2d.of(0, 0);

        public SingleImpl(SunscreenUser<?> viewer) {
            this.viewer = viewer;
        }

        @Override
        public void handleRot(float yaw, float pitch) {
            lastInput = Vec2d.of(yaw, -pitch).sub(lastInput).div(500);
            //move();
        }

        @Override
        public void handleSneak() {
            //leave();
        }

        @Override
        public void handleDamage() {
            //damageTick += 20;
        }

        @Override
        public void handleScroll(int slot) {
            //for (Map.Entry<Identifier, TextDisplay> entry : divEntityIdHashMap.entrySet()) {
            //    Div div = divHashMap.get(entry.getKey());
            //    if (div instanceof ScrollableDiv scrollableDiv) {
            //        scrollableDiv.scroll(slot);
            //        entry.getValue().text(CanvasRenderer.optimized().render(div.render()).component());
            //        MenuHelper.send(viewer, entry.getValue());
            //    }
            //}
        }


        public Canvas render() {
            Canvas image = Canvas.image(Vec2d.of(512, 256));
            image.fill(Vec2d.of(0,0), Vec2d.of(512, 256), Color.of(9, 10, 20));
            for (Div div : divHashMap.values()) {
                image = div.render(image);
            }
            return image;
        }

        @Override
        public void tick(Tick tick) {

        }

    }

    class MenuHelper {

        public static void send(SunscreenUser<?> viewer, TextDisplay textDisplay) {
            List<EntityData> entityData = textDisplay.type().metadata().entityData();
            WrapperPlayServerEntityMetadata clientEntityMetadata = new WrapperPlayServerEntityMetadata(textDisplay.id().intValue(), entityData);
            viewer.connection().send(clientEntityMetadata);
        }

    }

}
