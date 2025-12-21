package com.particle_life.app;

import com.particle_life.Physics;
import com.particle_life.Particle;
import com.particle_life.PhysicsSettings;

class PhysicsSnapshot {

    double[] positions;
    double[] velocities;
    int[] types;

    PhysicsSettings settings;
    int particleCount;

    void take(Physics p) {
        write(p.particles);
        settings = p.settings.deepCopy();
        particleCount = p.particles.length;
    }

    private void write(Particle[] particles) {
        int n = particles.length;

        if (types == null || types.length != n) {
            positions = new double[n * 3];
            velocities = new double[n * 3];
            types = new int[n];
        }

        for (int i = 0; i < n; i++) {
            Particle p = particles[i];

            final int i3 = 3 * i;

            positions[i3] = p.position.x;
            positions[i3 + 1] = p.position.y;
            positions[i3 + 2] = p.position.z;

            velocities[i3] = p.velocity.x;
            velocities[i3 + 1] = p.velocity.y;
            velocities[i3 + 2] = p.velocity.z;

            types[i] = p.type;
        }
    }
}
