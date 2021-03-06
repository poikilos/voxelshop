package com.vitco.app.core.world.container;

import com.vitco.app.core.data.container.Voxel;
import com.vitco.app.core.world.WorldManager;
import com.vitco.app.low.hull.HullManager;
import com.vitco.app.util.graphic.GraphicTools;
import com.vitco.app.util.graphic.SharedImageFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Used to generate a texture.
 */
public class TextureObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int minx;
    private final int miny;
    private final Collection<Voxel> faceList;
    private final Integer orientation;
    private final Integer axis;
    private final HullManager<Voxel> hullManager;
    private final int w;
    private final int h;
    private final int textureSizeX;
    private final int textureSizeY;
    private int textureId;
    private String textureName = null;

    // counts how often the textures are used
    private static final HashMap<String, Integer> textureCount = new HashMap<String, Integer>();

    // constructor
    public TextureObject(int minx, int miny, Collection<Voxel> faceList,
                         Integer orientation, Integer axis, HullManager<Voxel> hullManager,
                         int w, int h, int textureSizeX, int textureSizeY) {
        this.minx = minx;
        this.miny = miny;
        this.faceList = faceList;
        this.orientation = orientation;
        this.axis = axis;
        this.hullManager = hullManager;
        this.w = w;
        this.h = h;
        this.textureSizeX = textureSizeX;
        this.textureSizeY = textureSizeY;
    }

    public int getTextureId() {
        return textureId;
    }

    // helper to draw pixel interpolation where necessary (so black outline is only visible when appropriate)
    private void interpolatePixel(int[] pos2D, Voxel face, int axis, HullManager hullManager,
                                  int orientation, boolean containsTexture, Graphics2D g2,
                                  int x, int y, BufferedImage textureImage, int offsetx, int offsety) {
        int[] pos = VoxelManager.convert2D3D(pos2D[0] + offsetx, pos2D[1] + offsety, face.getPosAsInt()[axis], axis);
        short[] posS = new short[] {(short) pos[0], (short) pos[1], (short) pos[2]};
        if (hullManager.containsBorder(posS, orientation)) {
            if (containsTexture) {
                g2.setColor(face.getColor());
                g2.fillRect((x + 1 + offsetx)*32, (y + 1 + offsety)*32, 32, 32);
            } else {
                textureImage.setRGB(x + 1 + offsetx, y + 1 + offsety, face.getColor().getRGB());
            }
        }
    }

    // called when this instance is no longer needed
    public final void destroy() {
        setCurrentTextureUnused();
    }

    // sets the current texture unused
    private void setCurrentTextureUnused() {
        int count = textureCount.get(textureName);
        if (count == 1) {
            WorldManager.removeEfficientTexture(textureName);
            textureCount.remove(textureName);
        } else {
            textureCount.put(textureName, count - 1);
        }
    }

    // generates a texture
    // the seen points are stored in the seen hashmap
    public final void refreshTexture(HashSet<Point> seenTrianglePoints, BorderObject3D obj) {
        // create black image
        BufferedImage textureImage = SharedImageFactory.getBufferedImage(textureSizeX, textureSizeY);
        Graphics2D g2 = (Graphics2D) textureImage.getGraphics();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, textureSizeX, textureSizeY);

        boolean containsTexture = false;

        int s11 = 0, s21 = 0, s12 = 32, s22 = 32;

        // load colors into image, pixel by pixel
        for (Voxel face : faceList) {
            int[] pos2D = VoxelManager.convert3D2D(face, axis);
            int x = pos2D[0] - minx;
            int y = pos2D[1] - miny;
            int[] texture = face.getTexture();
            boolean isTexture = texture != null;
            if (!containsTexture && isTexture) {
                containsTexture = true; // this is now a texture that contains images (!)
                // resize image
                int textureSizeEnlargedX = textureSizeX * 32;
                int textureSizeEnlargedY = textureSizeY * 32;
                BufferedImage largeTextureImage = SharedImageFactory.getBufferedImage(textureSizeEnlargedX, textureSizeEnlargedY);
                largeTextureImage.getGraphics().drawImage(textureImage, 0, 0, textureSizeEnlargedX, textureSizeEnlargedY, null);
                textureImage = largeTextureImage;
                g2.dispose();
                g2 = (Graphics2D) textureImage.getGraphics();
            }
            if (containsTexture) {
                // draw image large
                if (isTexture) {
                    // Note: not null is this is checked by the "isTexture" value
                    //noinspection ConstantConditions
                    Image img = WorldManager.getTile(String.valueOf(texture[orientation]));
                    // this can happen when the tile was already removed,
                    // but the voxel is not updated yet
                    if (img != null) {
                        int[] rotBuff = face.getRotation();
                        int rotation = rotBuff == null ? 0 : rotBuff[orientation];
                        boolean[] flipBuff = face.getFlip();
                        boolean isFlip = flipBuff != null && flipBuff[orientation];

                        // account for the orientation "mess"
                        // (the rotation should be intuitive to use,
                        // but that doesn't correspond to "simple" code)
                        int type;
                        switch (orientation) {
                            case 0:
                                type = ((-rotation + 3)%4) * 2 + (isFlip ? 0 : 1);
                                break;
                            case 1:
                                type = ((rotation + 3)%4) * 2 + (isFlip ? 1 : 0);
                                break;
                            case 2:
                                type = ((rotation + 2)%4) * 2 + (isFlip ? 1 : 0);
                                break;
                            case 3:
                                type = ((-rotation + 6)%4) * 2 + (isFlip ? 0 : 1);
                                break;
                            case 4:
                                type = ((-rotation + 4)%4) * 2 + (isFlip ? 0 : 1);
                                break;
                            case 5:
                                type = rotation * 2 + (isFlip ? 1 : 0);
                                break;
                            default:
                                type = rotation * 2 + (isFlip ? 1 : 0);
                                break;
                        }

                        int d11 = (x + 1)*32, d21 = (y + 1)*32, d12 = d11 + 32, d22 = d21 + 32;


                        switch (type) {
                            case 1:case 2:case 3:case 5:case 6:case 7:
                                g2.drawImage(GraphicTools.rotate(img, type),
                                        d11, d21, d12, d22,
                                        s11, s21, s12, s22,
                                        null);
                                break;
                            case 4:
                                // rotation180, no flip
                                g2.drawImage(img,
                                        d11, d21, d12, d22,
                                        s22, s12, s21, s11,
                                        null);
                                break;
                            default: // no rotation or flip
                                g2.drawImage(img, d11, d21, null);
                                break;
                        }
                    }
                } else {
                    g2.setColor(face.getColor());
                    g2.fillRect((x + 1)*32, (y + 1)*32, 32, 32);
                }
            } else {
                // set the pixel
                textureImage.setRGB(x + 1, y + 1, face.getColor().getRGB());
            }

            if (!isTexture) {
                // ========================

                // check if this is edge pixel
                if (x == 0 && y == 0) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, -1, -1);
                }
                if (x == w - 1 && y == h - 1) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, 1, 1);
                }
                if (x == 0 && y == h - 1) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, -1, 1);
                }
                if (x == w - 1 && y == 0) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, 1, -1);
                }

                if (x == 0) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, -1, 0);
                }
                if (x == w - 1) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, 1, 0);
                }

                if (y == 0) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, 0, -1);
                }
                if (y == h - 1) {
                    interpolatePixel(pos2D, face, axis, hullManager, orientation,
                            containsTexture, g2, x, y, textureImage, 0, 1);
                }

                // =======================
            }

            // store in the seen list
            if (seenTrianglePoints != null) {
                seenTrianglePoints.add(new Point(x,y));
            }
        }
        g2.dispose();

        // get the hash of the texture as texture key
        String textureNameNew = GraphicTools.getHash(textureImage);

        if (!textureNameNew.equals(textureName)) {

            // update count and load if necessary
            Integer count = textureCount.get(textureNameNew);
            if (count == null) {
                count = 0;
                // load the texture
                WorldManager.loadEfficientTexture(textureNameNew, textureImage, false);
            }
            textureCount.put(textureNameNew, count + 1);
            // get the id for this texture
            textureId = WorldManager.getTextureId(textureNameNew);
            // update the texture of the object
            obj.setTexture(textureNameNew);
            // remove existing (old) texture information
            if (textureName != null) {
                setCurrentTextureUnused();
            }
            // assign the new texture name
            textureName = textureNameNew;
        }

    }
}
