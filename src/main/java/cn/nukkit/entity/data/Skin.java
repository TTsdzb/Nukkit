package cn.nukkit.entity.data;

import cn.nukkit.Server;
import cn.nukkit.nbt.stream.FastByteArrayOutputStream;
import cn.nukkit.utils.*;
import com.google.common.base.Preconditions;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jose.shaded.json.JSONValue;
import lombok.ToString;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class Skin {

    private static final int PIXEL_SIZE = 4;

    public static final int SINGLE_SKIN_SIZE = 64 * 32 * PIXEL_SIZE;
    public static final int DOUBLE_SKIN_SIZE = 64 * 64 * PIXEL_SIZE;
    public static final int SKIN_128_64_SIZE = 128 * 64 * PIXEL_SIZE;
    public static final int SKIN_128_128_SIZE = 128 * 128 * PIXEL_SIZE;
    
    private static final int MAX_DATA_SIZE = 262144;

    public static final String GEOMETRY_CUSTOM = convertLegacyGeometryName("geometry.humanoid.custom");
    public static final String GEOMETRY_CUSTOM_SLIM = convertLegacyGeometryName("geometry.humanoid.customSlim");

    private boolean noPlayFab; // Don't attempt to generate missing play fab id multiple times
    private String fullSkinId;
    private String skinId;
    private String playFabId = "";
    private String skinResourcePatch = GEOMETRY_CUSTOM;
    private SerializedImage skinData;
    private final List<SkinAnimation> animations = new ArrayList<>();
    private final List<PersonaPiece> personaPieces = new ArrayList<>();
    private final List<PersonaPieceTint> tintColors = new ArrayList<>();
    private SerializedImage capeData;
    private String geometryData;
    private String animationData;
    private boolean premium;
    private boolean persona;
    private boolean capeOnClassic;
    private boolean primaryUser = true;
    private String capeId;
    private String skinColor = "#0";
    private String armSize = "wide";
    private boolean trusted = true;
    private String geometryDataEngineVersion = "0.0.0";
    private boolean overridingPlayerAppearance = true;

    public boolean isValid() {
        return isValidSkin() && isValidResourcePatch();
    }

    private boolean isValidSkin() {
        try {
            return (skinId != null && !skinId.trim().isEmpty() && skinId.length() < 100) &&
                    (skinData != null && skinData.width >= 64 && skinData.height >= 32 && skinData.data.length >= SINGLE_SKIN_SIZE) &&
                    (geometryData != null && !geometryData.isEmpty()) &&
                    ((geometryData.getBytes(StandardCharsets.UTF_8).length <= MAX_DATA_SIZE &&
                            skinData.data.length <= MAX_DATA_SIZE &&
                            (capeData == null || capeData.data.length <= MAX_DATA_SIZE) &&
                            (animationData == null || animationData.getBytes(StandardCharsets.UTF_8).length <= MAX_DATA_SIZE))) &&
                    (playFabId == null || playFabId.length() < 100) &&
                    (capeId == null || capeId.length() < 100) &&
                    (skinColor == null || skinColor.length() < 100) &&
                    (armSize == null || armSize.length() < 100) &&
                    (fullSkinId == null || fullSkinId.length() < 200) &&
                    (geometryDataEngineVersion == null || geometryDataEngineVersion.length() < 100);
        } catch (Exception ex) {
            Server.getInstance().getLogger().logException(ex);
            return false;
        }
    }

    private boolean isValidResourcePatch() {
        if (skinResourcePatch == null || skinResourcePatch.length() > 1000) {
            return false;
        }
        try {
            JSONObject geometry = (JSONObject) ((JSONObject) JSONValue.parse(skinResourcePatch)).get("geometry");
            return geometry.containsKey("default") && geometry.get("default") instanceof String;
        } catch (ClassCastException | NullPointerException e) {
            return false;
        }
    }

    public SerializedImage getSkinData() {
        if (skinData == null) {
            return SerializedImage.EMPTY;
        }
        return skinData;
    }

    public String getSkinId() {
        if (this.skinId == null) {
            Server.getInstance().getLogger().debug("Missing skin ID, generating new");
            this.generateSkinId("Custom");
        }
        return skinId;
    }

    public void setSkinId(String skinId) {
        if (skinId == null || skinId.trim().isEmpty()) {
            Server.getInstance().getLogger().debug("Skin ID cannot be empty! ", new Throwable(""));
            return;
        }
        this.skinId = skinId;
    }

    public void generateSkinId(String name) {
        byte[] data = Binary.appendBytes(getSkinData().data, getSkinResourcePatch().getBytes(StandardCharsets.UTF_8));
        this.skinId = UUID.nameUUIDFromBytes(data) + "." + name;
    }

    void dumpSkinData(RenderedImage skinImage) {
        try {
            LocalDateTime time = LocalDateTime.now();
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
            File dir = new File("dumped_skins");
            if (!dir.exists()) {
                dir.mkdir();
            }
            ImageIO.write(skinImage, "png", new File(dir, time.format(dateTimeFormatter) + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    BufferedImage imageFromData(int width, int height, byte[] data) {
        DataBuffer buffer = new DataBufferByte(data, data.length);
        WritableRaster raster = Raster.createInterleavedRaster(buffer, width, height, 4 * width, 4, new int[]{0, 1, 2, 3}, null);
        ColorModel cm = new ComponentColorModel(ColorModel.getRGBdefault().getColorSpace(), true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
        return new BufferedImage(cm, raster, true, null);
    }

    public void setSkinData(byte[] skinData) {
        int width;
        int height;
        switch (skinData.length) {
            case SINGLE_SKIN_SIZE:
                width = 64;
                height = 32;
                break;
            case DOUBLE_SKIN_SIZE:
                width = 64;
                height = 64;
                break;
            case SKIN_128_64_SIZE:
                width = 128;
                height = 64;
                break;
            case SKIN_128_128_SIZE:
                width = 128;
                height = 128;
                break;
            default:
                throw new IllegalArgumentException("Invalid skin");
        }
        BufferedImage image = imageFromData(width, height, skinData);
        dumpSkinData(image);
        Server.getInstance().getLogger().info("Saved a player's skin from byte array.");

        setSkinData(SerializedImage.fromLegacy(skinData));
    }

    public void setSkinData(BufferedImage image) {
        dumpSkinData(image);
        Server.getInstance().getLogger().info("Saved a player's skin from buffered image.");

        setSkinData(parseBufferedImage(image));
    }

    public void setSkinData(SerializedImage skinData) {
        Objects.requireNonNull(skinData, "skinData");

        BufferedImage image = imageFromData(skinData.width, skinData.height, skinData.data);
        dumpSkinData(image);
        Server.getInstance().getLogger().info("Saved a player's skin from serialized image.");

        this.skinData = skinData;
    }

    public void setSkinResourcePatch(String skinResourcePatch) {
        if (skinResourcePatch == null || skinResourcePatch.trim().isEmpty()) {
            this.skinResourcePatch = GEOMETRY_CUSTOM;
            return;
        }
        this.skinResourcePatch = skinResourcePatch;
    }

    public void setGeometryName(String geometryName) {
        if (geometryName.trim().isEmpty()) {
            this.skinResourcePatch = GEOMETRY_CUSTOM;
            return;
        }

        this.skinResourcePatch = "{\"geometry\" : {\"default\" : \"" + geometryName + "\"}}";
    }

    public String getSkinResourcePatch() {
        if (this.skinResourcePatch == null) {
            return "";
        }
        return skinResourcePatch;
    }

    public SerializedImage getCapeData() {
        if (capeData == null) {
            return SerializedImage.EMPTY;
        }
        return capeData;
    }

    public String getCapeId() {
        if (capeId == null) {
            return "";
        }
        return capeId;
    }

    public void setCapeId(String capeId) {
        if (capeId == null || capeId.trim().isEmpty()) {
            capeId = null;
        }
        this.capeId = capeId;
    }

    public void setCapeData(byte[] capeData) {
        Objects.requireNonNull(capeData, "capeData");
        if (capeData.length == SINGLE_SKIN_SIZE) {
            setCapeData(new SerializedImage(64, 32, capeData));
        }
    }

    public void setCapeData(BufferedImage image) {
        setCapeData(parseBufferedImage(image));
    }

    public void setCapeData(SerializedImage capeData) {
        Objects.requireNonNull(capeData, "capeData");
        this.capeData = capeData;
    }

    public String getGeometryData() {
        if (geometryData == null) {
            return "";
        }
        return geometryData;
    }

    public void setGeometryData(String geometryData) {
        Preconditions.checkNotNull(geometryData, "geometryData");
        if (!geometryData.equals(this.geometryData)) {
            this.geometryData = geometryData;
        }
    }

    public String getAnimationData() {
        if (animationData == null) {
            return "";
        }
        return animationData;
    }

    public void setAnimationData(String animationData) {
        Preconditions.checkNotNull(animationData, "animationData");
        if (!animationData.equals(this.animationData)) {
            this.animationData = animationData;
        }
    }

    public List<SkinAnimation> getAnimations() {
        return animations;
    }

    public List<PersonaPiece> getPersonaPieces() {
        return personaPieces;
    }

    public List<PersonaPieceTint> getTintColors() {
        return tintColors;
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public boolean isPersona() {
        return persona;
    }

    public void setPersona(boolean persona) {
        this.persona = persona;
    }

    public boolean isCapeOnClassic() {
        return capeOnClassic;
    }

    public void setCapeOnClassic(boolean capeOnClassic) {
        this.capeOnClassic = capeOnClassic;
    }

    public void setPrimaryUser(boolean primaryUser) {
        this.primaryUser = primaryUser;
    }

    public boolean isPrimaryUser() {
        return primaryUser;
    }

    public void setGeometryDataEngineVersion(String geometryDataEngineVersion) {
        this.geometryDataEngineVersion = geometryDataEngineVersion;
    }

    public String getGeometryDataEngineVersion() {
        return geometryDataEngineVersion;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    public String getSkinColor() {
        return skinColor;
    }

    public void setSkinColor(String skinColor) {
        this.skinColor = skinColor;
    }

    public String getArmSize() {
        return armSize;
    }

    public void setArmSize(String armSize) {
        this.armSize = armSize;
    }

    public void setFullSkinId(String fullSkinId) {
        this.fullSkinId = fullSkinId;
        this.noPlayFab = false; // Allow another attempt to generate it using the new id
    }

    public String getFullSkinId() {
        if (this.fullSkinId == null) {
            this.fullSkinId = this.getSkinId() + this.getCapeId();
            this.noPlayFab = false; // Allow another attempt to generate it using the new id
        }
        return this.fullSkinId;
    }

    public void setPlayFabId(String playFabId) {
        this.playFabId = playFabId;
        this.noPlayFab = false;
    }

    public String getPlayFabId() {
        if (this.noPlayFab) {
            return "";
        }
        if ((this.playFabId == null || this.playFabId.isEmpty())) {
            String[] split = this.getFullSkinId().split("-", 6);
            if (split.length > 5) {
                this.playFabId = split[5];
                this.noPlayFab = false;
            } else {
                try {
                    this.playFabId = this.getFullSkinId().replace("-", "").substring(16);
                    this.noPlayFab = false;
                } catch (Exception ignore) {
                    Server.getInstance().getLogger().debug("Couldn't generate Skin playFabId for " + this.getFullSkinId());
                    this.playFabId = "";
                    this.noPlayFab = true;
                }
            }
        }
        return this.playFabId;
    }

    private static SerializedImage parseBufferedImage(BufferedImage image) {
        FastByteArrayOutputStream outputStream = ThreadCache.fbaos.get().reset();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                outputStream.write(color.getRed());
                outputStream.write(color.getGreen());
                outputStream.write(color.getBlue());
                outputStream.write(color.getAlpha());
            }
        }
        image.flush();
        return new SerializedImage(image.getWidth(), image.getHeight(), outputStream.toByteArray());
    }

    private static String convertLegacyGeometryName(String geometryName) {
        return "{\"geometry\" : {\"default\" : \"" + geometryName + "\"}}";
    }

    public void setOverridingPlayerAppearance(boolean overridingPlayerAppearance) {
        this.overridingPlayerAppearance = overridingPlayerAppearance;
    }

    public boolean isOverridingPlayerAppearance() {
        return this.overridingPlayerAppearance;
    }
}
