package com.defrag.redmineplugin.service;

import com.defrag.redmineplugin.model.ConnectionInfo;
import com.defrag.redmineplugin.model.Task;
import com.taskadapter.redmineapi.bean.Issue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 * Work only only for linux
 * Uses curl
 *
 * Created by defrag on 22.08.17.
 */
@Slf4j
public class ExtendedTaskMapper implements TaskMapper {

    private static final String CURL_GET_COMMAND_PATTERN = "curl -X GET" +
            " --cookie '%s'" +
            " --header 'X-CSRF-Token:%s'" +
            " %s/issues/%d" +
            " |  grep -Po '(?<=hours[)]<\\/th><td>)\\d+[.]\\d+'"; // (hours)</th><td>x.xx

    private static final String CURL_POST_COMMAND_PATTERN = "curl -X POST" +
            " --cookie '%s'" +
            " --header 'X-CSRF-Token:%s'" +
            " -d '_method=patch&remaining_hours=%d'" +
            " %s/issues/%d";

    private final TaskMapper taskMapper;

    private final ConnectionInfo connectionInfo;

    public ExtendedTaskMapper(TaskMapper taskMapper, ConnectionInfo connectionInfo) {
        this.taskMapper = taskMapper;
        this.connectionInfo = connectionInfo;
    }

    @Override
    public Optional<Task> toPluginTask(Issue source) {
        Optional<Task> dest = taskMapper.toPluginTask(source);
        if (!dest.isPresent()) {
            return Optional.empty();
        }

        findRemainingHours(source.getId()).ifPresent(hours -> dest.get().setRemaining(hours));
        return dest;
    }

    @Override
    public Optional<Issue> toRedmineTask(Task source) {
        Optional<Issue> dest = taskMapper.toRedmineTask(source);

        if (!dest.isPresent()) {
            return Optional.empty();
        }
        // todo
        return dest;
    }

    private Optional<Float> findRemainingHours(Integer taskId) {
        String[] curlCommand = new String[] {"/bin/bash", "-c",
                String.format(CURL_GET_COMMAND_PATTERN, connectionInfo.getCookie(), connectionInfo.getCsrfToken(),
                        connectionInfo.getRedmineUri(), taskId)
        };

        String remainingStr;
        try {
            log.info("Try to find remaining hours");

            Process proc = new ProcessBuilder(curlCommand).start();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                remainingStr = in.readLine();
            }
        } catch (IOException e) {
            log.error("Couldn't find remaining hours!");
            return Optional.empty();
        }

        if (StringUtils.isBlank(remainingStr)) {
            log.info("Remaining hours is blank, set it to zero");
            return Optional.of(0.0f);
        }

        try {
            return Optional.of(Float.valueOf(remainingStr));
        } catch (NumberFormatException e) {
            log.error("Couldn't parse remaining str value {}", remainingStr);
            return Optional.empty();
        }
    }
}