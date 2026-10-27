package com.shumtugle.freedie;

/**
 * A short film between two episodes, made of the street itself: it runs on its own clock,
 * has its own eye, draws what it brings into the scene and over it, and ends in the light.
 */
interface Film {
    void update(float dt);

    /** Whether it has run its course, or was cut short: the light may rise. */
    boolean over();

    /** A touch: after the first time, it cuts the film short. */
    void tap();

    /** What it brings into the scene, besides the bodies it handed to the physics. */
    void draw(DrawList d);

    /** Its own eye. */
    void camera(DrawList d, int w);

    /** Whatever it draws on the flat layer over the scene. */
    void flat(Px f);
}
