package com.dji.sample.manage.model.enums;

public enum UserStatusEnum {
    ENABLE(1),

    NOT_ACTIVATED(0),

    DISABLE(-1);

    private Integer val;

    UserStatusEnum(Integer val) {
        this.val = val;
    }

    public Integer getVal() {
        return this.val;
    }

    public void setVal(Integer val) {
        this.val = val;
    }

    public static UserStatusEnum find(int val) {
        if (val == ENABLE.val) {
            return ENABLE;
        }
        if (val == NOT_ACTIVATED.val) {
            return NOT_ACTIVATED;
        }
        if (val == DISABLE.val) {
            return DISABLE;
        }
        return null;
    }
}
