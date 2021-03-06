// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.shell;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.runtime.ProcessWrapperUtil;
import com.google.devtools.build.lib.testutil.BlazeTestUtils;
import com.google.devtools.build.lib.testutil.TestConstants;
import com.google.devtools.build.lib.testutil.TestUtils;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Command}s that are wrapped using the {@code process-wrapper}. */
@RunWith(JUnit4.class)
public final class CommandUsingProcessWrapperTest {
  private String getProcessWrapperPath() {
    return BlazeTestUtils.runfilesDir() + "/" + TestConstants.PROCESS_WRAPPER_PATH;
  }

  private String getCpuTimeSpenderPath() {
    return BlazeTestUtils.runfilesDir() + "/" + TestConstants.CPU_TIME_SPENDER_PATH;
  }

  @Test
  public void testCommand_Echo() throws Exception {
    ImmutableList<String> commandArguments = ImmutableList.of("echo", "worker bees can leave");

    Command command = new Command(commandArguments.toArray(new String[0]));
    CommandResult commandResult = command.execute();

    assertThat(commandResult.getTerminationStatus().success()).isTrue();
    assertThat(commandResult.getStdoutStream().toString()).contains("worker bees can leave");
  }

  @Test
  public void testProcessWrappedCommand_Echo() throws Exception {
    ImmutableList<String> commandArguments = ImmutableList.of("echo", "even drones can fly away");

    List<String> fullProcessWrapperCommandLine =
        ProcessWrapperUtil.commandLineBuilder()
            .setProcessWrapperPath(getProcessWrapperPath())
            .setCommandArguments(commandArguments)
            .build();

    Command command = new Command(fullProcessWrapperCommandLine.toArray(new String[0]));
    CommandResult commandResult = command.execute();

    assertThat(commandResult.getTerminationStatus().success()).isTrue();
    assertThat(commandResult.getStdoutStream().toString()).contains("even drones can fly away");
  }

  private void checkStatisticsAboutCpuTimeSpent(
      Duration userTimeToSpend, Duration systemTimeToSpend) throws CommandException, IOException {
    Duration userTimeLowerBound = userTimeToSpend;
    Duration userTimeUpperBound = userTimeToSpend.plusSeconds(2);
    Duration systemTimeLowerBound = systemTimeToSpend;
    Duration systemTimeUpperBound = systemTimeToSpend.plusSeconds(2);

    File outputDir = TestUtils.makeTempDir();
    String statisticsFilePath = outputDir.getAbsolutePath() + "/" + "stats.out";

    ImmutableList<String> commandArguments =
        ImmutableList.of(
            getCpuTimeSpenderPath(),
            Long.toString(userTimeToSpend.getSeconds()),
            Long.toString(systemTimeToSpend.getSeconds()));

    List<String> fullProcessWrapperCommandLine =
        ProcessWrapperUtil.commandLineBuilder()
            .setProcessWrapperPath(getProcessWrapperPath())
            .setCommandArguments(commandArguments)
            .setStatisticsPath(statisticsFilePath)
            .build();

    Command command = new Command(fullProcessWrapperCommandLine.toArray(new String[0]));
    CommandResult commandResult = command.execute();
    assertThat(commandResult.getTerminationStatus().success()).isTrue();

    Optional<ExecutionStatistics.ResourceUsage> resourceUsage =
        ExecutionStatistics.getResourceUsage(statisticsFilePath);
    assertThat(resourceUsage).isPresent();

    Duration userTime = resourceUsage.get().getUserExecutionTime();
    assertThat(userTime).isAtLeast(userTimeLowerBound);
    assertThat(userTime).isAtMost(userTimeUpperBound);

    Duration systemTime = resourceUsage.get().getSystemExecutionTime();
    assertThat(systemTime).isAtLeast(systemTimeLowerBound);
    assertThat(systemTime).isAtMost(systemTimeUpperBound);
  }

  @Test
  public void testProcessWrappedCommand_WithStatistics_SpendUserTime()
      throws CommandException, IOException {
    Duration userTimeToSpend = Duration.ofSeconds(10);
    Duration systemTimeToSpend = Duration.ZERO;

    checkStatisticsAboutCpuTimeSpent(userTimeToSpend, systemTimeToSpend);
  }

  @Test
  public void testProcessWrappedCommand_WithStatistics_SpendSystemTime()
      throws CommandException, IOException {
    Duration userTimeToSpend = Duration.ZERO;
    Duration systemTimeToSpend = Duration.ofSeconds(10);

    checkStatisticsAboutCpuTimeSpent(userTimeToSpend, systemTimeToSpend);
  }

  @Test
  public void testProcessWrappedCommand_WithStatistics_SpendUserAndSystemTime()
      throws CommandException, IOException {
    Duration userTimeToSpend = Duration.ofSeconds(10);
    Duration systemTimeToSpend = Duration.ofSeconds(10);

    checkStatisticsAboutCpuTimeSpent(userTimeToSpend, systemTimeToSpend);
  }
}
