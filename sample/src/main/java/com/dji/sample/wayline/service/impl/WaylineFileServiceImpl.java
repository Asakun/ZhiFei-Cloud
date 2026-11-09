package com.dji.sample.wayline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cleaner.djuav.domain.UavRouteReq;
import com.dji.sample.common.util.KmzUtil;
import com.dji.sample.component.oss.model.OssConfiguration;
import com.dji.sample.component.oss.service.impl.OssServiceContext;
import com.dji.sample.wayline.dao.IWaylineFileEditMapper;
import com.dji.sample.wayline.dao.IWaylineFileMapper;
import com.dji.sample.wayline.model.dto.KmzFileProperties;
import com.dji.sample.wayline.model.dto.WaylineFileDTO;
import com.dji.sample.wayline.model.entity.WaylineFileEditEntity;
import com.dji.sample.wayline.model.entity.WaylineFileEntity;
import com.dji.sample.wayline.model.param.WaylineQueryParam;
import com.dji.sample.wayline.service.IWaylineFileService;
import com.dji.sdk.cloudapi.device.DeviceDomainEnum;
import com.dji.sdk.cloudapi.device.DeviceEnum;
import com.dji.sdk.cloudapi.device.DeviceSubTypeEnum;
import com.dji.sdk.cloudapi.device.DeviceTypeEnum;
import com.dji.sdk.cloudapi.wayline.GetWaylineListRequest;
import com.dji.sdk.cloudapi.wayline.GetWaylineListResponse;
import com.dji.sdk.cloudapi.wayline.WaylineTypeEnum;
import com.dji.sdk.common.Pagination;
import com.dji.sdk.common.PaginationData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.dji.sample.wayline.model.dto.KmzFileProperties.WAYLINE_FILE_SUFFIX;

/**
 * @author sean
 * @version 0.3
 * @date 2021/12/22
 */
@Service
@Transactional
public class WaylineFileServiceImpl implements IWaylineFileService {

    @Autowired
    private IWaylineFileMapper mapper;

    @Autowired
    private IWaylineFileEditMapper editMapper;

    @Autowired
    private OssServiceContext ossService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PaginationData<GetWaylineListResponse> getWaylinesByParam(String workspaceId, GetWaylineListRequest param) {
        // Paging Query
        Page<WaylineFileEntity> page = mapper.selectPage(
                new Page<WaylineFileEntity>(param.getPage(), param.getPageSize()),
                new LambdaQueryWrapper<WaylineFileEntity>()
                        .eq(WaylineFileEntity::getWorkspaceId, workspaceId)
                        .eq(Objects.nonNull(param.getFavorited()), WaylineFileEntity::getFavorited, param.getFavorited())
                        .and(param.getTemplateType() != null, wrapper ->  {
                                for (WaylineTypeEnum type : param.getTemplateType()) {
                                    wrapper.like(WaylineFileEntity::getTemplateTypes, type.getValue()).or();
                                }
                        })
                        .and(param.getPayloadModelKey() != null, wrapper ->  {
                                for (DeviceEnum type : param.getPayloadModelKey()) {
                                    wrapper.like(WaylineFileEntity::getPayloadModelKeys, type.getType()).or();
                                }
                        })
                        .and(param.getDroneModelKeys() != null, wrapper ->  {
                                for (DeviceEnum type : param.getDroneModelKeys()) {
                                    wrapper.eq(WaylineFileEntity::getDroneModelKey, type.getType()).or();
                                }
                        })
                        .like(Objects.nonNull(param.getKey()), WaylineFileEntity::getName, param.getKey())
                        // There is a risk of SQL injection
                        .last(Objects.nonNull(param.getOrderBy()), " order by " + param.getOrderBy().toString()));

        // Wrap the results of a paging query into a custom paging object.
        List<GetWaylineListResponse> records = page.getRecords()
                .stream()
                .map(this::entityConvertToDTO)
                .collect(Collectors.toList());

        return new PaginationData<>(records, new Pagination(page.getCurrent(), page.getSize(), page.getTotal()));
    }

    @Override
    public Optional<GetWaylineListResponse> getWaylineByWaylineId(String workspaceId, String waylineId) {
        return Optional.ofNullable(
                this.entityConvertToDTO(getWaylineFileByWaylineId(workspaceId, waylineId)));
    }

    @Override
    public WaylineFileEntity getWaylineFileByWaylineId(String workspaceId, String waylineId) {
        return mapper.selectOne(
                new LambdaQueryWrapper<WaylineFileEntity>()
                        .eq(WaylineFileEntity::getWorkspaceId, workspaceId)
                        .eq(WaylineFileEntity::getWaylineId, waylineId));
    }

    @Override
    public URL getObjectUrl(String workspaceId, String waylineId) throws SQLException {
        Optional<GetWaylineListResponse> waylineOpt = this.getWaylineByWaylineId(workspaceId, waylineId);
        if (waylineOpt.isEmpty()) {
            throw new SQLException(waylineId + " does not exist.");
        }
        return ossService.getObjectUrl(OssConfiguration.bucket, waylineOpt.get().getObjectKey());
    }

    @Override
    public Integer saveWaylineFile(String workspaceId, WaylineFileDTO metadata) {
        metadata.setWaylineId(UUID.randomUUID().toString());
        WaylineFileEntity file = this.dtoConvertToEntity(metadata);
        file.setWorkspaceId(workspaceId);

        if (!StringUtils.hasText(file.getSign())) {
            try (InputStream object = ossService.getObject(OssConfiguration.bucket, metadata.getObjectKey())) {
                if (object.available() == 0) {
                    throw new RuntimeException("The file " + metadata.getObjectKey() +
                            " does not exist in the bucket[" + OssConfiguration.bucket + "].");
                }
                file.setSign(DigestUtils.md5DigestAsHex(object));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        int insertId = mapper.insert(file);
        return insertId > 0 ? file.getId() : insertId;
    }

    @Override
    public Boolean markFavorite(String workspaceId, List<String> waylineIds, Boolean isFavorite) {
        if (waylineIds.isEmpty()) {
            return false;
        }
        if (isFavorite == null) {
            return true;
        }
        return mapper.update(null, new LambdaUpdateWrapper<WaylineFileEntity>()
                .set(WaylineFileEntity::getFavorited, isFavorite)
                .eq(WaylineFileEntity::getWorkspaceId, workspaceId)
                .in(WaylineFileEntity::getWaylineId, waylineIds)) > 0;
    }

    @Override
    public List<String> getDuplicateNames(String workspaceId, List<String> names) {
        return mapper.selectList(new LambdaQueryWrapper<WaylineFileEntity>()
                .eq(WaylineFileEntity::getWorkspaceId, workspaceId)
                .in(WaylineFileEntity::getName, names))
                .stream()
                .map(WaylineFileEntity::getName)
                .collect(Collectors.toList());
    }

    @Override
    public Boolean deleteByWaylineId(String workspaceId, String waylineId) {
        Optional<GetWaylineListResponse> waylineOpt = this.getWaylineByWaylineId(workspaceId, waylineId);
        if (waylineOpt.isEmpty()) {
            return true;
        }
        GetWaylineListResponse wayline = waylineOpt.get();
        boolean isDel = mapper.delete(new LambdaUpdateWrapper<WaylineFileEntity>()
                    .eq(WaylineFileEntity::getWorkspaceId, workspaceId)
                    .eq(WaylineFileEntity::getWaylineId, waylineId))
                > 0;
        if (!isDel) {
            return false;
        }
        editMapper.delete(new LambdaUpdateWrapper<WaylineFileEditEntity>()
                .eq(WaylineFileEditEntity::getWaylineId, waylineId));
        return ossService.deleteObject(OssConfiguration.bucket, wayline.getObjectKey());
    }

    public void insertWaylineFileEditByInputStream(MultipartFile file, String waylineId, String kmzJson) throws IOException {
        if (kmzJson == null) {
            UavRouteReq uavRouteReq = KmzUtil.parseKmz(file.getInputStream());
            kmzJson = objectMapper.writeValueAsString(uavRouteReq);
        }
        editMapper.insert(WaylineFileEditEntity.builder().waylineId(waylineId).uavRoute(kmzJson).build());
    }

    @Override
    public String importKmzFile(MultipartFile file, String workspaceId, String creator, String kmzJson) {
        Optional<WaylineFileDTO> waylineFileOpt = validKmzFile(file);
        if (waylineFileOpt.isEmpty()) {
            throw new RuntimeException("The file format is incorrect.");
        }

        try {
            WaylineFileDTO waylineFile = waylineFileOpt.get();
            waylineFile.setUsername(creator);

            ossService.putObject(OssConfiguration.bucket, waylineFile.getObjectKey(), file.getInputStream());
            this.saveWaylineFile(workspaceId, waylineFile);
            insertWaylineFileEditByInputStream(file, waylineFile.getWaylineId(), kmzJson);
            return waylineFile.getWaylineId();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("import kmz file failed.");
        }
    }

    @Override
    public Integer updateKmzFile(MultipartFile file, WaylineFileEntity waylineFile, String creator) throws IOException {
        Optional<WaylineFileDTO> waylineFileOpt = validKmzFile(file);
        if (waylineFileOpt.isEmpty()) {
            throw new RuntimeException("The file format is incorrect.");
        }
        waylineFile.setUsername(creator);
        waylineFile.setSign(waylineFileOpt.get().getSign());

        ossService.overwritePutObject(OssConfiguration.bucket, waylineFile.getObjectKey(), file.getInputStream());
        return mapper.updateById(waylineFile);
    }

    private Optional<WaylineFileDTO> validKmzFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (Objects.nonNull(filename) && !filename.endsWith(WAYLINE_FILE_SUFFIX)) {
            throw new RuntimeException("The file format is incorrect.");
        }
        try (ZipInputStream unzipFile = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {

            ZipEntry nextEntry = unzipFile.getNextEntry();
            while (Objects.nonNull(nextEntry)) {
                boolean isWaylines = (KmzFileProperties.FILE_DIR_FIRST + "/" + KmzFileProperties.FILE_DIR_SECOND_TEMPLATE).equals(nextEntry.getName());
                if (!isWaylines) {
                    nextEntry = unzipFile.getNextEntry();
                    continue;
                }
                SAXReader reader = new SAXReader();
                Document document = reader.read(unzipFile);
                if (!StandardCharsets.UTF_8.name().equals(document.getXMLEncoding())) {
                    throw new RuntimeException("The file encoding format is incorrect.");
                }

                Node droneNode = document.selectSingleNode("//" + KmzFileProperties.TAG_WPML_PREFIX + KmzFileProperties.TAG_DRONE_INFO);
                Node payloadNode = document.selectSingleNode("//" + KmzFileProperties.TAG_WPML_PREFIX + KmzFileProperties.TAG_PAYLOAD_INFO);
                if (Objects.isNull(droneNode) || Objects.isNull(payloadNode)) {
                    throw new RuntimeException("The file format is incorrect.");
                }

                DeviceTypeEnum type = DeviceTypeEnum.find(Integer.parseInt(droneNode.valueOf(KmzFileProperties.TAG_WPML_PREFIX + KmzFileProperties.TAG_DRONE_ENUM_VALUE)));
                DeviceSubTypeEnum subType = DeviceSubTypeEnum.find(Integer.parseInt(droneNode.valueOf(KmzFileProperties.TAG_WPML_PREFIX + KmzFileProperties.TAG_DRONE_SUB_ENUM_VALUE)));
                DeviceTypeEnum payloadType = DeviceTypeEnum.find(Integer.parseInt(payloadNode.valueOf(KmzFileProperties.TAG_WPML_PREFIX + KmzFileProperties.TAG_PAYLOAD_ENUM_VALUE)));
                DeviceSubTypeEnum payloadSubType = DeviceSubTypeEnum.find(Integer.parseInt(payloadNode.valueOf(KmzFileProperties.TAG_WPML_PREFIX + KmzFileProperties.TAG_PAYLOAD_SUB_ENUM_VALUE)));
                String templateType = document.valueOf("//" + KmzFileProperties.TAG_WPML_PREFIX + KmzFileProperties.TAG_TEMPLATE_TYPE);

                return Optional.of(WaylineFileDTO.builder()
                        .droneModelKey(DeviceEnum.find(DeviceDomainEnum.DRONE, type, subType).getDevice())
                        .payloadModelKeys(List.of(DeviceEnum.find(DeviceDomainEnum.PAYLOAD, payloadType, payloadSubType).getDevice()))
                        .objectKey(OssConfiguration.objectDirPrefix + File.separator + filename)
                        .name(filename.substring(0, filename.lastIndexOf(WAYLINE_FILE_SUFFIX)))
                        .sign(DigestUtils.md5DigestAsHex(file.getInputStream()))
                        .templateTypes(List.of(WaylineTypeEnum.find(templateType).getValue()))
                        .build());
            }

        } catch (IOException | DocumentException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Convert database entity objects into wayline data transfer object.
     * @param entity
     * @return
     */
    private GetWaylineListResponse entityConvertToDTO(WaylineFileEntity entity) {
        if (entity == null) {
            return null;
        }
        return new GetWaylineListResponse()
                .setDroneModelKey(DeviceEnum.find(entity.getDroneModelKey()))
                .setFavorited(entity.getFavorited())
                .setName(entity.getName())
                .setPayloadModelKeys(entity.getPayloadModelKeys() != null ?
                        Arrays.stream(entity.getPayloadModelKeys().split(",")).map(DeviceEnum::find).collect(Collectors.toList()) : null)
                .setTemplateTypes(Arrays.stream(entity.getTemplateTypes().split(","))
                        .map(Integer::parseInt).map(WaylineTypeEnum::find)
                        .collect(Collectors.toList()))
                .setUsername(entity.getUsername())
                .setObjectKey(entity.getObjectKey())
                .setSign(entity.getSign())
                .setUpdateTime(entity.getUpdateTime())
                .setCreateTime(entity.getCreateTime())
                .setId(entity.getWaylineId());

    }

    /**
     * Convert the received wayline object into a database entity object.
     * @param file
     * @return
     */
    private WaylineFileEntity dtoConvertToEntity(WaylineFileDTO file) {
        WaylineFileEntity.WaylineFileEntityBuilder builder = WaylineFileEntity.builder();

        if (file != null) {
            builder.droneModelKey(file.getDroneModelKey())
                    .waylineId(file.getWaylineId())
                    .name(file.getName())
                    .username(file.getUsername())
                    .objectKey(file.getObjectKey())
                    // Separate multiple payload data with ",".
                    .payloadModelKeys(String.join(",", file.getPayloadModelKeys()))
                    .templateTypes(file.getTemplateTypes().stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")))
                    .favorited(file.getFavorited())
                    .sign(file.getSign())
                    .build();
        }

        return builder.build();
    }

    @Override
    public PaginationData<WaylineFileDTO> getWaylinesByParam(String workspaceId, WaylineQueryParam queryParam, long page, long pageSize) {
        Page<WaylineFileEntity> pageData = mapper.selectPage(
                new Page<WaylineFileEntity>(page, pageSize),
                new LambdaQueryWrapper<WaylineFileEntity>()
                        .eq(WaylineFileEntity::getWorkspaceId, workspaceId)
                        .eq(StringUtils.hasText(queryParam.getWaylineId()), WaylineFileEntity::getWaylineId, queryParam.getWaylineId())
                        .like(StringUtils.hasText(queryParam.getWaylineName()), WaylineFileEntity::getName, queryParam.getWaylineName())
                        .eq(queryParam.getFavorited() != null, WaylineFileEntity::getFavorited, queryParam.getFavorited())
                        .like(queryParam.getTemplateType() != null, WaylineFileEntity::getTemplateTypes, queryParam.getTemplateType())
                        .eq(StringUtils.hasText(queryParam.getUsername()), WaylineFileEntity::getUsername, queryParam.getUsername())
                        .eq(StringUtils.hasText(queryParam.getDockSn()), WaylineFileEntity::getDockSn, queryParam.getDockSn())
                        .orderByDesc(WaylineFileEntity::getId));
        List<WaylineFileDTO> records = pageData.getRecords()
                .stream()
                .map(this::entity2Dto)
                .collect(Collectors.toList());

        return new PaginationData<>(records, new Pagination(pageData.getCurrent(), pageData.getSize(), pageData.getTotal()));
    }

    private WaylineFileDTO entity2Dto(WaylineFileEntity entity) {
        if (entity == null) {
            return null;
        }
        return WaylineFileDTO.builder()
                .waylineId(entity.getWaylineId())
                .name(entity.getName())
                .username(entity.getUsername())
                .objectKey(entity.getObjectKey())
                .sign(entity.getSign())
                .droneModelKey(entity.getDroneModelKey())
                .payloadModelKeys(StringUtils.hasText(entity.getPayloadModelKeys())?Arrays.stream(entity.getPayloadModelKeys().split(",")).collect(Collectors.toList()):null)
                .favorited(entity.getFavorited())
                .templateTypes(Arrays.stream(entity.getTemplateTypes().split(",")).map(Integer::valueOf).collect(Collectors.toList()))
                .dockSn(entity.getDockSn())
                .updateTime(entity.getUpdateTime())
                .build();
    }
    
}
