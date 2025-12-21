package com.particle_life;

import org.joml.Vector3d;

import java.util.Arrays;
import java.util.Collections;

public class Physics {

    private static final int DEFAULT_MATRIX_SIZE = 7;

    public PhysicsSettings settings = new PhysicsSettings();

    public Particle[] particles;

    private Accelerator accelerator;
    private MatrixGenerator matrixGenerator;
    private PositionSetter positionSetter;
    /**
     * This is the TypeSetter that is used by default whenever
     * an individual particle needs to be assigned a new type.
     * @see TypeSetter#getType
     */
    private TypeSetter typeSetter;

    // INITIALIZATION:

    /**
     * Shorthand constructor for {@link #Physics(Accelerator, PositionSetter, MatrixGenerator, TypeSetter)} using
     * <ul>
     *     <li>{@link DefaultPositionSetter}</li>
     *     <li>{@link DefaultMatrixGenerator}</li>
     *     <li>{@link DefaultTypeSetter}</li>
     * </ul>
     */
    public Physics(Accelerator accelerator) {
        this(accelerator, new DefaultPositionSetter(), new DefaultMatrixGenerator(), new DefaultTypeSetter());
    }

    /**
     * @param accelerator
     * @param positionSetter
     * @param matrixGenerator
     */
    public Physics(Accelerator accelerator,
                   PositionSetter positionSetter,
                   MatrixGenerator matrixGenerator,
                   TypeSetter typeSetter) {

        this.accelerator = accelerator;
        this.positionSetter = positionSetter;
        this.matrixGenerator = matrixGenerator;
        this.typeSetter = typeSetter;

        generateMatrix();
        setParticleCount(1000);  // uses current position setter to create particles
    }

    /**
     * Calculate the next step in the simulation.
     * That is, it changes the velocity and position of each particle
     * in the particle array according to <code>this.settings</code>.
     */
    public void update() {
        updateParticles();
    }

    private void updateParticles() {
        for (int i = 0; i < particles.length; i++) {
            updateVelocity(i);
            updatePosition(i);
        }
    }

    public void generateMatrix() {
        int prevSize = settings.matrix != null ? settings.matrix.size() : DEFAULT_MATRIX_SIZE;
        settings.matrix = matrixGenerator.makeMatrix(prevSize);

        assert settings.matrix.size() == prevSize : "Matrix size should only change via setMatrixSize()";
    }

    /**
     * Set the size of the particle array.<br><br>
     * If the particle array is null, a new array will be created.
     * If n is greater than the current particle array size, new particles will be created.
     * If n is smaller than the current particle array size, random particles will be removed.
     * In that case, the order of the particles in the array will be random afterwards.<br>
     * New particles will be created using the active position setter.
     * 
     * @param n The new number of particles. Must be 0 or greater.
     */
    private void setParticleCount(int n) {
        if (particles == null) {
            particles = new Particle[n];
            for (int i = 0; i < n; i++) {
                particles[i] = generateParticle();
            }
        } else if (n != particles.length) {
            // strategy: if the array size changed, try to keep most of the particles

            Particle[] newParticles = new Particle[n];

            if (n < particles.length) {  // array becomes shorter
                // randomly shuffle particles first
                // (otherwise, the container layout becomes visible)
                shuffleParticles();

                // copy previous array as far as possible
                for (int i = 0; i < n; i++) {
                    newParticles[i] = particles[i];
                }
            } else {  // array becomes longer
                // copy old array and add particles to the end
                for (int i = 0; i < particles.length; i++) {
                    newParticles[i] = particles[i];
                }
                for (int i = particles.length; i < n; i++) {
                    newParticles[i] = generateParticle();
                }
            }
            particles = newParticles;
        }
    }

    /**
     * Use this to avoid the container pattern showing
     * (i.e. if particles are treated differently depending on their position in the array).
     */
    private void shuffleParticles() {
        Collections.shuffle(Arrays.asList(particles));
    }

    /**
     * Creates a new particle and
     * <ol>
     *     <li>sets its type using the default type setter</li>
     *     <li>sets its position using the active position setter</li>
     * </ol>
     * (in that order) and returns it.
     */
    private Particle generateParticle() {
        Particle p = new Particle();
        setType(p);
        setPosition(p);
        return p;
    }

    protected final void setPosition(Particle p) {
        positionSetter.set(p.position, p.type, settings.matrix.size());
        ensurePosition(p.position);
        p.velocity.x = 0;
        p.velocity.y = 0;
        p.velocity.z = 0;
    }

    protected final void setType(Particle p) {
        p.type = typeSetter.getType(new Vector3d(p.position), new Vector3d(p.velocity), p.type, settings.matrix.size());
    }
    
    private void updateVelocity(int i) {
        Particle p = particles[i];

        // apply friction before adding new velocity
        double frictionFactor = Math.pow(settings.friction, 60 * settings.dt);
        p.velocity.mul(frictionFactor);

        for (int j = 0; j < particles.length; j++) {
            if (j == i) continue;
            Particle q = particles[j];

            Vector3d relativePosition = connection(p.position, q.position);

            double distanceSquared = relativePosition.lengthSquared();
            if (distanceSquared != 0 && distanceSquared <= settings.rmax * settings.rmax) {

                relativePosition.div(settings.rmax);
                Vector3d deltaV = accelerator.accelerate(settings.matrix.get(p.type, q.type), relativePosition);
                // apply force as acceleration
                p.velocity.add(deltaV.mul(settings.rmax * settings.force * settings.dt));
            }
        }
    }

    private void updatePosition(int i) {
        Particle p = particles[i];

        // pos += vel * dt;
        p.velocity.mulAdd(settings.dt, p.position, p.position);

        ensurePosition(p.position);
    }

    /**
     * Calculates the shortest connection between two positions.
     * If <code>settings.wrap == true</code>, the connection might
     * go across the world's borders.
     * @param pos1 first position, with coordinates in the range [0, 1].
     * @param pos2 second position, with coordinates in the range [0, 1].
     * @return the shortest connection between the two positions
     */
    private Vector3d connection(Vector3d pos1, Vector3d pos2) {
        Vector3d delta = new Vector3d(pos2).sub(pos1);
        if (settings.wrap) {
            // wrapping the connection gives us the shortest possible distance
            Range.wrapConnection(delta);
        }
        return delta;
    }

    /**
     * Changes the coordinates of the given vector to ensures that they are in the correct range.
     * <ul>
     *     <li>
     *         If <code>settings.wrap == false</code>,
     *         the coordinates are simply clamped to [0.0, 1.0].
     *     </li>
     *     <li>
     *         If <code>settings.wrap == true</code>,
     *         the coordinates are made to be inside [0.0, 1.0) by adding or subtracting multiples of 1.
     *     </li>
     * </ul>
     * This method is called by {@link #update()} after changing the particles' positions.
     * It is just exposed for convenience.
     * That is, if you change the coordinates of the particles yourself,
     * you can use this to make sure that the coordinates are in the correct range before {@link #update()} is called.
     * @param position
     */
    public void ensurePosition(Vector3d position) {
        if (settings.wrap) {
            Range.wrap(position);
        } else {
            Range.clamp(position);
        }
    }
}
