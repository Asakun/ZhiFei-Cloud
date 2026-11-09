package com.dji.sample.control.model.dto;

import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthorityOwner {
    private String authorityOwner;

    private String authorityOwnerId;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false; // 判空 + 判类型

        AuthorityOwner that = (AuthorityOwner) o;

        return ObjectUtil.equal(getAuthorityOwnerId(), that.getAuthorityOwnerId())
                && ObjectUtil.equal(getAuthorityOwner(), that.getAuthorityOwner());
    }
}
