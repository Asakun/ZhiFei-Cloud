package com.dji.sample.component;

import com.dji.sample.component.oss.model.OssConfiguration;
import com.dji.sample.component.oss.service.impl.OssServiceContext;
import com.dji.sample.manage.dao.*;
import com.dji.sample.manage.model.entity.*;
import com.dji.sample.manage.service.IApiKeyService;
import com.dji.sample.map.dao.IGroupMapper;
import com.dji.sample.map.model.entity.GroupEntity;
import com.dji.sample.proxy.service.IEmqxService;
import com.dji.sample.psdk.dao.IPsdkWidgetValueSetMapper;
import com.dji.sample.psdk.model.entity.PsdkWidgetValueSetEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

/**
 * Spring 启动时初始化表数据：无数据则插入
 */
@Component
public class DataInitRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitRunner.class);

    @Autowired
    private IUserRoleMapper userRoleMapper;

    @Autowired
    private IRoleMapper roleMapper;

    @Autowired
    private IUserMapper userMapper;

    @Autowired
    private IWorkspaceMapper workspaceMapper;

    @Autowired
    private IGroupMapper groupMapper;

    @Autowired
    private IDeviceDictionaryMapper dictMapper;

    @Autowired
    private IPsdkWidgetValueSetMapper widgetValueSetMapper;

    @Value("${init.database.admin-name}")
    private String adminName;

    @Value("${init.database.workspace-name}")
    private String workspaceName;

    @Value("${init.database.bind-code}")
    private String bindCode;

    @Autowired
    private IApiKeyService apiKeyService;

    @Autowired
    private OssServiceContext ossServiceContext;

    @Autowired
    private IEmqxService emqxService;

    @Value("${mqtt.BASIC.username}")
    private String mqttBasicUsername;

    @Value("${mqtt.BASIC.password}")
    private String mqttBasicPassword;

    @Value("${mqtt.DRC.username}")
    private String mqttDrcUsername;

    @Value("${mqtt.DRC.password}")
    private String mqttDrcPassword;

    /**
     * 项目启动后执行（Spring 容器初始化完成）
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 事务保证：插入失败回滚
    public void run(String... args) throws Exception {
        initDatabase();

        syncApiKey();

        initOssBucket();

        initEmqxAccount();
    }

    private void initDatabase() {
        long count = roleMapper.selectCount(null);

        if (count == 0) {
            log.info("init database:");
            String workspaceId = UUID.randomUUID().toString();
            String uid = UUID.randomUUID().toString();

            workspaceMapper.insert(new WorkspaceEntity(workspaceId,workspaceName,null,"Cloud Api Platform",bindCode));
            log.info("insert workspace id:{}", workspaceId);

            roleMapper.insert(new RoleEntity(1,"ROLE_ADMIN","管理员","ROLE_MANAGER",5));
            roleMapper.insert(new RoleEntity(2,"ROLE_MANAGER","组管理员","ROLE_USER",3));
            roleMapper.insert(new RoleEntity(3,"ROLE_USER","普通用户",null,1));
            roleMapper.insert(new RoleEntity(4,"ROLE_SHARER","分享用户",null,0));

            userMapper.insert(new UserEntity(uid,adminName,new BCryptPasswordEncoder().encode("password"),workspaceId,1,1));
            log.info("insert user id:{}", uid);

            userRoleMapper.insert(new UserRoleEntity(1,uid,"ROLE_ADMIN"));

            groupMapper.insert(new GroupEntity(UUID.randomUUID().toString(),"Pilot Share Layer",2,workspaceId));
            groupMapper.insert(new GroupEntity(UUID.randomUUID().toString(),"Custom Flight Area",0,workspaceId));

            dictMapper.insert(new DeviceDictionaryEntity(1, 0, 60, 0, "Matrice 300 RTK", null));
            dictMapper.insert(new DeviceDictionaryEntity(2, 0, 67, 0, "Matrice 30", null));
            dictMapper.insert(new DeviceDictionaryEntity(3, 0, 67, 1, "Matrice 30T", null));
            dictMapper.insert(new DeviceDictionaryEntity(4, 1, 20, 0, "Z30", null));
            dictMapper.insert(new DeviceDictionaryEntity(5, 1, 26, 0, "XT2", null));
            dictMapper.insert(new DeviceDictionaryEntity(6, 1, 39, 0, "FPV", null));
            dictMapper.insert(new DeviceDictionaryEntity(7, 1, 41, 0, "XTS", null));
            dictMapper.insert(new DeviceDictionaryEntity(8, 1, 42, 0, "H20", null));
            dictMapper.insert(new DeviceDictionaryEntity(9, 1, 43, 0, "H20T", null));
            dictMapper.insert(new DeviceDictionaryEntity(10, 1, 50, 65535, "P1", "include 24 and 35 and 50mm"));
            dictMapper.insert(new DeviceDictionaryEntity(11, 1, 52, 0, "M30 Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(12, 1, 53, 0, "M30T Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(13, 1, 61, 0, "H20N", null));
            dictMapper.insert(new DeviceDictionaryEntity(14, 1, 165, 0, "DJI Dock Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(15, 1, 90742, 0, "L1", null));
            dictMapper.insert(new DeviceDictionaryEntity(16, 2, 56, 0, "DJI Smart Controller", "Remote control for M300"));
            dictMapper.insert(new DeviceDictionaryEntity(17, 2, 119, 0, "DJI RC Plus", "Remote control for M30"));
            dictMapper.insert(new DeviceDictionaryEntity(18, 3, 1, 0, "DJI Dock", null));
            dictMapper.insert(new DeviceDictionaryEntity(19, 0, 77, 0, "Mavic 3E", null));
            dictMapper.insert(new DeviceDictionaryEntity(20, 0, 77, 1, "Mavic 3T", null));
            dictMapper.insert(new DeviceDictionaryEntity(21, 1, 66, 0, "Mavic 3E Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(22, 1, 67, 0, "Mavic 3T Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(23, 2, 144, 0, "DJI RC Pro", "Remote control for Mavic 3E/T and Mavic 3M"));
            dictMapper.insert(new DeviceDictionaryEntity(24, 0, 77, 2, "Mavic 3M", null));
            dictMapper.insert(new DeviceDictionaryEntity(25, 1, 68, 0, "Mavic 3M Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(26, 0, 89, 0, "Matrice 350 RTK", null));
            dictMapper.insert(new DeviceDictionaryEntity(27, 3, 2, 0, "DJI Dock2", null));
            dictMapper.insert(new DeviceDictionaryEntity(28, 0, 91, 0, "M3D", null));
            dictMapper.insert(new DeviceDictionaryEntity(29, 0, 91, 1, "M3TD", null));
            dictMapper.insert(new DeviceDictionaryEntity(30, 1, 80, 0, "M3D Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(31, 1, 81, 0, "M3TD Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(32, 3, 3, 0, "DJI Dock3", null));
            dictMapper.insert(new DeviceDictionaryEntity(33, 0, 100, 0, "M4D", null));
            dictMapper.insert(new DeviceDictionaryEntity(34, 0, 100, 1, "M4TD", null));
            dictMapper.insert(new DeviceDictionaryEntity(35, 1, 98, 0, "M4D Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(36, 1, 99, 0, "M4TD Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(37, 0, 0, 0, "Empty", null));
            dictMapper.insert(new DeviceDictionaryEntity(39, 2, 174, 0, "DJI RC Plus 2", "Remote control for Mavic 4E/T"));
            dictMapper.insert(new DeviceDictionaryEntity(40, 0, 99, 0, "M4E", null));
            dictMapper.insert(new DeviceDictionaryEntity(41, 0, 99, 1, "M4T", null));
            dictMapper.insert(new DeviceDictionaryEntity(42, 1, 88, 0, "M4E Camera", null));
            dictMapper.insert(new DeviceDictionaryEntity(43, 1, 89, 0, "M4T Camera", null));

            widgetValueSetMapper.insert(new PsdkWidgetValueSetEntity(1,"PSJ_aeroSpot",null,2,8,1,"one pick","单次采集",1));
            widgetValueSetMapper.insert(new PsdkWidgetValueSetEntity(2,"PSJ_aeroSpot",null,2,10,1,"continuous pick start","开始连续采集",1));
            widgetValueSetMapper.insert(new PsdkWidgetValueSetEntity(1,"PSJ_aeroSpot",null,2,11,1,"continuous pick stop","停止连续采集",1));
            widgetValueSetMapper.insert(new PsdkWidgetValueSetEntity(1,"PSJ_aeroSpot",null,2,12,1,"query info","查询信息",1));
            widgetValueSetMapper.insert(new PsdkWidgetValueSetEntity(1,"PSJ_aeroSpot",null,2,14,1,"sd format","sd格式化",1));
            widgetValueSetMapper.insert(new PsdkWidgetValueSetEntity(1,"PSJ_aeroSpot",null,2,15,1,"new folder","新建文件夹",1));
        }
    }

    private void syncApiKey() {
        Integer count = apiKeyService.syncApiKey();
        log.info("sync apikey:{}", count);
    }

    /**
     * 确保默认 OSS 桶存在（不存在则创建）。
     */
    private void initOssBucket() {
        if (!OssConfiguration.enable) {
            log.info("Oss is not enabled, skip bucket init.");
            return;
        }
        boolean result = ossServiceContext.ensureBucket(OssConfiguration.bucket);
        log.info("init oss bucket:{} -> {}", OssConfiguration.bucket, result);
    }

    /**
     * 初始化 EMQX：
     * 1. 确保 JWT 认证器存在（不存在则创建，密钥用 jwt secret，从 MQTT password 验证）。
     * 2. 确保 MQTT BASIC / DRC 两个账号存在。
     * 若 EMQX 登录失败（如账号密码错误），跳过整个初始化，避免阻塞应用启动。
     */
    private void initEmqxAccount() {
        try {
            boolean jwtOk = emqxService.ensureJwtAuthenticator();
            log.info("init emqx jwt authenticator -> {}", jwtOk);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("emqx login failed (401), skip emqx init: {}", e.getResponseBodyAsString());
                return;
            }
            log.error("init emqx jwt authenticator failed.", e);
            return;
        } catch (JsonProcessingException e) {
            log.error("init emqx jwt authenticator failed.", e);
        }
        ensureEmqxAccount(mqttBasicUsername, mqttBasicPassword);
        ensureEmqxAccount(mqttDrcUsername, mqttDrcPassword);
    }

    private void ensureEmqxAccount(String username, String password) {
        try {
            boolean result = emqxService.ensureEmqxAccount(username, password, true);
            log.info("init emqx account({}) -> {}", username, result);
        } catch (JsonProcessingException e) {
            log.error("init emqx account({}) failed.", username, e);
        }
    }
}