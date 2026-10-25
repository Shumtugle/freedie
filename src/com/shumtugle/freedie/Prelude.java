package com.shumtugle.freedie;

/** What plays before the game: a timeline of its own, its own eye, its own things to draw. */
interface Prelude {
    void update(float dt);

    /** The eye, the light and the dark for this moment; the world has set the rest. */
    void frame(DrawList d, int w);

    /** Everything it shows, into the list. */
    void draw(DrawList d);

    /** Whatever it lays flat over the frame. */
    void flat(Px f, DrawList d);

    /** A touch: straight on to the game. */
    void skip();

    /** The light is at its whitest: the game takes over. */
    boolean done();
}
