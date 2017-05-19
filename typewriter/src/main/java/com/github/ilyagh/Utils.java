package com.github.ilyagh;

import android.content.Context;

class Utils {

    static int convertDpToPixel(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    /**
     * Rotate point P around center point C.
     * @param pX x coordinate of point P
     * @param pY y coordinate of point P
     * @param cX x coordinate of point C
     * @param cY y coordinate of point C
     * @param angleInDegrees rotation angle in degrees
     * @return new x coordinate
     */
    static float rotateX(float pX, float pY, float cX, float cY, float angleInDegrees) {
        double angle = Math.toRadians(angleInDegrees);
        return (float) (Math.cos(angle) * (pX - cX) - Math.sin(angle) * (pY - cY) + cX);
    }

    /**
     * Rotate point P around center point C.
     * @param pX x coordinate of point P
     * @param pY y coordinate of point P
     * @param cX x coordinate of point C
     * @param cY y coordinate of point C
     * @param angleInDegrees rotation angle in degrees
     * @return new y coordinate
     */
    static float rotateY(float pX, float pY, float cX, float cY, float angleInDegrees) {
        double angle = Math.toRadians(angleInDegrees);
        return (float) (Math.sin(angle) * (pX - cX) + Math.cos(angle) * (pY - cY) + cY);
    }
}
