package com.freenow.sauron.controller;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PipelineController
{
    private final PipelineService pipelineService;


    @Autowired
    public PipelineController(PipelineService pipelineService)
    {
        this.pipelineService = pipelineService;
    }


    @PostMapping("/build")
    @Operation(summary = "Start a new asynchronous build in Sauron.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New build has been accepted"),
        @ApiResponse(responseCode = "400", description = "Bad request. Invalid input parameters."),
        @ApiResponse(responseCode = "500", description = "Internal Server Error.")
    })
    public ResponseEntity<Void> build(@Valid @RequestBody BuildRequest request)
    {
        pipelineService.publish(request);
        return ResponseEntity.ok().build();
    }
}
