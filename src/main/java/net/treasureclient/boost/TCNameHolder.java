package net.treasureclient.boost;

/** Name Tag Cache: what every entity keeps (added to Entity by NameTagEntityMixin). */
public interface TCNameHolder {
    Object tc$name();

    void tc$setName(Object tag);

    Object tc$score();

    void tc$setScore(Object tag);
}
