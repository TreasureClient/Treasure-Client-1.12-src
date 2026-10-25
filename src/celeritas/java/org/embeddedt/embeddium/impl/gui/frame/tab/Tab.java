package org.embeddedt.embeddium.impl.gui.frame.tab;

import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.embeddedt.embeddium.impl.gui.framework.TextFormattingStyle;
import org.embeddedt.embeddium.impl.util.Dim2i;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.embeddedt.embeddium.impl.gui.frame.AbstractFrame;
import org.embeddedt.embeddium.impl.gui.frame.OptionPageFrame;
import org.embeddedt.embeddium.impl.gui.frame.ScrollableFrame;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record Tab<T extends AbstractFrame>(OptionIdentifier<Void> id, TextComponent title, Supplier<Boolean> onSelectFunction, Function<Dim2i, T> frameFunction) {
    public static Tab.Builder<?> createBuilder() {
        return new Tab.Builder<>();
    }

    public T createFrame(Dim2i dim) {
        return this.frameFunction != null ? this.frameFunction.apply(dim) : null;
    }

    public static Tab<ScrollableFrame> from(OptionPage page, Predicate<Option<?>> optionFilter, AtomicReference<Integer> verticalScrollBarOffset) {
        Function<Dim2i, ScrollableFrame> frameFunction = dim2i -> ScrollableFrame.createBuilder().setDimension(dim2i).setFrame(OptionPageFrame.createBuilder().setDimension(new Dim2i(dim2i.x(), dim2i.y(), dim2i.width(), dim2i.height())).setOptionPage(page).setOptionFilter(optionFilter).build()).setVerticalScrollBarOffset(verticalScrollBarOffset).build();
        return Tab.<ScrollableFrame>builder().setTitle(page.getName()).setId(page.getId()).setFrameFunction(frameFunction).build();
    }


    @java.lang.SuppressWarnings("all")
    public static class Builder<T extends AbstractFrame> {
        @java.lang.SuppressWarnings("all")
        private OptionIdentifier<Void> id;
        @java.lang.SuppressWarnings("all")
        private TextComponent title;
        @java.lang.SuppressWarnings("all")
        private Supplier<Boolean> onSelectFunction;
        @java.lang.SuppressWarnings("all")
        private Function<Dim2i, T> frameFunction;

        @java.lang.SuppressWarnings("all")
        Builder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Tab.Builder<T> setId(final OptionIdentifier<Void> id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Tab.Builder<T> setTitle(final TextComponent title) {
            this.title = title;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Tab.Builder<T> setOnSelectFunction(final Supplier<Boolean> onSelectFunction) {
            this.onSelectFunction = onSelectFunction;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Tab.Builder<T> setFrameFunction(final Function<Dim2i, T> frameFunction) {
            this.frameFunction = frameFunction;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Tab<T> build() {
            return new Tab<T>(this.id, this.title, this.onSelectFunction, this.frameFunction);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "Tab.Builder(id=" + this.id + ", title=" + this.title + ", onSelectFunction=" + this.onSelectFunction + ", frameFunction=" + this.frameFunction + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static <T extends AbstractFrame> Tab.Builder<T> builder() {
        return new Tab.Builder<T>();
    }
}
