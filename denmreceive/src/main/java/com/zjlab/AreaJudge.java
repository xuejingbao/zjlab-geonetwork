package com.zjlab;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author xue
 * @create 2022-10-27 9:44
 */
public enum AreaJudge {

    /**
     * 单例
     */
    INSTANCE;

    private AtomicReference<CircularAreaJudge> areaJudge = new AtomicReference<>();

    public void changeArea(CircularAreaJudge circularAreaJudge) {
        if (!circularAreaJudge.equals(areaJudge.get())) {
            areaJudge.set(circularAreaJudge);
        }
    }

    public WarnMessage inside(Position position) {
        CircularAreaJudge areaJudge = this.areaJudge.get();
        if (areaJudge != null) {
            if (areaJudge.areaJudge(position)) {
                return new WarnMessage(new Position(
                        areaJudge.getLat(),
                        areaJudge.getLon()),
                        areaJudge.getRadius(), position
                );
            }
        }
        return null;
    }

}
