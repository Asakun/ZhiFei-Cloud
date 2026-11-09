package com.dji.sample.control.service;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.control.model.dto.AuthorityOwner;
import com.dji.sample.control.model.dto.ControlAuthority;
import com.dji.sample.control.model.enums.DroneAuthorityEnum;

import java.util.Optional;

public interface IControlRedisService {

    AuthorityOwner setAuthorityOwner(DroneAuthorityEnum authority, String dockSn, AuthorityOwner data);

    Optional<AuthorityOwner> getAuthorityOwner(DroneAuthorityEnum authority, String dockSn);

    Boolean deleteAuthorityOwner(DroneAuthorityEnum authority, String dockSn);

    ControlAuthority getControlAuthority(String dockSn);

    Boolean clearControlAuthority(String dockSn);

    Boolean exitControlAuthority(CustomClaim customClaim, String dockSn);
}
