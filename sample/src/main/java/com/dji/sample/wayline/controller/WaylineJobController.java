package com.dji.sample.wayline.controller;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sample.wayline.model.dto.WaylineJobDTO;
import com.dji.sample.wayline.model.enums.WaylineJobGroupByEnum;
import com.dji.sample.wayline.model.enums.WaylineJobSortEnum;
import com.dji.sample.wayline.model.param.CreateJobParam;
import com.dji.sample.wayline.model.param.UpdateJobParam;
import com.dji.sample.wayline.model.param.JobQueryParam;
import com.dji.sample.wayline.service.IFlightTaskService;
import com.dji.sample.wayline.service.IWaylineJobService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.PaginationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dji.sample.component.AuthInterceptor.TOKEN_CLAIM;

/**
 * @author sean
 * @version 1.1
 * @date 2022/6/1
 */
@RequestMapping("${url.wayline.prefix}${url.wayline.version}/workspaces")
@RestController
public class WaylineJobController {

    @Autowired
    private IWaylineJobService waylineJobService;

    @Autowired
    private IFlightTaskService flighttaskService;

    /**
     * Create a wayline task for the Dock.
     * @param request
     * @param param
     * @param workspaceId
     * @return
     * @throws SQLException
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{workspace_id}/flight-tasks")
    public HttpResultResponse createJob(HttpServletRequest request, @Valid @RequestBody CreateJobParam param,
                                        @PathVariable(name = "workspace_id") String workspaceId) throws SQLException {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        customClaim.setWorkspaceId(workspaceId);

        return flighttaskService.publishFlightTask(param, customClaim);
    }

    /**
     * Paginate through all jobs in this workspace.
     * @param page
     * @param pageSize
     * @param workspaceId
     * @return
     */
    @PostMapping("/{workspace_id}/jobs")
    public HttpResultResponse<PaginationData<WaylineJobDTO>> getJobs(@RequestParam(defaultValue = "1") Long page,
                                                                     @RequestParam(name = "page_size", defaultValue = "10") Long pageSize,
                                                                     @PathVariable(name = "workspace_id") String workspaceId,
                                                                     @RequestBody JobQueryParam queryParam) {
        PaginationData<WaylineJobDTO> data = waylineJobService.getJobsByWorkspaceId(workspaceId, queryParam, page, pageSize);
        return HttpResultResponse.success(data);
    }

    /**
     * Send the command to cancel the jobs.
     * @param jobIds
     * @param workspaceId
     * @return
     * @throws SQLException
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @DeleteMapping("/{workspace_id}/jobs")
    public HttpResultResponse publishCancelJob(@RequestParam(name = "job_id") Set<String> jobIds,
                                               @PathVariable(name = "workspace_id") String workspaceId) throws SQLException {
        flighttaskService.cancelFlightTask(workspaceId, jobIds);
        return HttpResultResponse.success();
    }

    /**
     * Set the media files for this job to upload immediately.
     * @param workspaceId
     * @param jobId
     * @return
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{workspace_id}/jobs/{job_id}/media-highest")
    public HttpResultResponse uploadMediaHighestPriority(@PathVariable(name = "workspace_id") String workspaceId,
                                                         @PathVariable(name = "job_id") String jobId) {
        flighttaskService.uploadMediaHighestPriority(workspaceId, jobId);
        return HttpResultResponse.success();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PutMapping("/{workspace_id}/jobs/{job_id}")
    public HttpResultResponse updateJobStatus(@PathVariable(name = "workspace_id") String workspaceId,
                                              @PathVariable(name = "job_id") String jobId,
                                              @Valid @RequestBody UpdateJobParam param) {
        flighttaskService.updateJobStatus(workspaceId, jobId, param);
        return HttpResultResponse.success();
    }

    @GetMapping("/{workspace_id}/ongoing-job")
    public HttpResultResponse getOngoingJob(@PathVariable(name = "workspace_id") String workspaceId, String dockSn) {
        DuringFlightInfo duringFlightInfo = waylineJobService.getOngoingJobIdByDockSn(workspaceId, dockSn);
        return HttpResultResponse.success(duringFlightInfo);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{workspace_id}/ongoing-job")
    public HttpResultResponse updateOngoingJob(@PathVariable(name = "workspace_id") String workspaceId, String dockSn,
                                               @RequestParam(name = "enable_record") Boolean enablePsdkFloatingWindowTextRecord)
            throws IllegalAccessException {
        DuringFlightInfo info = DuringFlightInfo.builder().enablePsdkFloatingWindowTextRecord(enablePsdkFloatingWindowTextRecord).build();
        DuringFlightInfo duringFlightInfo = waylineJobService.setOngoingJobIdByDockSn(workspaceId, dockSn, info);
        return HttpResultResponse.success(duringFlightInfo);
    }

    @GetMapping("/{workspace_id}/job-sum")
    public HttpResultResponse statisticsJobSum(@PathVariable(name = "workspace_id") String workspaceId,
                                               @RequestParam(name="start_time", defaultValue = "") Long startTime,
                                               @RequestParam(name="end_time", defaultValue = "") Long endTime) {
        Map<String, Object> sum = waylineJobService.getWaylineJobSum(workspaceId, startTime, endTime);
        return HttpResultResponse.success(sum);
    }

    @GetMapping("/{workspace_id}/job-sum-by-date")
    public HttpResultResponse statisticsJobSumByDate(@PathVariable(name = "workspace_id") String workspaceId,
                                                     @RequestParam(name="start_time", defaultValue = "") Long startTime,
                                                     @RequestParam(name="end_time", defaultValue = "") Long endTime) {
        List<Map<String, Object>> sum = waylineJobService.getWaylineJobSumByDate(workspaceId, startTime, endTime);
        return HttpResultResponse.success(sum);
    }

    @GetMapping("/{workspace_id}/job-rank")
    public HttpResultResponse statisticsJobRank(@PathVariable(name = "workspace_id") String workspaceId,
                                                @RequestParam(name="start_time", defaultValue = "") Long startTime,
                                                @RequestParam(name="end_time", defaultValue = "") Long endTime,
                                                @RequestParam(name="group_by") WaylineJobGroupByEnum groupBy,
                                                WaylineJobSortEnum sort) {
        List<Map<String, Object>> rank = waylineJobService.getWaylineJobRank(workspaceId, startTime, endTime, groupBy.getField(), sort.getField());
        return HttpResultResponse.success(rank);
    }
}
