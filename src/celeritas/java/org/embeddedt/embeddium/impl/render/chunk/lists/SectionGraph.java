package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionLattice;

import java.util.ArrayDeque;
import org.embeddedt.embeddium.impl.util.SimpleFuture;
import java.util.function.Supplier;

/**
 * State shared by every graph search of one world renderer: the {@link SectionLattice}, the single search thread,
 * and the bookkeeping that keeps lattice mutations off that thread while a search is running.
 *
 * <p>One {@link RenderListManager} per pass (terrain, and optionally shadow) submits its searches here. All searches
 * run on one single-thread executor in submission order, which is what lets the shadow search consume the root set
 * produced by the terrain search submitted just before it.
 */
public final class SectionGraph {
    private final SectionLattice lattice;

    

    // Number of submitted searches that have not been joined. While non-zero, structural mutations are forbidden
    // and metadata updates are deferred.
    private int inFlight;

    // Tasks deferred by submitUpdateTask() while a search is in flight. Drained on the render thread by
    // runDeferredTasks() once every search has been joined.
    private final ArrayDeque<Runnable> updateTasks = new ArrayDeque<>();

    public SectionGraph(int minSectionY, int maxSectionY, AsyncOcclusionMode asyncMode, boolean hasShadowPass, boolean rasterOcclusion) {
        this.lattice = new SectionLattice(minSectionY, maxSectionY, hasShadowPass, rasterOcclusion);

        // Treasure Client: no background threads in the browser, so searches always run inline
    }

    SectionLattice getLattice() {
        return this.lattice;
    }

    /**
     * Run a search task, on the search thread when {@code async} is set and an executor exists, otherwise inline.
     * The caller must pair every submission with {@link #onSearchJoined()} after joining the returned future.
     */
    <T> SimpleFuture<T> submit(Supplier<T> task, boolean async) {
        this.inFlight++;

        if (false) {
            return null;
        } else {
            return SimpleFuture.completedFuture(task.get());
        }
    }

    void onSearchJoined() {
        if (this.inFlight <= 0) {
            throw new IllegalStateException("No search in flight");
        }
        this.inFlight--;
    }

    /**
     * Run deferred lattice updates if no search is in flight. Callers must have joined the searches they submitted,
     * which establishes the happens-before edge for the search thread's writes to the lattice arrays.
     */
    void runDeferredTasks() {
        if (this.inFlight > 0) {
            return;
        }

        Runnable task;

        while ((task = this.updateTasks.poll()) != null) {
            task.run();
        }
    }

    // Structural mutations to the lattice (adding/removing nodes, rewiring neighbor links)
    // are unsafe while a search holds a reference to it.
    private void assertSearchNotRunning() {
        if (this.inFlight > 0) {
            throw new IllegalStateException("Attempted to update occlusion graph during occlusion!");
        }
    }

    public void attachRenderSection(RenderSection section) {
        this.assertSearchNotRunning();
        this.lattice.attach(section);
    }

    public void detachRenderSection(RenderSection section) {
        this.assertSearchNotRunning();
        this.lattice.detach(section);
    }

    /**
     * Mirror a section's packed metadata into the lattice, immediately if no search is active and otherwise once
     * every in-flight search has been joined. {@code onGraphInputChanged} runs (at the same time as the update) when
     * the change can alter a search's output, so the owning managers can request a re-search.
     */
    public void updateSectionMetadata(int x, int y, int z, long packedMetadata, Runnable onGraphInputChanged) {
        this.submitUpdateTask(() -> {
            if (this.lattice.updateSectionMetadata(x, y, z, packedMetadata)) {
                onGraphInputChanged.run();
            }
        });
    }

    // Runs the task immediately if no search is active, otherwise defers it to
    // runDeferredTasks() to prevent concurrent writes to the lattice arrays.
    private void submitUpdateTask(Runnable runnable) {
        if (this.inFlight == 0) {
            runnable.run();
        } else {
            this.updateTasks.add(runnable);
        }
    }

    public void destroy() {
    }
}
